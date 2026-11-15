package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.artifacts.InMemoryArtifactRepository
import com.samsonnjaji.nrc.api.model.ArtifactRecord
import com.samsonnjaji.nrc.api.storage.ArtifactObjectStorage
import com.samsonnjaji.nrc.api.storage.CompletedObjectPart
import com.samsonnjaji.nrc.api.storage.ObjectVerification
import com.samsonnjaji.nrc.api.storage.SignedObjectUpload
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArtifactUploadApiTest {
    @Test
    fun singlePartArtifactIsVerifiedBeforeDownloadUrlIsIssued() = testApplication {
        val storage = FakeArtifactObjectStorage()
        val repository = InMemoryArtifactRepository()
        application {
            module(
                artifactRepository = repository,
                artifactObjectStorage = storage
            )
        }

        val recordingId = createFinalizedRecording(client)
        val expectedHash = "a".repeat(64)
        storage.verification = ObjectVerification(
            sizeBytes = 5,
            etag = "\"single-etag\"",
            sha256Hex = expectedHash
        )

        val created = client.post("/api/v1/recordings/$recordingId/artifacts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "fileName": "rain-scene.mcap",
                  "contentType": "application/octet-stream",
                  "sizeBytes": 5,
                  "sha256Hex": "$expectedHash"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdBody = created.bodyAsText()
        assertContains(createdBody, "\"uploadMode\":\"SINGLE_PUT\"")
        assertContains(createdBody, "https://upload.invalid/")
        val artifactId = extractId(createdBody)

        val completed = client.post("/api/v1/artifacts/$artifactId/complete") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, completed.status)
        assertContains(completed.bodyAsText(), "\"status\":\"VERIFIED\"")

        val download = client.post("/api/v1/artifacts/$artifactId/download-url")
        assertEquals(HttpStatusCode.OK, download.status)
        assertContains(download.bodyAsText(), "https://download.invalid/")
    }

    @Test
    fun multipartArtifactRequestsEveryPartAndVerifiesAfterCompletion() = testApplication {
        val storage = FakeArtifactObjectStorage()
        application {
            module(
                artifactRepository = InMemoryArtifactRepository(),
                artifactObjectStorage = storage
            )
        }

        val recordingId = createFinalizedRecording(client)
        val sizeBytes = 64L * 1024 * 1024 + 1
        val expectedHash = "b".repeat(64)
        storage.verification = ObjectVerification(
            sizeBytes = sizeBytes,
            etag = "\"multipart-etag\"",
            sha256Hex = expectedHash
        )

        val created = client.post("/api/v1/recordings/$recordingId/artifacts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "fileName": "long-run.mcap",
                  "contentType": "application/octet-stream",
                  "sizeBytes": $sizeBytes,
                  "sha256Hex": "$expectedHash"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val body = created.bodyAsText()
        assertContains(body, "\"uploadMode\":\"MULTIPART\"")
        assertContains(body, "\"partCount\":2")
        val artifactId = extractId(body)

        val firstPart = client.post("/api/v1/artifacts/$artifactId/parts/1/upload-url")
        val secondPart = client.post("/api/v1/artifacts/$artifactId/parts/2/upload-url")
        assertEquals(HttpStatusCode.OK, firstPart.status)
        assertEquals(HttpStatusCode.OK, secondPart.status)

        val completed = client.post("/api/v1/artifacts/$artifactId/complete") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "parts": [
                    {"partNumber": 1, "etag": "\"etag-1\""},
                    {"partNumber": 2, "etag": "\"etag-2\""}
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, completed.status)
        assertEquals(listOf(1, 2), storage.completedParts.map { it.partNumber })
        assertContains(completed.bodyAsText(), "\"status\":\"VERIFIED\"")
    }

    @Test
    fun artifactChecksumMismatchMarksArtifactFailed() = testApplication {
        val storage = FakeArtifactObjectStorage()
        application {
            module(
                artifactRepository = InMemoryArtifactRepository(),
                artifactObjectStorage = storage
            )
        }

        val recordingId = createFinalizedRecording(client)
        val expectedHash = "c".repeat(64)
        storage.verification = ObjectVerification(
            sizeBytes = 5,
            etag = "\"wrong-etag\"",
            sha256Hex = "d".repeat(64)
        )

        val created = client.post("/api/v1/recordings/$recordingId/artifacts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "fileName": "bad-checksum.bin",
                  "sizeBytes": 5,
                  "sha256Hex": "$expectedHash"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val artifactId = extractId(created.bodyAsText())

        val completed = client.post("/api/v1/artifacts/$artifactId/complete") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.Conflict, completed.status)
        assertContains(completed.bodyAsText(), "artifact_checksum_mismatch")

        val artifact = client.get("/api/v1/artifacts/$artifactId")
        assertContains(artifact.bodyAsText(), "\"status\":\"FAILED\"")
        assertEquals(1, storage.deletedObjects.size)
    }

    private suspend fun createFinalizedRecording(client: HttpClient): String {
        val experiment = client.post("/api/v1/experiments") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Artifact upload test",
                  "scenario": "HEAVY_RAIN",
                  "sourceType": "SIMULATOR",
                  "sourceId": "nrc-test-simulator",
                  "modelIdentifier": "test-detector",
                  "modelVersion": "0.1"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, experiment.status)
        val experimentId = extractId(experiment.bodyAsText())

        val run = client.post("/api/v1/experiments/$experimentId/runs") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.Created, run.status)
        val runId = extractId(run.bodyAsText())

        val startRun = client.post("/api/v1/runs/$runId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"RUNNING"}""")
        }
        assertEquals(HttpStatusCode.OK, startRun.status)

        val recording = client.post("/api/v1/runs/$runId/recordings") {
            contentType(ContentType.Application.Json)
            setBody("""{"format":"MCAP","sourceId":"nrc-test-robot"}""")
        }
        assertEquals(HttpStatusCode.Created, recording.status)
        val recordingId = extractId(recording.bodyAsText())

        val startRecording = client.post("/api/v1/recordings/$recordingId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"RECORDING"}""")
        }
        assertEquals(HttpStatusCode.OK, startRecording.status)

        val finalizeRecording = client.post("/api/v1/recordings/$recordingId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"FINALIZED","sampleCount":5}""")
        }
        assertEquals(HttpStatusCode.OK, finalizeRecording.status)
        return recordingId
    }

    private fun extractId(body: String): String =
        requireNotNull(Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.get(1))
}

