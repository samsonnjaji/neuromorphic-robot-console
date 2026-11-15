package com.samsonnjaji.nrc.domain.transport

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlinx.serialization.Serializable

const val NRC_TELEMETRY_SCHEMA_VERSION = 1
const val NRC_TELEMETRY_SNAPSHOT_TYPE = "telemetry.snapshot"

/**
 * Typed application-level envelope. The gateway is responsible for decoding the wire format
 * and mapping its payload into this stable model before it reaches the shared UI.
 */
@Serializable
data class TelemetryEnvelope(
    val schemaVersion: Int = NRC_TELEMETRY_SCHEMA_VERSION,
    val messageType: String = NRC_TELEMETRY_SNAPSHOT_TYPE,
    val robotId: String,
    val sequence: Long,
    val timestampUnixMs: Long,
    val payload: RobotTelemetry
)
