package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateExperimentRunRequest
import com.samsonnjaji.nrc.api.model.ExperimentRunRecord
import com.samsonnjaji.nrc.api.model.TransitionExperimentRunRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class JdbcExperimentRunRepository(
    private val dataSource: DataSource
) : ExperimentRunRepository {

    override suspend fun create(
        experimentId: String,
        request: CreateExperimentRunRequest
    ): ExperimentRunRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val record = ExperimentRunRecord(
            id = UUID.randomUUID().toString(),
            experimentId = experimentId,
            status = "CREATED",
            sourceRevision = request.sourceRevision.trim(),
            configuration = request.configuration,
            createdAtUnixMs = now,
            updatedAtUnixMs = now
        )

        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(record.id))
                statement.setObject(2, UUID.fromString(record.experimentId))
                statement.setString(3, record.status)
                statement.setString(4, record.sourceRevision)
                statement.setString(5, record.configuration.toString())
                statement.setLong(6, record.createdAtUnixMs)
                statement.setLong(7, record.updatedAtUnixMs)
                statement.executeUpdate()
            }
        }
        record
    }

    override suspend fun listByExperiment(experimentId: String): List<ExperimentRunRecord> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(LIST_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(experimentId))
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toExperimentRunRecord())
                        }
                    }
                }
            }
        }

    override suspend fun find(id: String): ExperimentRunRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_SQL).use { statement ->
                statement.setObject(1, UUID.fromString(id))
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toExperimentRunRecord() else null
                }
            }
        }
    }

    override suspend fun transition(
        id: String,
        request: TransitionExperimentRunRequest
    ): RepositoryUpdate<ExperimentRunRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val current = connection.prepareStatement(FIND_FOR_UPDATE_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(id))
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toExperimentRunRecord() else null
                    }
                }

                when {
                    current == null -> {
                        connection.rollback()
                        RepositoryUpdate.NotFound
                    }
                    !canTransitionExperimentRun(current.status, request.status) -> {
                        connection.rollback()
                        RepositoryUpdate.InvalidTransition
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        val updated = current.copy(
                            status = request.status,
                            resultSummary = request.resultSummary ?: current.resultSummary,
                            failureReason = if (request.status == "FAILED") request.failureReason.trim() else current.failureReason,
                            startedAtUnixMs = if (request.status == "RUNNING") current.startedAtUnixMs ?: now else current.startedAtUnixMs,
                            finishedAtUnixMs = if (request.status in TERMINAL_STATUSES) now else current.finishedAtUnixMs,
                            updatedAtUnixMs = now
                        )
                        connection.prepareStatement(UPDATE_SQL).use { statement ->
                            statement.setString(1, updated.status)
                            statement.setString(2, updated.resultSummary?.toString())
                            statement.setString(3, updated.failureReason)
                            statement.setNullableLong(4, updated.startedAtUnixMs)
                            statement.setNullableLong(5, updated.finishedAtUnixMs)
                            statement.setLong(6, updated.updatedAtUnixMs)
                            statement.setObject(7, UUID.fromString(updated.id))
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

    private fun ResultSet.toExperimentRunRecord() = ExperimentRunRecord(
        id = getString("id"),
        experimentId = getString("experiment_id"),
        status = getString("status"),
        sourceRevision = getString("source_revision"),
        configuration = Json.parseToJsonElement(getString("configuration_json")).jsonObject,
        resultSummary = getString("result_summary_json")?.let {
            Json.parseToJsonElement(it).jsonObject
        },
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
        val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "CANCELLED")

        const val INSERT_SQL = """
            INSERT INTO nrc_experiment_runs (
                id, experiment_id, status, source_revision, configuration_json,
                created_at_unix_ms, updated_at_unix_ms
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
        """

        const val LIST_SQL = """
            SELECT id, experiment_id, status, source_revision, configuration_json,
                   result_summary_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_experiment_runs
            WHERE experiment_id = ?
            ORDER BY created_at_unix_ms DESC, id DESC
            LIMIT 500
        """

        const val FIND_SQL = """
            SELECT id, experiment_id, status, source_revision, configuration_json,
                   result_summary_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_experiment_runs
            WHERE id = ?
        """

        const val FIND_FOR_UPDATE_SQL = """
            SELECT id, experiment_id, status, source_revision, configuration_json,
                   result_summary_json, failure_reason, created_at_unix_ms,
                   started_at_unix_ms, finished_at_unix_ms, updated_at_unix_ms
            FROM nrc_experiment_runs
            WHERE id = ?
            FOR UPDATE
        """

        const val UPDATE_SQL = """
            UPDATE nrc_experiment_runs
            SET status = ?, result_summary_json = ?::jsonb, failure_reason = ?,
                started_at_unix_ms = ?, finished_at_unix_ms = ?, updated_at_unix_ms = ?
            WHERE id = ?
        """
    }
}
