package com.samsonnjaji.nrc.domain.robustness

import kotlinx.serialization.Serializable

@Serializable
enum class ExperimentSourceType {
    SIMULATOR,
    ROS2_LIVE,
    MCAP_RECORDING,
    ROS_BAG_RECORDING,
    CARLA_SIMULATION,
    ISAAC_SIMULATION
}

/**
 * Provenance record for a robustness run.
 * sourceTimestampMs is source time; in the current simulator it is simulation time.
 */
@Serializable
data class ExperimentManifest(
    val manifestVersion: Int = 1,
    val experimentId: String,
    val sourceType: ExperimentSourceType,
    val sourceId: String,
    val sourceSequence: Long,
    val sourceTimestampMs: Long,
    val modelIdentifier: String,
    val modelVersion: String,
    val scenario: EnvironmentScenario,
    val lidarRetention: Float,
    val eventRetention: Float,
    val addedEventNoiseFraction: Float,
    val detectionConfidenceScale: Float,
    val inferenceLatencyScale: Float,
    val lidarNoiseMeters: Float
)
