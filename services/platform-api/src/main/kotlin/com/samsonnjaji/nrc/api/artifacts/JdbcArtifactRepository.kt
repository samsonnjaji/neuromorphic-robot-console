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
        verifiedAtUnixMs = getNullableLong("verified_at_unix_ms")
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
                part_size_bytes, part_count, created_at_unix_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        const val FIND_SQL = """
            SELECT id, recording_session_id, status, upload_mode, file_name, content_type,
                   size_bytes, sha256_hex, bucket, object_key, multipart_upload_id,
                   part_size_bytes, part_count, etag, failure_reason,
                   created_at_unix_ms, verified_at_unix_ms
            FROM nrc_artifacts WHERE id = ?
        """

        const val FIND_FOR_UPDATE_SQL = """
            SELECT id, recording_session_id, status, upload_mode, file_name, content_type,
                   size_bytes, sha256_hex, bucket, object_key, multipart_upload_id,
                   part_size_bytes, part_count, etag, failure_reason,
                   created_at_unix_ms, verified_at_unix_ms
            FROM nrc_artifacts WHERE id = ? FOR UPDATE
        """

        const val LIST_SQL = """
            SELECT id, recording_session_id, status, upload_mode, file_name, content_type,
                   size_bytes, sha256_hex, bucket, object_key, multipart_upload_id,
                   part_size_bytes, part_count, etag, failure_reason,
                   created_at_unix_ms, verified_at_unix_ms
            FROM nrc_artifacts
            WHERE recording_session_id = ?
            ORDER BY created_at_unix_ms DESC, id DESC
            LIMIT 500
        """

        const val UPDATE_STATUS_SQL = """
            UPDATE nrc_artifacts
            SET status = ?, etag = ?, failure_reason = ?, verified_at_unix_ms = ?
            WHERE id = ?
        """
    }
}
