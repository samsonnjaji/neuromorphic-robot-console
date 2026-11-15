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
        assumeTrue("PostgreSQL test database is required.", !jdbcUrl.isNullOrBlank())
        val dataSource = createNrcDataSource(
            jdbcUrl = requireNotNull(jdbcUrl),
            username = System.getenv("NRC_TEST_DATABASE_USER") ?: "nrc",
            password = requireNotNull(System.getenv("NRC_TEST_DATABASE_PASSWORD"))
        )
        try {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val experiment = JdbcExperimentRepository(dataSource).create(
                CreateExperimentRequest("Artifact repository test", "PostgreSQL artifact metadata persistence",
                    "HEAVY_RAIN", "SIMULATOR", "nrc-test-simulator", "test-detector", "0.1")
            )
            val runRepository = JdbcExperimentRunRepository(dataSource)
            val run = runRepository.create(experiment.id, CreateExperimentRunRequest(sourceRevision = "artifact-test"))
            assertTrue(runRepository.transition(run.id, TransitionExperimentRunRequest("RUNNING")) is RepositoryUpdate.Updated)
            val recordingRepository = JdbcRecordingSessionRepository(dataSource)
            val recording = recordingRepository.create(
                run.id,
                CreateRecordingSessionRequest("MCAP", "nrc-test-robot", Json.parseToJsonElement("""{"origin":"test"}""").jsonObject)
            )
            assertTrue(recordingRepository.transition(recording.id, TransitionRecordingSessionRequest("RECORDING")) is RepositoryUpdate.Updated)
            assertTrue(recordingRepository.transition(recording.id, TransitionRecordingSessionRequest("FINALIZED", sampleCount = 12)) is RepositoryUpdate.Updated)

            val now = System.currentTimeMillis()
            val artifactId = UUID.randomUUID().toString()
            val artifact = ArtifactRecord(
                id = artifactId, recordingSessionId = recording.id, status = "PENDING_UPLOAD",
                uploadMode = "SINGLE_PUT", fileName = "sample.mcap", contentType = "application/octet-stream",
                sizeBytes = 128, sha256Hex = "a".repeat(64), bucket = "nrc-test-artifacts",
                objectKey = "ci/$artifactId/sample.mcap", createdAtUnixMs = now,
                uploadUrlExpiresAtUnixMs = now + 86_400_000
            )
            val repository = JdbcArtifactRepository(dataSource)
            repository.create(artifact)
            val verified = repository.updateStatus(
                id = artifactId, status = "VERIFIED", etag = "\"test-etag\"",
                verifiedAtUnixMs = System.currentTimeMillis()
            )
            assertTrue(verified is RepositoryUpdate.Updated)
            val reopened = repository.find(artifactId)
            assertNotNull(reopened)
            assertEquals("VERIFIED", reopened.status)
            assertEquals("sample.mcap", reopened.fileName)
            assertEquals(128L, reopened.sizeBytes)
            assertEquals("nrc-test-artifacts", reopened.bucket)
            assertEquals(now + 86_400_000, reopened.uploadUrlExpiresAtUnixMs)
            assertEquals(1, repository.listByRecordingSession(recording.id).size)

            val expiredId = UUID.randomUUID().toString()
            repository.create(
                artifact.copy(
                    id = expiredId,
                    status = "PENDING_UPLOAD",
                    objectKey = "ci/$expiredId/expired.mcap",
                    createdAtUnixMs = now - 90_000,
                    uploadUrlExpiresAtUnixMs = now - 60_000,
                    etag = "",
                    failureReason = "",
                    verifiedAtUnixMs = null,
                    cleanupCompletedAtUnixMs = null
                )
            )
            val candidates = repository.listCleanupCandidates(now - 30_000, 10)
            assertTrue(candidates.any { it.id == expiredId })
            val aborted = repository.abortIfExpired(expiredId, now - 30_000)
            assertTrue(aborted is RepositoryUpdate.Updated)
            assertEquals("ABORTED", (aborted as RepositoryUpdate.Updated).value.status)
            assertTrue(repository.markCleanupCompleted(expiredId, now))
            assertEquals(now, repository.find(expiredId)?.cleanupCompletedAtUnixMs)
            assertTrue(repository.listCleanupCandidates(now + 10_000, 10).none { it.id == expiredId })
        } finally {
            dataSource.close()
        }
    }
}
