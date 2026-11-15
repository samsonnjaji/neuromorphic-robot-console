package com.samsonnjaji.nrc.api

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

class ExperimentLifecycleApiTest {
    @Test
    fun experimentRunAndRecordingSessionFollowTheirLifecycles() = testApplication {
        application { module() }

        val experimentId = createExperiment(client)
        val runResponse = client.post("/api/v1/experiments/$experimentId/runs") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceRevision": "abc123",
                  "configuration": {
                    "scenario": "HEAVY_RAIN",
                    "randomSeed": 42
                  }
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, runResponse.status)
        val runBody = runResponse.bodyAsText()
        assertContains(runBody, "\"status\":\"CREATED\"")
        assertContains(runBody, "\"sourceRevision\":\"abc123\"")
        val runId = extractId(runBody)

        val startedRun = client.post("/api/v1/runs/$runId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"RUNNING"}""")
        }
        assertEquals(HttpStatusCode.OK, startedRun.status)
        assertContains(startedRun.bodyAsText(), "\"status\":\"RUNNING\"")

        val recordingResponse = client.post("/api/v1/runs/$runId/recordings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "format": "MCAP",
                  "sourceId": "nrc-simulated-robot",
                  "metadata": {"topicCount": 4}
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, recordingResponse.status)
        val recordingBody = recordingResponse.bodyAsText()
        assertContains(recordingBody, "\"status\":\"CREATED\"")
        assertContains(recordingBody, "\"format\":\"MCAP\"")
        val recordingId = extractId(recordingBody)

        val recordingStarted = client.post("/api/v1/recordings/$recordingId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"RECORDING"}""")
        }
        assertEquals(HttpStatusCode.OK, recordingStarted.status)

        val recordingFinalized = client.post("/api/v1/recordings/$recordingId/transition") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "status": "FINALIZED",
                  "sampleCount": 120,
                  "metadata": {"topicCount": 4, "clock": "simulated"}
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, recordingFinalized.status)
        assertContains(recordingFinalized.bodyAsText(), "\"sampleCount\":120")
        assertContains(recordingFinalized.bodyAsText(), "\"status\":\"FINALIZED\"")

        val completedRun = client.post("/api/v1/runs/$runId/transition") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "status": "COMPLETED",
                  "resultSummary": {"frames": 120, "dataOrigin": "SYNTHETIC"}
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, completedRun.status)
        assertContains(completedRun.bodyAsText(), "\"status\":\"COMPLETED\"")
        assertContains(completedRun.bodyAsText(), "\"dataOrigin\":\"SYNTHETIC\"")

        val persistedRun = client.get("/api/v1/runs/$runId")
        assertEquals(HttpStatusCode.OK, persistedRun.status)
        assertContains(persistedRun.bodyAsText(), "\"status\":\"COMPLETED\"")

        val recordings = client.get("/api/v1/runs/$runId/recordings")
        assertEquals(HttpStatusCode.OK, recordings.status)
        assertContains(recordings.bodyAsText(), recordingId)
        assertContains(recordings.bodyAsText(), "\"status\":\"FINALIZED\"")
    }

    @Test
    fun lifecycleRejectsInvalidTransitionsAndMissingFailureReasons() = testApplication {
        application { module() }

        val experimentId = createExperiment(client)
        val runResponse = client.post("/api/v1/experiments/$experimentId/runs") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.Created, runResponse.status)
        val runId = extractId(runResponse.bodyAsText())

        val skippedStart = client.post("/api/v1/runs/$runId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"COMPLETED"}""")
        }
        assertEquals(HttpStatusCode.Conflict, skippedStart.status)

        val missingFailureReason = client.post("/api/v1/runs/$runId/transition") {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"FAILED"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingFailureReason.status)

        val unchanged = client.get("/api/v1/runs/$runId")
        assertContains(unchanged.bodyAsText(), "\"status\":\"CREATED\"")
    }

    @Test
    fun runCannotBeCreatedForUnknownExperiment() = testApplication {
        application { module() }

        val response = client.post("/api/v1/experiments/00000000-0000-0000-0000-000000000001/runs") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private suspend fun createExperiment(client: HttpClient): String {
        val response = client.post("/api/v1/experiments") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Lifecycle API test",
                  "description": "Synthetic test fixture",
                  "scenario": "HEAVY_RAIN",
                  "sourceType": "SIMULATOR",
                  "sourceId": "nrc-test-simulator",
                  "modelIdentifier": "test-detector",
                  "modelVersion": "0.1"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return extractId(response.bodyAsText())
    }

    private fun extractId(body: String): String =
        requireNotNull(Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.get(1))
}
