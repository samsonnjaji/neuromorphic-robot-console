package com.samsonnjaji.nrc.data.robustness

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RobustnessLabEngineTest {
    @Test
    fun assignsSequentialExperimentIdentifiers() {
        val engine = RobustnessLabEngine()
        engine.run(RobotTelemetry(timestampMs = 100))
        assertEquals("NRC-DEMO-0001", engine.state.value.latestReport?.manifest?.experimentId)

        engine.run(RobotTelemetry(timestampMs = 200))
        assertEquals("NRC-DEMO-0002", engine.state.value.latestReport?.manifest?.experimentId)
    }

    @Test
    fun changingScenarioClearsThePreviousResult() {
        val engine = RobustnessLabEngine()
        engine.run(RobotTelemetry(timestampMs = 100))
        engine.selectScenario(EnvironmentScenario.FOG)

        assertEquals(EnvironmentScenario.FOG, engine.state.value.selectedScenario)
        assertNull(engine.state.value.latestReport)
    }
}
