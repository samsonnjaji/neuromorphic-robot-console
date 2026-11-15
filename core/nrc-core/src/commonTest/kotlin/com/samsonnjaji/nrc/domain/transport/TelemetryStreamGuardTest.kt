package com.samsonnjaji.nrc.domain.transport

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryStreamGuardTest {
    private fun envelope(
        robotId: String = "robot-01",
        sequence: Long = 1L,
        schemaVersion: Int = NRC_TELEMETRY_SCHEMA_VERSION,
        messageType: String = NRC_TELEMETRY_SNAPSHOT_TYPE,
        timestamp: Long = 1_800_000_000_000L
    ) = TelemetryEnvelope(
        schemaVersion = schemaVersion,
        messageType = messageType,
        robotId = robotId,
        sequence = sequence,
        timestampUnixMs = timestamp,
        payload = RobotTelemetry(sequence = sequence, timestampMs = timestamp)
    )

    @Test
    fun acceptsFirstAndIncreasingSnapshots() {
        val guard = TelemetryStreamGuard("robot-01")
        assertEquals(EnvelopeValidation.ACCEPTED, guard.validate(envelope(sequence = 1)))
        assertEquals(EnvelopeValidation.ACCEPTED, guard.validate(envelope(sequence = 2)))
        assertEquals(2L, guard.lastSequence())
    }

    @Test
    fun rejectsDuplicateAndOutOfOrderSnapshots() {
        val guard = TelemetryStreamGuard("robot-01")
        assertEquals(EnvelopeValidation.ACCEPTED, guard.validate(envelope(sequence = 8)))
        assertEquals(
            EnvelopeValidation.STALE_OR_DUPLICATE_SEQUENCE,
            guard.validate(envelope(sequence = 8))
        )
        assertEquals(
            EnvelopeValidation.STALE_OR_DUPLICATE_SEQUENCE,
            guard.validate(envelope(sequence = 7))
        )
        assertEquals(8L, guard.lastSequence())
    }

    @Test
    fun rejectsPayloadSequenceMismatch() {
        val guard = TelemetryStreamGuard("robot-01")
        val inconsistent = envelope(sequence = 9).copy(
            payload = RobotTelemetry(sequence = 8, timestampMs = 900)
        )
        assertEquals(
            EnvelopeValidation.PAYLOAD_SEQUENCE_MISMATCH,
            guard.validate(inconsistent)
        )
        assertEquals(-1L, guard.lastSequence())
    }

    @Test
    fun rejectsUnknownSchemaAndMessageType() {
        val guard = TelemetryStreamGuard("robot-01")
        assertEquals(
            EnvelopeValidation.UNSUPPORTED_SCHEMA,
            guard.validate(envelope(schemaVersion = 2))
        )
        assertEquals(
            EnvelopeValidation.UNSUPPORTED_MESSAGE,
            guard.validate(envelope(messageType = "telemetry.unknown"))
        )
    }

    @Test
    fun rejectsOtherRobotAndInvalidTimestamp() {
        val guard = TelemetryStreamGuard("robot-01")
        assertEquals(
            EnvelopeValidation.WRONG_ROBOT,
            guard.validate(envelope(robotId = "robot-02"))
        )
        assertEquals(
            EnvelopeValidation.INVALID_TIMESTAMP,
            guard.validate(envelope(timestamp = 0L))
        )
    }

    @Test
    fun resetAllowsANewStreamSequence() {
        val guard = TelemetryStreamGuard("robot-01")
        guard.validate(envelope(sequence = 100))
        guard.reset()
        assertEquals(EnvelopeValidation.ACCEPTED, guard.validate(envelope(sequence = 1)))
    }
}
