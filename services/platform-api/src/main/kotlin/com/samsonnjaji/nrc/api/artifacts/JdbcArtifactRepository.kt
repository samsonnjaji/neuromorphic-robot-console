package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.model.ArtifactRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class JdbcArtifactRepository(
    private val dataSource: DataSource
) : ArtifactRepository {

    override suspend fun create(record: ArtifactRecord): ArtifactRecord = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(record.id))
                statement.setObject(2, UUID.fromString(record.recordingSessionId))
                statement.setString(3, record.status)
                statement.setString(4, record.uploadMode)
                statement.setString(5, record.fileName)
                statement.setString(6, record.contentType)
                statement.setLong(7, record.sizeBytes)
                statement.setString(8, record.sha256Hex)
                statement.setString(9, record.bucket)
                statement.setString(10, record.objectKey)
                statement.setString(11, record.multipartUploadId)
                statement.setNullableLong(12, record.partSizeBytes)
                statement.setNullableInt(13, record.partCount)
                statement.setLong(14, record.createdAtUnixMs)
                statement.setLong(15, record.uploadUrlExpiresAtUnixMs)
                statement.setNullableLong(16, record.cleanupCompletedAtUnixMs)
                statement.executeUpdate()
            }
        }
        record
    }

    override suspend fun find(id: String): ArtifactRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(id))
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toArtifactRecord() else null
                }
            }
        }
    }

    override suspend fun listByRecordingSession(recordingSessionId: String): List<ArtifactRecord> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(LIST_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(recordingSessionId))
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toArtifactRecord())
                        }
                    }
                }
            }
        }

    override suspend fun updateStatus(
        id: String,
        status: String,
        etag: String,
        failureReason: String,
        verifiedAtUnixMs: Long?
    ): RepositoryUpdate<ArtifactRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val current = connection.prepareStatement(FIND_FOR_UPDATE_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(id))
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toArtifactRecord() else null
                    }
                }
                when {
                    current == null -> {
                        connection.rollback()
                        RepositoryUpdate.NotFound
                    }
                    current.status != "PENDING_UPLOAD" ||
                        status !in setOf("VERIFIED", "FAILED", "ABORTED") -> {
                        connection.rollback()
                        RepositoryUpdate.InvalidTransition
                    }
                    else -> {
                        val updated = current.copy(
                            status = status,
                            etag = etag,
                            failureReason = failureReason,
                            verifiedAtUnixMs = verifiedAtUnixMs
                        )
                        connection.prepareStatement(UPDATE_STATUS_SQL).use { statement ->
                            statement.setString(1, updated.status)
                            statement.setString(2, updated.etag)
                            statement.setString(3, updated.failureReason)
                            statement.setNullableLong(4, updated.verifiedAtUnixMs)
                            statement.setObject(5, UUID.fromString(updated.id))
                            statement.executeUpdate()
                        }
                        connection.commit()
                        RepositoryUpdate.Updated(updated)
                    }
                }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    override suspend fun updateUploadUrlExpiry(id: String, expiresAtUnixMs: Long): ArtifactRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                val changed = connection.prepareStatement(UPDATE_UPLOAD_EXPIRY_SQL).use { statement ->
                    statement.setLong(1, expiresAtUnixMs)
                    statement.setObject(2, UUID.fromString(id))
                    statement.executeUpdate()
                }
                if (changed == 0) null else connection.prepareStatement(FIND_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(id))
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toArtifactRecord() else null
                    }
                }
            }
        }

    override suspend fun listCleanupCandidates(expiredBeforeUnixMs: Long, limit: Int): List<ArtifactRecord> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(LIST_CLEANUP_SQL).use { statement ->
                    statement.setLong(1, expiredBeforeUnixMs)
                    statement.setInt(2, limit.coerceIn(1, 1_000))
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toArtifactRecord())
                        }
                    }
                }
            }
        }

    override suspend fun abortIfExpired(
        id: String,
        expiredBeforeUnixMs: Long
    ): RepositoryUpdate<ArtifactRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val current = connection.prepareStatement(FIND_FOR_UPDATE_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(id))
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toArtifactRecord() else null
                    }
                }
                when {
                    current == null -> {
                        connection.rollback()
                        RepositoryUpdate.NotFound
                    }
                    current.status != "PENDING_UPLOAD" ||
                        current.uploadUrlExpiresAtUnixMs > expiredBeforeUnixMs -> {
                        connection.rollback()
                        RepositoryUpdate.InvalidTransition
                    }
                    else -> {
                        val updated = current.copy(
                            status = "ABORTED",
                            failureReason = "Upload expired before verification."
                        )
                        connection.prepareStatement(ABORT_EXPIRED_SQL).use { statement ->
                            statement.setString(1, updated.failureReason)
                            statement.setObject(2, UUID.fromString(updated.id))
                            statement.executeUpdate()
                        }
                        connection.commit()
                        RepositoryUpdate.Updated(updated)
                    }
                }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    override suspend fun markCleanupCompleted(id: String, completedAtUnixMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(MARK_CLEANUP_COMPLETED_SQL).use { statement ->
                    statement.setLong(1, completedAtUnixMs)
                    statement.setObject(2, UUID.fromString(id))
                    statement.executeUpdate() == 1
                }
            }
        }

    private fun ResultSet.toArtifactRecord() = ArtifactRecord(
        id = getString("id"),
        recordingSessionId = getString("recording_session_id"),
        status = getString("status"),
        uploadMode = getString("upload_mode"),
        fileName = getString("file_name"),
        contentType = getString("content_type"),
        sizeBytes = getLong("size_bytes"),
        sha256Hex = getString("sha256_hex"),
        bucket = getString("bucket"),
        objectKey = getString("object_key"),
        multipartUploadId = getString("multipart_upload_id"),
        partSizeBytes = getNullableLong("part_size_bytes"),
        partCount = getNullableInt("part_count"),
        etag = getString("etag"),
        failureReason = getString("failure_reason"),
        createdAtUnixMs = getLong("created_at_unix_ms"),
        verifiedAtUnixMs = getNullableLong("verified_at_unix_ms"),
        uploadUrlExpiresAtUnixMs = getLong("upload_url_expires_at_unix_ms"),
        cleanupCompletedAtUnixMs = getNullableLong("cleanup_completed_at_unix_ms")
    )

    private fun ResultSet.getNullableLong(column: String): Long? =
        getLong(column).let { value -> if (wasNull()) null else value }

    private fun ResultSet.getNullableInt(column: String): Int? =
        getInt(column).let { value -> if (wasNull()) null else value }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
    }

    private fun java.sql.PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) setNull(index, java.sql.Types.INTEGER) else setInt(index, value)
    }

    private companion object {
        const val INSERT_SQL = """
            INSERT INTO nrc_artifacts (
                id, recording_session_id, status, upload_mode, file_name, content_type,
                size_bytes, sha256_hex, bucket, object_key, multipart_upload_id,
                part_size_bytes, part_count, created_at_unix_ms,
                upload_url_expires_at_unix_ms, cleanup_completed_at_unix_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        const val FIND_COLUMNS = """
            id, recording_session_id, status, upload_mode, file_name, content_type,
            size_bytes, sha256_hex, bucket, object_key, multipart_upload_id,
            part_size_bytes, part_count, etag, failure_reason,
            created_at_unix_ms, verified_at_unix_ms,
            upload_url_expires_at_unix_ms, cleanup_completed_at_unix_ms
        """
        const val FIND_SQL = "SELECT $FIND_COLUMNS FROM nrc_artifacts WHERE id = ?"
        const val FIND_FOR_UPDATE_SQL = "SELECT $FIND_COLUMNS FROM nrc_artifacts WHERE id = ? FOR UPDATE"
        const val LIST_SQL = """
            SELECT $FIND_COLUMNS FROM nrc_artifacts
            WHERE recording_session_id = ?
            ORDER BY created_at_unix_ms DESC, id DESC
            LIMIT 500
        """
        const val LIST_CLEANUP_SQL = """
            SELECT $FIND_COLUMNS FROM nrc_artifacts
            WHERE (status = 'PENDING_UPLOAD' AND upload_url_expires_at_unix_ms <= ?)
               OR (status = 'ABORTED' AND cleanup_completed_at_unix_ms IS NULL)
            ORDER BY upload_url_expires_at_unix_ms ASC, created_at_unix_ms ASC, id ASC
            LIMIT ?
        """
        const val UPDATE_STATUS_SQL = """
            UPDATE nrc_artifacts
            SET status = ?, etag = ?, failure_reason = ?, verified_at_unix_ms = ?
            WHERE id = ?
        """
        const val UPDATE_UPLOAD_EXPIRY_SQL = """
            UPDATE nrc_artifacts
            SET upload_url_expires_at_unix_ms = GREATEST(upload_url_expires_at_unix_ms, ?)
            WHERE id = ? AND status = 'PENDING_UPLOAD'
        """
        const val ABORT_EXPIRED_SQL = """
            UPDATE nrc_artifacts SET status = 'ABORTED', failure_reason = ? WHERE id = ?
        """
        const val MARK_CLEANUP_COMPLETED_SQL = """
            UPDATE nrc_artifacts SET cleanup_completed_at_unix_ms = ?
            WHERE id = ? AND status = 'ABORTED' AND cleanup_completed_at_unix_ms IS NULL
        """
    }
}
