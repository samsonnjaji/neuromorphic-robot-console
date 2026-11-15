package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateRecordingSessionRequest
import com.samsonnjaji.nrc.api.model.RecordingSessionRecord
import com.samsonnjaji.nrc.api.model.TransitionRecordingSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class JdbcRecordingSessionRepository(
    private val dataSource: DataSource
) : RecordingSessionRepository {

    override suspend fun create(
        experimentRunId: String,
        request: CreateRecordingSessionRequest
    ): RecordingSessionRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val record = RecordingSessionRecord(
            id = UUID.randomUUID().toString(),
            experimentRunId = experimentRunId,
            status = "CREATED",
            format = request.format,
            sourceId = request.sourceId.trim(),
            sampleCount = 0,
            metadata = request.metadata,
            createdAtUnixMs = now,
            updatedAtUnixMs = now
        )

        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(record.id))
                statement.setObject(2, UUID.fromString(record.experimentRunId))
                statement.setString(3, record.status)
                statement.setString(4, record.format)
                statement.setString(5, record.sourceId)
                statement.setString(6, record.metadata.toString())
                statement.setLong(7, record.createdAtUnixMs)
                statement.setLong(8, record.updatedAtUnixMs)
                statement.executeUpdate()
            }
        }
        record
    }

    override suspend fun listByRun(experimentRunId: String): List<RecordingSessionRecord> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(LIST_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(experimentRunId))
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toRecordingSessionRecord())
                        }
                    }
                }
            }
        }

    override suspend fun find(id: String): RecordingSessionRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(id))
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toRecordingSessionRecord() else null
                }
            }
        }
    }

    override suspend fun transition(
        id: String,
        request: TransitionRecordingSessionRequest
    ): RepositoryUpdate<RecordingSessionRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val current = connection.prepareStatement(FIND_FOR_UPDATE_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(id))
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toRecordingSessionRecord() else null
                    }
                }

                when {
                    current == null -> {
                        connection.rollback()
                        RepositoryUpdate.NotFound
                    }
                    !canTransitionRecordingSession(current.status, request.status) -> {
                        connection.rollback()
                        RepositoryUpdate.InvalidTransition
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        val updated = current.copy(
                            status = request.status,
                            sampleCount = request.sampleCount ?: current.sampleCount,
                            metadata = request.metadata ?: current.metadata,
                            failureReason = if (request.status == "FAILED") request.failureReason.trim() else current.failureReason,
                            startedAtUnixMs = if (request.status == "RECORDING") current.startedAtUnixMs ?: now else current.startedAtUnixMs,
                            finishedAtUnixMs = if (request.status in TERMINAL_STATUSES) now else current.finishedAtUnixMs,
                            updatedAtUnixMs = now
                        )
                        connection.prepareStatement(UPDATE_SQL).use { statement ->
                            statement.setString(1, updated.status)
                            statement.setLong(2, updated.sampleCount)
                            statement.setString(3, updated.metadata.toString())
                            statement.setString(4, updated.failureReason)
                            statement.setNullableLong(5, updated.startedAtUnixMs)
                            statement.setNullableLong(6, updated.finishedAtUnixMs)
                            statement.setLong(7, updated.updatedAtUnixMs)
                            statement.setObject(8, UUID.fromString(updated.id))
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

    private fun ResultSet.toRecordingSessionRecord() = RecordingSessionRecord(
        id = getString("id"),
        experimentRunId = getString("experiment_run_id"),
        status = getString("status"),
        format = getString("format"),
        sourceId = getString("source_id"),
        sampleCount = getLong("sample_count"),
        metadata = Json.parseToJsonElement(getString("metadata_json")).jsonObject,
        failureReason = getString("failure_reason"),
        createdAtUnixMs = getLong("created_at_unix_ms"),
        startedAtUnixMs = getNullableLong("started_at_unix_ms"),
        finishedAtUnixMs = getNullableLong("finished_at_unix_ms"),
        updatedAtUnixMs = getLong("updated_at_unix_ms")
    )

    private fun ResultSet.getNullableLong(column: String): Long? =
        getLong(column).let { value -> if (wasNull()) null else value }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
    }

    private companion object {
        val TERMINAL_STATUSES = setOf("FINALIZED", "FAILED", "CANCELLED")

        const val INSERT_SQL = """
            INSERT INTO nrc_recording_sessions (
                id, experiment_run_id, status, format, source_id, metadata_json,
                created_at_unix_ms, updated_at_unix_ms
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """

        const val LIST_SQL = """
            SELECT id, experiment_run_id, status, format, source_id, sample_count,
                   metadata_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_recording_sessions
            WHERE experiment_run_id = ?
            ORDER BY created_at_unix_ms DESC, id DESC
            LIMIT 500
        """

        const val FIND_SQL = """
            SELECT id, experiment_run_id, status, format, source_id, sample_count,
                   metadata_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_recording_sessions
            WHERE id = ?
        """

        const val FIND_FOR_UPDATE_SQL = """
            SELECT id, experiment_run_id, status, format, source_id, sample_count,
                   metadata_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_recording_sessions
            WHERE id = ?
            FOR UPDATE
        """

        const val UPDATE_SQL = """
            UPDATE nrc_recording_sessions
            SET status = ?, sample_count = ?, metadata_json = ?::jsonb, failure_reason = ?,
                started_at_unix_ms = ?, finished_at_unix_ms = ?, updated_at_unix_ms = ?
            WHERE id = ?
        """
    }
}
