package com.samsonnjaji.nrc.api

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

class ApplicationTest {
    @Test
    fun healthEndpointReportsServiceAndApiVersion() = testApplication {
        application { module() }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"service\":\"nrc-platform-api\"")
        assertContains(response.bodyAsText(), "\"apiVersion\":\"v1\"")
    }

    @Test
    fun experimentCanBeCreatedListedAndRetrieved() = testApplication {
        application { module() }

        val created = client.post("/api/v1/experiments") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Rain perception baseline",
                  "description": "Local API integration test",
                  "scenario": "HEAVY_RAIN",
                  "sourceType": "SIMULATOR",
                  "sourceId": "nrc-test-simulator",
                  "modelIdentifier": "test-detector",
                  "modelVersion": "0.1"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Created, created.status)
        val body = created.bodyAsText()
        assertContains(body, "\"name\":\"Rain perception baseline\"")
        assertContains(body, "\"scenario\":\"HEAVY_RAIN\"")

        val listed = client.get("/api/v1/experiments")
        assertEquals(HttpStatusCode.OK, listed.status)
        assertContains(listed.bodyAsText(), "Rain perception baseline")

        val id = Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        requireNotNull(id)
        val retrieved = client.get("/api/v1/experiments/$id")
        assertEquals(HttpStatusCode.OK, retrieved.status)
        assertContains(retrieved.bodyAsText(), "\"modelIdentifier\":\"test-detector\"")
    }

    @Test
    fun experimentValidationRejectsUnknownScenario() = testApplication {
        application { module() }

        val response = client.post("/api/v1/experiments") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Invalid scenario",
                  "scenario": "HURRICANE",
                  "sourceType": "SIMULATOR",
                  "sourceId": "test",
                  "modelIdentifier": "detector",
                  "modelVersion": "0.1"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Unsupported environment scenario.")
    }

    @Test
    fun telemetryIngestionRejectsMismatchedPayloadSequence() = testApplication {
        application { module() }
        val payload = """
            {
              "schemaVersion": 1,
              "messageType": "telemetry.snapshot",
              "robotId": "nrc-test-robot",
              "sequence": 7,
              "timestampUnixMs": 1794729600000,
              "payload": {
                "sequence": 6,
                "timestampMs": 700,
                "connected": true,
                "batteryPercent": 95,
                "linearVelocityMps": 0.1,
                "angularVelocityRadS": 0.0,
                "xMeters": 1.0,
                "yMeters": 2.0,
                "headingRadians": 0.0,
                "cpuLoadPercent": 20,
                "inferenceLatencyMs": 8.0,
                "eventRateHz": 12000,
                "targetSpeedMps": 0.1,
                "emergencyStop": false,
                "missionRunning": true,
                "detections": [],
                "snnLayers": [],
                "lidar": [],
                "events": [],
                "imu": {
                  "accelerationX": 0.0,
                  "accelerationY": 0.0,
                  "accelerationZ": 9.81,
                  "angularVelocityZ": 0.0
                }
              }
            }
        """.trimIndent()

        val response = client.post("/api/v1/robots/nrc-test-robot/telemetry") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "payload_sequence_mismatch")
    }

    @Test
    fun sensorObservationIngestionAcceptsAndRejectsDuplicateSequence() = testApplication {
        application { module() }
        val payload = """
            {
              "schemaVersion": 1,
              "messageType": "sensor.observation",
              "robotId": "nrc-test-robot",
              "sourceId": "imu-front",
              "sensorType": "IMU",
              "topic": "/imu/data",
              "rosType": "sensor_msgs/msg/Imu",
              "sequence": 1,
              "sourceTimestampNs": 123000000,
              "sourceClock": "ROS_TIME",
              "receivedAtUnixNs": 1800000000000000000,
              "frameId": "imu_link",
              "dataOrigin": "PHYSICAL_SENSOR",
              "units": {
                "linear_acceleration": "m/s^2",
                "angular_velocity": "rad/s"
              },
              "payload": {
                "linearAcceleration": {"x": 0.1, "y": 0.2, "z": 9.81},
                "angularVelocity": {"x": 0.0, "y": 0.0, "z": 0.01}
              }
            }
        """.trimIndent()

        val first = client.post("/api/v1/robots/nrc-test-robot/observations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Accepted, first.status)
        assertContains(first.bodyAsText(), "\"sourceId\":\"imu-front\"")

        val duplicate = client.post("/api/v1/robots/nrc-test-robot/observations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.BadRequest, duplicate.status)
        assertContains(duplicate.bodyAsText(), "stale_or_duplicate_sequence")
    }

    @Test
    fun sensorObservationIngestionRejectsRouteRobotMismatch() = testApplication {
        application { module() }
        val payload = """
            {
              "schemaVersion": 1,
              "messageType": "sensor.observation",
              "robotId": "robot-inside-payload",
              "sourceId": "lidar-front",
              "sensorType": "LASER_SCAN",
              "topic": "/scan",
              "rosType": "sensor_msgs/msg/LaserScan",
              "sequence": 1,
              "sourceClock": "UNKNOWN",
              "receivedAtUnixNs": 1800000000000000000,
              "dataOrigin": "UNKNOWN",
              "units": {},
              "payload": {"ranges": [0.5, 1.0, 1.5]}
            }
        """.trimIndent()

        val response = client.post("/api/v1/robots/nrc-test-robot/observations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "wrong_robot")
    }

    @Test
    fun telemetryIngestionRejectsDuplicateSequence() = testApplication {
        application { module() }
        val payload = """
            {
              "schemaVersion": 1,
              "messageType": "telemetry.snapshot",
              "robotId": "nrc-test-robot",
              "sequence": 7,
              "timestampUnixMs": 1794729600000,
              "payload": {
                "sequence": 7,
                "timestampMs": 700,
                "connected": true,
                "batteryPercent": 95,
                "linearVelocityMps": 0.1,
                "angularVelocityRadS": 0.0,
                "xMeters": 1.0,
                "yMeters": 2.0,
                "headingRadians": 0.0,
                "cpuLoadPercent": 20,
                "inferenceLatencyMs": 8.0,
                "eventRateHz": 12000,
                "targetSpeedMps": 0.1,
                "emergencyStop": false,
                "missionRunning": true,
                "detections": [],
                "snnLayers": [],
                "lidar": [],
                "events": [],
                "imu": {
                  "accelerationX": 0.0,
                  "accelerationY": 0.0,
                  "accelerationZ": 9.81,
                  "angularVelocityZ": 0.0
                }
              }
            }
        """.trimIndent()

        val first = client.post("/api/v1/robots/nrc-test-robot/telemetry") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Accepted, first.status)

        val duplicate = client.post("/api/v1/robots/nrc-test-robot/telemetry") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.BadRequest, duplicate.status)
        assertContains(duplicate.bodyAsText(), "stale_or_duplicate_sequence")
    }
}
