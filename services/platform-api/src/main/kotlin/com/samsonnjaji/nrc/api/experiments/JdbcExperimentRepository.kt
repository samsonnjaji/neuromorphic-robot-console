package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.ExperimentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-backed experiment metadata repository.
 *
 * JDBC work is dispatched to IO so blocking driver calls do not occupy Ktor's
 * request/event-loop threads. Raw sensor recordings are deliberately not stored
 * in this relational table.
 */
class JdbcExperimentRepository(
    private val dataSource: DataSource
) : ExperimentRepository {

    override suspend fun create(request: CreateExperimentRequest): ExperimentRecord =
        withContext(Dispatchers.IO) {
            val record = ExperimentRecord(
                id = UUID.randomUUID().toString(),
                name = request.name.trim(),
                description = request.description.trim(),
                scenario = request.scenario,
                sourceType = request.sourceType,
                sourceId = request.sourceId.trim(),
                modelIdentifier = request.modelIdentifier.trim(),
                modelVersion = request.modelVersion.trim(),
                createdAtUnixMs = System.currentTimeMillis()
            )

            dataSource.connection.use { connection ->
                connection.prepareStatement(INSERT_SQL).use { statement ->
                    statement.setObject(1, UUID.fromString(record.id))
                    statement.setString(2, record.name)
                    statement.setString(3, record.description)
                    statement.setString(4, record.scenario)
                    statement.setString(5, record.sourceType)
                    statement.setString(6, record.sourceId)
                    statement.setString(7, record.modelIdentifier)
                    statement.setString(8, record.modelVersion)
                    statement.setLong(9, record.createdAtUnixMs)
                    statement.executeUpdate()
                }
            }
            record
        }

    override suspend fun list(): List<ExperimentRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(LIST_SQL).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toExperimentRecord())
                    }
                }
            }
        }
    }

    override suspend fun find(id: String): ExperimentRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_SQL).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toExperimentRecord() else null
                }
            }
        }
    }

    private fun ResultSet.toExperimentRecord() = ExperimentRecord(
        id = getString("id"),
        name = getString("name"),
        description = getString("description"),
        scenario = getString("scenario"),
        sourceType = getString("source_type"),
        sourceId = getString("source_id"),
        modelIdentifier = getString("model_identifier"),
        modelVersion = getString("model_version"),
        createdAtUnixMs = getLong("created_at_unix_ms")
    )

    private companion object {
        const val INSERT_SQL = """
            INSERT INTO nrc_experiments (
                id, name, description, scenario, source_type, source_id,
                model_identifier, model_version, created_at_unix_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        const val LIST_SQL = """
            SELECT id, name, description, scenario, source_type, source_id,
                   model_identifier, model_version, created_at_unix_ms
            FROM nrc_experiments
            ORDER BY created_at_unix_ms DESC, id DESC
            LIMIT 500
        """

        const val FIND_SQL = """
            SELECT id, name, description, scenario, source_type, source_id,
                   model_identifier, model_version, created_at_unix_ms
            FROM nrc_experiments
            WHERE id::text = ?
        """
    }
}
