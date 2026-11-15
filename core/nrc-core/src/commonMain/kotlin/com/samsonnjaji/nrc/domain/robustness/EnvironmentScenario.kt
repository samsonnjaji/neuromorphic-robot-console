package com.samsonnjaji.nrc.domain.robustness

import kotlinx.serialization.Serializable

/**
 * Deterministic stress profiles for the first NRC robustness-lab prototype.
 *
 * Coefficients are illustrative perturbations, not calibrated weather physics.
 * They must be replaced or fitted against measured sensor data before scientific
 * or safety-critical conclusions are drawn.
 */
@Serializable
enum class EnvironmentScenario(
    val displayName: String,
    val description: String,
    val lidarRetention: Float,
    val eventRetention: Float,
    val addedEventNoiseFraction: Float,
    val detectionConfidenceScale: Float,
    val inferenceLatencyScale: Float,
    val lidarNoiseMeters: Float
) {
    CLEAR(
        "Clear baseline",
        "Reference run without synthetic sensor degradation.",
        1.00f, 1.00f, 0.00f, 1.00f, 1.00f, 0.00f
    ),
    HEAVY_RAIN(
        "Heavy rain",
        "Illustrative rain profile: LiDAR dropout, event clutter and reduced detection confidence.",
        0.72f, 0.82f, 0.12f, 0.84f, 1.12f, 0.08f
    ),
    FOG(
        "Fog",
        "Illustrative fog profile: reduced range returns and visual confidence.",
        0.65f, 0.95f, 0.03f, 0.68f, 1.18f, 0.04f
    ),
    LOW_LIGHT(
        "Low light",
        "Illustrative low-light profile: visual confidence falls while event and LiDAR streams remain mostly available.",
        1.00f, 0.98f, 0.01f, 0.70f, 1.08f, 0.00f
    ),
    DUST_SMOKE(
        "Dust / smoke",
        "Illustrative obscuration profile: stronger range dropout, event noise and perception degradation.",
        0.58f, 0.76f, 0.18f, 0.64f, 1.25f, 0.10f
    )
}
