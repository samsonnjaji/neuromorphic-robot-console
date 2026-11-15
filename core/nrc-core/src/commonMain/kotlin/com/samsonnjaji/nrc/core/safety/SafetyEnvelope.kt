package com.samsonnjaji.nrc.core.safety

/**
 * A UI-side guard for the simulator. Physical robots must enforce independent limits
 * in their own controller; this class is not a safety-rated control system.
 */
class SafetyEnvelope(
    private val maxLinearSpeedMps: Float = 0.50f
) {
    init {
        require(maxLinearSpeedMps > 0f) { "Maximum speed must be positive." }
    }

    fun limitLinearSpeed(requestedMps: Float, emergencyStop: Boolean): Float {
        if (emergencyStop || !requestedMps.isFinite()) return 0f
        return requestedMps.coerceIn(0f, maxLinearSpeedMps)
    }
}
