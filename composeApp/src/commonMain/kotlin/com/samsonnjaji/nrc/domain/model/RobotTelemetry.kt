package com.samsonnjaji.nrc.domain.model

data class Detection(
    val label: String,
    val confidence: Float,
    val bearingDegrees: Float
)

data class LidarPoint(
    val angleRadians: Float,
    val distanceMeters: Float
)

data class EventSample(
    val x: Float,
    val y: Float,
    val polarity: Int
)

data class ImuReading(
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val angularVelocityZ: Float
)

data class SnnLayerActivity(
    val layerName: String,
    val activeNeurons: Int,
    val neuronCount: Int,
    val spikesPerSecond: Float,
    val meanMembranePotentialMv: Float
) {
    val activeRatio: Float
        get() = if (neuronCount <= 0) 0f
        else (activeNeurons.toFloat() / neuronCount).coerceIn(0f, 1f)
}

data class RobotTelemetry(
    val sequence: Long = 0,
    val timestampMs: Long = 0,
    val connected: Boolean = true,
    val batteryPercent: Int = 100,
    val linearVelocityMps: Float = 0f,
    val angularVelocityRadS: Float = 0f,
    val xMeters: Float = 0f,
    val yMeters: Float = 0f,
    val headingRadians: Float = 0f,
    val cpuLoadPercent: Int = 0,
    val inferenceLatencyMs: Float = 0f,
    val eventRateHz: Int = 0,
    val targetSpeedMps: Float = 0f,
    val emergencyStop: Boolean = false,
    val missionRunning: Boolean = true,
    val detections: List<Detection> = emptyList(),
    val snnLayers: List<SnnLayerActivity> = emptyList(),
    val lidar: List<LidarPoint> = emptyList(),
    val events: List<EventSample> = emptyList(),
    val imu: ImuReading = ImuReading(0f, 0f, 9.81f, 0f)
)
