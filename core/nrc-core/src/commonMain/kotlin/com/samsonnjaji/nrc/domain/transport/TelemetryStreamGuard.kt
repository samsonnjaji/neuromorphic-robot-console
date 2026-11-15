package com.samsonnjaji.nrc.domain.transport

/**
 * Validates envelope metadata before a snapshot is allowed into the application state stream.
 * A new guard should be created for each robot connection/session.
 */
class TelemetryStreamGuard(
    private val expectedRobotId: String,
    private val supportedSchemaVersion: Int = NRC_TELEMETRY_SCHEMA_VERSION
) {
    private var lastAcceptedSequence: Long = -1L

    fun validate(envelope: TelemetryEnvelope): EnvelopeValidation {
        if (envelope.schemaVersion != supportedSchemaVersion) {
            return EnvelopeValidation.UNSUPPORTED_SCHEMA
        }
        if (envelope.messageType != NRC_TELEMETRY_SNAPSHOT_TYPE) {
            return EnvelopeValidation.UNSUPPORTED_MESSAGE
        }
        if (envelope.robotId != expectedRobotId || expectedRobotId.isBlank()) {
            return EnvelopeValidation.WRONG_ROBOT
        }
        if (envelope.sequence < 0L || envelope.sequence <= lastAcceptedSequence) {
            return EnvelopeValidation.STALE_OR_DUPLICATE_SEQUENCE
        }
        if (envelope.timestampUnixMs <= 0L) {
            return EnvelopeValidation.INVALID_TIMESTAMP
        }

        lastAcceptedSequence = envelope.sequence
        return EnvelopeValidation.ACCEPTED
    }

    fun reset() {
        lastAcceptedSequence = -1L
    }

    fun lastSequence(): Long = lastAcceptedSequence
}

enum class EnvelopeValidation {
    ACCEPTED,
    UNSUPPORTED_SCHEMA,
    UNSUPPORTED_MESSAGE,
    WRONG_ROBOT,
    STALE_OR_DUPLICATE_SEQUENCE,
    INVALID_TIMESTAMP
}
