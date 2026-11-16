package com.samsonnjaji.nrc.domain.transport

import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SensorObservationStreamGuardTest {
    private fun observation(
        sourceId: String = "imu-front",
        topic: String = "/imu/data",
        sequence: Long = 1L,
        sourceTimestampNs: Long? = 123_000_000L,
        sourceClock: SensorTimestampClock = SensorTimestampClock.ROS_TIME,
        receivedAtUnixNs: Long = 1_800_000_000_000_000_000L
    ) = SensorObservationEnvelope(
        robotId = "robot-01",
        sourceId = sourceId,
        sensorType = SensorObservationType.IMU,
        topic = topic,
        rosType = "sensor_msgs/msg/Imu",
        sequence = sequence,
        sourceTimestampNs = sourceTimestampNs,
        sourceClock = sourceClock,
        receivedAtUnixNs = receivedAtUnixNs,
        frameId = "imu_link",
        dataOrigin = SensorDataOrigin.PHYSICAL_SENSOR,
        units = mapOf(
            "linear_acceleration" to "m/s^2",
            "angular_velocity" to "rad/s"
        ),
        payload = buildJsonObject {
            put("linearAcceleration", buildJsonObject {
                put("x", 0.1)
                put("y", 0.2)
                put("z", 9.81)
            })
            put("angularVelocity", buildJsonObject {
                put("x", 0.0)
                put("y", 0.0)
                put("z", 0.01)
            })
        }
    )

    @Test
    fun acceptsIncreasingSequencesPerSourceAndTopic() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(sequence = 1))
        )
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(sequence = 2))
        )
        assertEquals(2L, guard.lastSequence("imu-front", "/imu/data"))
    }

    @Test
    fun keepsIndependentSequenceDomainsForDifferentTopicsAndSources() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(sequence = 10))
        )
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(topic = "/imu/temperature", sequence = 1))
        )
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(sourceId = "imu-rear", sequence = 1))
        )
    }

    @Test
    fun rejectsDuplicateAndOutOfOrderSequence() {
        val guard = SensorObservationStreamGuard("robot-01")
        guard.validate(observation(sequence = 8))
        assertEquals(
            SensorObservationValidation.STALE_OR_DUPLICATE_SEQUENCE,
            guard.validate(observation(sequence = 8))
        )
        assertEquals(
            SensorObservationValidation.STALE_OR_DUPLICATE_SEQUENCE,
            guard.validate(observation(sequence = 7))
        )
        assertEquals(8L, guard.lastSequence("imu-front", "/imu/data"))
    }

    @Test
    fun rejectsWrongRobotAndInvalidIdentity() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.WRONG_ROBOT,
            guard.validate(observation().copy(robotId = "robot-02"))
        )
        assertEquals(
            SensorObservationValidation.INVALID_IDENTITY,
            guard.validate(observation().copy(topic = " "))
        )
        assertEquals(
            SensorObservationValidation.INVALID_IDENTITY,
            guard.validate(observation().copy(sourceId = ""))
        )
    }

    @Test
    fun rejectsUnsupportedSchemaAndMessageType() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.UNSUPPORTED_SCHEMA,
            guard.validate(observation().copy(schemaVersion = 2))
        )
        assertEquals(
            SensorObservationValidation.UNSUPPORTED_MESSAGE,
            guard.validate(observation().copy(messageType = "telemetry.snapshot"))
        )
    }

    @Test
    fun distinguishesSourceClockFromHostReceiveTime() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.INVALID_SOURCE_TIMESTAMP,
            guard.validate(
                observation().copy(
                    sourceTimestampNs = null,
                    sourceClock = SensorTimestampClock.ROS_TIME
                )
            )
        )
        assertEquals(
            SensorObservationValidation.INVALID_SOURCE_TIMESTAMP,
            guard.validate(
                observation().copy(
                    sourceTimestampNs = 123L,
                    sourceClock = SensorTimestampClock.UNKNOWN
                )
            )
        )
        assertEquals(
            SensorObservationValidation.INVALID_RECEIVE_TIMESTAMP,
            guard.validate(observation().copy(receivedAtUnixNs = 0L))
        )
    }

    @Test
    fun rejectsEmptyOversizedPayloadAndInvalidUnits() {
        val guard = SensorObservationStreamGuard("robot-01")
        assertEquals(
            SensorObservationValidation.EMPTY_PAYLOAD,
            guard.validate(observation().copy(payload = buildJsonObject {}))
        )
        val largePayload = buildJsonObject {
            put("blob", "x".repeat(NRC_SENSOR_OBSERVATION_MAX_PAYLOAD_BYTES + 1))
        }
        assertEquals(
            SensorObservationValidation.PAYLOAD_TOO_LARGE,
            guard.validate(observation().copy(payload = largePayload))
        )
        assertEquals(
            SensorObservationValidation.INVALID_METADATA,
            guard.validate(observation().copy(units = mapOf("" to "m")))
        )
    }

    @Test
    fun resetClearsAllSourceTopicSequenceDomains() {
        val guard = SensorObservationStreamGuard("robot-01")
        guard.validate(observation(sequence = 100))
        guard.reset()
        assertEquals(
            SensorObservationValidation.ACCEPTED,
            guard.validate(observation(sequence = 1))
        )
    }
}
