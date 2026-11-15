package com.samsonnjaji.nrc.data.robustness

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import com.samsonnjaji.nrc.domain.robustness.RobustnessEvaluator
import com.samsonnjaji.nrc.domain.robustness.StressTestReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RobustnessLabState(
    val selectedScenario: EnvironmentScenario = EnvironmentScenario.HEAVY_RAIN,
    val latestReport: StressTestReport? = null
)

/** Coordinates repeatable synthetic environment stress tests for the dashboard. */
class RobustnessLabEngine(
    private val evaluator: RobustnessEvaluator = RobustnessEvaluator()
) {
    private val _state = MutableStateFlow(RobustnessLabState())
    val state: StateFlow<RobustnessLabState> = _state.asStateFlow()

    fun selectScenario(scenario: EnvironmentScenario) {
        _state.value = _state.value.copy(
            selectedScenario = scenario,
            latestReport = null
        )
    }

    fun run(telemetry: RobotTelemetry) {
        val scenario = _state.value.selectedScenario
        _state.value = _state.value.copy(
            latestReport = evaluator.evaluate(telemetry, scenario)
        )
    }

    fun clearReport() {
        _state.value = _state.value.copy(latestReport = null)
    }
}
