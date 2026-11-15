package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.database.createNrcDataSource
import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.CreateExperimentRunRequest
import com.samsonnjaji.nrc.api.model.CreateRecordingSessionRequest
import com.samsonnjaji.nrc.api.model.TransitionExperimentRunRequest
import com.samsonnjaji.nrc.api.model.TransitionRecordingSessionRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.flywaydb.core.Flyway
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdbcLifecycleRepositoryTest {
    @Test
    fun experimentRunsAndRecordingSessionsPersistAcrossRepositoryRecreation() = runBlocking {
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

            val experiment = JdbcExperimentRepository(dataSource).create(
                CreateExperimentRequest(
                    name = "Lifecycle persistence test",
                    description = "PostgreSQL run and recording persistence",
                    scenario = "HEAVY_RAIN",
                    sourceType = "SIMULATOR",
                    sourceId = "nrc-test-simulator",
                    modelIdentifier = "test-detector",
                    modelVersion = "0.1"
                )
            )

            val runRepository = JdbcExperimentRunRepository(dataSource)
            val run = runRepository.create(
                experiment.id,
                CreateExperimentRunRequest(
                    sourceRevision = "test-commit",
                    configuration = Json.parseToJsonElement("""{"seed":7}""").jsonObject
                )
            )
            val startedRun = runRepository.transition(
                run.id,
                TransitionExperimentRunRequest(status = "RUNNING")
            )
            assertTrue(startedRun is RepositoryUpdate.Updated)

            val recordingRepository = JdbcRecordingSessionRepository(dataSource)
            val recording = recordingRepository.create(
                run.id,
                CreateRecordingSessionRequest(
                    format = "MCAP",
                    sourceId = "nrc-test-robot",
                    metadata = Json.parseToJsonElement("""{"topicCount":2}""").jsonObject
                )
            )
            val startedRecording = recordingRepository.transition(
                recording.id,
                TransitionRecordingSessionRequest(status = "RECORDING")
            )
            assertTrue(startedRecording is RepositoryUpdate.Updated)

            val finalizedRecording = recordingRepository.transition(
                recording.id,
                TransitionRecordingSessionRequest(
                    status = "FINALIZED",
                    sampleCount = 256,
                    metadata = Json.parseToJsonElement("""{"topicCount":2,"verified":true}""").jsonObject
                )
            )
            assertTrue(finalizedRecording is RepositoryUpdate.Updated)

            val completedRun = runRepository.transition(
                run.id,
                TransitionExperimentRunRequest(
                    status = "COMPLETED",
                    resultSummary = Json.parseToJsonElement("""{"frames":256}""").jsonObject
                )
            )
            assertTrue(completedRun is RepositoryUpdate.Updated)

            val reopenedRun = JdbcExperimentRunRepository(dataSource).find(run.id)
            val reopenedRecording = JdbcRecordingSessionRepository(dataSource).find(recording.id)

            assertNotNull(reopenedRun)
            assertNotNull(reopenedRecording)
            assertEquals("COMPLETED", reopenedRun.status)
            assertEquals("test-commit", reopenedRun.sourceRevision)
            assertEquals(7, reopenedRun.configuration["seed"]?.toString()?.toInt())
            assertEquals("FINALIZED", reopenedRecording.status)
            assertEquals(256L, reopenedRecording.sampleCount)
            assertEquals("true", reopenedRecording.metadata["verified"]?.toString())
        } finally {
            dataSource.close()
        }
    }
}
