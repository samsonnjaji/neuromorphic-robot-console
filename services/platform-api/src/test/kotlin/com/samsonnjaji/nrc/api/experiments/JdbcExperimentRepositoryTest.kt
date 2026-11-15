package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.database.createNrcDataSource
import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JdbcExperimentRepositoryTest {
    @Test
    fun experimentMetadataPersistsAcrossRepositoryRecreation() = runBlocking {
        val jdbcUrl = System.getenv("NRC_TEST_DATABASE_URL")
        assumeTrue(
            "Set NRC_TEST_DATABASE_URL to run the PostgreSQL integration test.",
            !jdbcUrl.isNullOrBlank()
        )

        val dataSource = createNrcDataSource(
            jdbcUrl = requireNotNull(jdbcUrl),
            username = System.getenv("NRC_TEST_DATABASE_USER") ?: "nrc",
            password = requireNotNull(System.getenv("NRC_TEST_DATABASE_PASSWORD")) {
                "NRC_TEST_DATABASE_PASSWORD is required for the PostgreSQL integration test."
            }
        )

        try {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM nrc_experiments")
                }
            }

            val request = CreateExperimentRequest(
                name = "Rain perception baseline",
                description = "PostgreSQL persistence integration test",
                scenario = "HEAVY_RAIN",
                sourceType = "SIMULATOR",
                sourceId = "nrc-test-simulator",
                modelIdentifier = "test-detector",
                modelVersion = "0.1"
            )

            val created = JdbcExperimentRepository(dataSource).create(request)
            val recreatedRepository = JdbcExperimentRepository(dataSource)
            val found = recreatedRepository.find(created.id)

            assertNotNull(found)
            assertEquals(created, found)
            assertEquals(listOf(created), recreatedRepository.list())
        } finally {
            dataSource.close()
        }
    }
}
