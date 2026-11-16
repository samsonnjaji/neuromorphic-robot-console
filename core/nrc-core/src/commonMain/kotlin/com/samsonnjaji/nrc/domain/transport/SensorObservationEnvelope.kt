package com.samsonnjaji.nrc.domain.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val NRC_SENSOR_OBSERVATION_SCHEMA_VERSION = 1
const val NRC_SENSOR_OBSERVATION_TYPE = "sensor.observation"
const val NRC_SENSOR_OBSERVATION_MAX_PAYLOAD_BYTES = 64 * 1024

@Serializable
enum class SensorObservationType {
    IMU,
    ODOMETRY,
    LASER_SCAN,
    IMAGE,
    POINT_CLOUD2,
    EVENT_CAMERA,
    RADAR,
    GPS,
    OTHER
}

@Serializable
enum class SensorTimestampClock {
    ROS_TIME,
    SYSTEM_TIME,
    MCAP_PUBLISH_TIME,
    UNKNOWN
}

@Serializable
enum class SensorDataOrigin {
    PHYSICAL_SENSOR,
    SIMULATED,
    DATASET,
    UNKNOWN
}

/**
 * A bounded, typed metadata envelope for one decoded sensor observation.
 *
 * sourceTimestampNs belongs to sourceClock and is deliberately not assumed to be Unix time.
 * receivedAtUnixNs is the edge gateway's Unix wall-clock receive time.
 * High-bandwidth raw payloads remain in MCAP/object-storage artifacts; payload is a compact
 * JSON observation or summary for live fan-out.
 */
@Serializable
data class SensorObservationEnvelope(
    val schemaVersion: Int = NRC_SENSOR_OBSERVATION_SCHEMA_VERSION,
    val messageType: String = NRC_SENSOR_OBSERVATION_TYPE,
    val robotId: String,
    val sourceId: String,
    val sensorType: SensorObservationType,
    val topic: String,
    val rosType: String,
    val sequence: Long,
    val sourceTimestampNs: Long? = null,
    val sourceClock: SensorTimestampClock,
    val receivedAtUnixNs: Long,
    val frameId: String? = null,
    val childFrameId: String? = null,
    val dataOrigin: SensorDataOrigin,
    val units: Map<String, String>,
    val payload: JsonObject
)
