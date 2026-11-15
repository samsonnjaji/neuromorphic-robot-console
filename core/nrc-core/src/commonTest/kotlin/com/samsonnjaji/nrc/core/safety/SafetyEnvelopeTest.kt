package com.samsonnjaji.nrc.core.safety

import kotlin.test.Test
import kotlin.test.assertEquals

class SafetyEnvelopeTest {
    private val safety = SafetyEnvelope(maxLinearSpeedMps = 0.5f)

    @Test
    fun clampsRequestedSpeedToConfiguredMaximum() {
        assertEquals(0.5f, safety.limitLinearSpeed(2.0f, emergencyStop = false))
    }

    @Test
    fun emergencyStopAlwaysReturnsZero() {
        assertEquals(0f, safety.limitLinearSpeed(0.4f, emergencyStop = true))
    }

    @Test
    fun negativeSpeedIsRejectedAsZero() {
        assertEquals(0f, safety.limitLinearSpeed(-0.3f, emergencyStop = false))
    }

    @Test
    fun nonFiniteSpeedIsRejectedAsZero() {
        assertEquals(0f, safety.limitLinearSpeed(Float.NaN, emergencyStop = false))
    }
}