private class FakeArtifactObjectStorage : ArtifactObjectStorage {
    override val bucket: String = "nrc-test-artifacts"
    var verification: ObjectVerification? = null
    var completedParts: List<CompletedObjectPart> = emptyList()
    val deletedObjects = mutableListOf<String>()

    override fun ensureBucket() = Unit

    override suspend fun createSingleUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long
    ) = SignedObjectUpload(
        url = "https://upload.invalid/$objectKey",
        requiredHeaders = mapOf("Content-Type" to contentType),
        expiresAtUnixMs = System.currentTimeMillis() + 900_000
    )

    override suspend fun initiateMultipartUpload(objectKey: String, contentType: String): String =
        "fake-multipart-upload-id"

    override suspend fun createPartUpload(
        objectKey: String,
        uploadId: String,
        partNumber: Int
    ) = SignedObjectUpload(
        url = "https://upload.invalid/$objectKey?partNumber=$partNumber",
        requiredHeaders = emptyMap(),
        expiresAtUnixMs = System.currentTimeMillis() + 900_000
    )

    override suspend fun completeMultipartUpload(
        objectKey: String,
        uploadId: String,
        parts: List<CompletedObjectPart>
    ) {
        completedParts = parts
    }

    override suspend fun abortMultipartUpload(objectKey: String, uploadId: String) = Unit
    override suspend fun deleteObject(objectKey: String) { deletedObjects += objectKey }

    override suspend fun verifyObject(objectKey: String): ObjectVerification? = verification

    override suspend fun createDownloadUrl(objectKey: String) = SignedObjectUpload(
        url = "https://download.invalid/$objectKey",
        requiredHeaders = emptyMap(),
        expiresAtUnixMs = System.currentTimeMillis() + 900_000
    )

    override fun close() = Unit
}
