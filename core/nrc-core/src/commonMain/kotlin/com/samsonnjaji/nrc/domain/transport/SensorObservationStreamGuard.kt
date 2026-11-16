package com.samsonnjaji.nrc.domain.transport

/**
 * Validates compact sensor observations before live fan-out.
 *
 * Sequence numbers are monotonic per (sourceId, topic), because independent ROS 2 topics
 * commonly have independent publisher sequence domains.
 */
class SensorObservationStreamGuard(
    private val expectedRobotId: String,
    private val supportedSchemaVersion: Int = NRC_SENSOR_OBSERVATION_SCHEMA_VERSION,
    private val maxPayloadBytes: Int = NRC_SENSOR_OBSERVATION_MAX_PAYLOAD_BYTES
) {
    private val lastAcceptedSequences = mutableMapOf<String, Long>()

    fun validate(envelope: SensorObservationEnvelope): SensorObservationValidation {
        if (envelope.schemaVersion != supportedSchemaVersion) {
            return SensorObservationValidation.UNSUPPORTED_SCHEMA
        }
        if (envelope.messageType != NRC_SENSOR_OBSERVATION_TYPE) {
            return SensorObservationValidation.UNSUPPORTED_MESSAGE
        }
        if (expectedRobotId.isBlank() || envelope.robotId != expectedRobotId) {
            return SensorObservationValidation.WRONG_ROBOT
        }
        if (
            envelope.sourceId.isBlank() || envelope.sourceId.length > 255 ||
            envelope.topic.isBlank() || envelope.topic.length > 512 ||
            envelope.rosType.isBlank() || envelope.rosType.length > 255
        ) {
            return SensorObservationValidation.INVALID_IDENTITY
        }
        if (envelope.sequence < 0L) {
            return SensorObservationValidation.INVALID_SEQUENCE
        }
        if (envelope.receivedAtUnixNs <= 0L) {
            return SensorObservationValidation.INVALID_RECEIVE_TIMESTAMP
        }
        if (
            (envelope.sourceTimestampNs != null && envelope.sourceTimestampNs < 0L) ||
            (envelope.sourceTimestampNs == null) !=
                (envelope.sourceClock == SensorTimestampClock.UNKNOWN)
        ) {
            return SensorObservationValidation.INVALID_SOURCE_TIMESTAMP
        }
        if (
            (envelope.frameId?.length ?: 0) > 255 ||
            (envelope.childFrameId?.length ?: 0) > 255 ||
            envelope.units.size > 32 ||
            envelope.units.any { (key, value) ->
                key.isBlank() || key.length > 64 || value.isBlank() || value.length > 64
            }
        ) {
            return SensorObservationValidation.INVALID_METADATA
        }
        if (envelope.payload.isEmpty()) {
            return SensorObservationValidation.EMPTY_PAYLOAD
        }
        if (envelope.payload.toString().encodeToByteArray().size > maxPayloadBytes) {
            return SensorObservationValidation.PAYLOAD_TOO_LARGE
        }

        val streamKey = "${envelope.sourceId}\u0000${envelope.topic}"
        val lastSequence = lastAcceptedSequences[streamKey] ?: -1L
        if (envelope.sequence <= lastSequence) {
            return SensorObservationValidation.STALE_OR_DUPLICATE_SEQUENCE
        }

        lastAcceptedSequences[streamKey] = envelope.sequence
        return SensorObservationValidation.ACCEPTED
    }

    fun reset() {
        lastAcceptedSequences.clear()
    }

    fun lastSequence(sourceId: String, topic: String): Long? =
        lastAcceptedSequences["${sourceId}\u0000${topic}"]
}

enum class SensorObservationValidation {
    ACCEPTED,
    UNSUPPORTED_SCHEMA,
    UNSUPPORTED_MESSAGE,
    WRONG_ROBOT,
    INVALID_IDENTITY,
    INVALID_SEQUENCE,
    INVALID_RECEIVE_TIMESTAMP,
    INVALID_SOURCE_TIMESTAMP,
    INVALID_METADATA,
    EMPTY_PAYLOAD,
    PAYLOAD_TOO_LARGE,
    STALE_OR_DUPLICATE_SEQUENCE
}
