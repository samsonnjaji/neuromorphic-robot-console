package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.database.createNrcDataSource
import com.samsonnjaji.nrc.api.experiments.JdbcExperimentRepository
import com.samsonnjaji.nrc.api.experiments.JdbcExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.JdbcRecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.model.ArtifactRecord
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdbcArtifactRepositoryTest {

    @Test
    fun artifactMetadataPersistsAndCanBeMarkedVerified() = runBlocking {
        val jdbcUrl = System.getenv("NRC_TEST_DATABASE_URL")
        assumeTrue(
            "PostgreSQL test database is required.",
            !jdbcUrl.isNullOrBlank()
        )
        val dataSource = createNrcDataSource(
            jdbcUrl = requireNotNull(jdbcUrl),
            username = System.getenv("NRC_TEST_DATABASE_USER") ?: "nrc",
            password = requireNotNull(System.getenv("NRC_TEST_DATABASE_PASSWORD"))
        )

        try {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            val experiment = JdbcExperimentRepository(dataSource).create(
                CreateExperimentRequest(
                    name = "Artifact repository test",
                    description = "PostgreSQL artifact metadata persistence",
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
                CreateExperimentRunRequest(sourceRevision = "artifact-test")
            )
            assertTrue(
                runRepository.transition(run.id, TransitionExperimentRunRequest("RUNNING")) is
                    RepositoryUpdate.Updated
            )

            val recordingRepository = JdbcRecordingSessionRepository(dataSource)
            val recording = recordingRepository.create(
                run.id,
                CreateRecordingSessionRequest(
                    format = "MCAP",
                    sourceId = "nrc-test-robot",
                    metadata = Json.parseToJsonElement("""{"origin":"test"}""").jsonObject
                )
            )
            assertTrue(
                recordingRepository.transition(
                    recording.id,
                    TransitionRecordingSessionRequest("RECORDING")
                ) is RepositoryUpdate.Updated
            )
            assertTrue(
                recordingRepository.transition(
                    recording.id,
                    TransitionRecordingSessionRequest("FINALIZED", sampleCount = 12)
                ) is RepositoryUpdate.Updated
            )

            val artifactId = UUID.randomUUID().toString()
            val artifact = ArtifactRecord(
                id = artifactId,
                recordingSessionId = recording.id,
                status = "PENDING_UPLOAD",
                uploadMode = "SINGLE_PUT",
                fileName = "sample.mcap",
                contentType = "application/octet-stream",
                sizeBytes = 128,
                sha256Hex = "a".repeat(64),
                bucket = "nrc-test-artifacts",
                objectKey = "ci/$artifactId/sample.mcap",
                createdAtUnixMs = System.currentTimeMillis()
            )
            val artifactRepository = JdbcArtifactRepository(dataSource)
            artifactRepository.create(artifact)

            val update = artifactRepository.updateStatus(
                id = artifactId,
                status = "VERIFIED",
                etag = "\"test-etag\"",
                verifiedAtUnixMs = System.currentTimeMillis()
            )
            assertTrue(update is RepositoryUpdate.Updated)

            val reopenedRepository = JdbcArtifactRepository(dataSource)
            val reopened = reopenedRepository.find(artifactId)
            assertNotNull(reopened)
            assertEquals("VERIFIED", reopened.status)
            assertEquals("sample.mcap", reopened.fileName)
            assertEquals(128L, reopened.sizeBytes)
            assertEquals("nrc-test-artifacts", reopened.bucket)
            assertEquals(1, reopenedRepository.listByRecordingSession(recording.id).size)
        } finally {
            dataSource.close()
        }
    }
}
