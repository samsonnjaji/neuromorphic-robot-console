package com.samsonnjaji.nrc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.samsonnjaji.nrc.data.demo.DemoTelemetryEngine
import com.samsonnjaji.nrc.data.robustness.RobustnessLabEngine
import com.samsonnjaji.nrc.presentation.dashboard.DashboardScreen

private val NrcColors = darkColorScheme(
    primary = Color(0xFF73E0C1),
    secondary = Color(0xFF80B9FF),
    background = Color(0xFF090F18),
    surface = Color(0xFF121D2A),
    onPrimary = Color(0xFF071610),
    onBackground = Color(0xFFE8F0F7),
    onSurface = Color(0xFFE8F0F7),
    error = Color(0xFFFF6577)
)

@Composable
fun App() {
    MaterialTheme(colorScheme = NrcColors) {
        val engine = remember { DemoTelemetryEngine() }
        val robustnessEngine = remember { RobustnessLabEngine() }
        val scope = rememberCoroutineScope()
        val telemetry by engine.telemetry.collectAsState()
        val missionState by engine.missionState.collectAsState()
        val robustnessState by robustnessEngine.state.collectAsState()

        LaunchedEffect(engine) { engine.start(scope) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            DashboardScreen(
                telemetry = telemetry,
                onSpeedChange = engine::setTargetSpeed,
                onEmergencyStop = engine::triggerEmergencyStop,
                onResetEmergencyStop = engine::resetEmergencyStop,
                onToggleMission = engine::toggleMission,
                missionState = missionState,
                onStartRecording = engine::startRecording,
                onStopRecording = engine::stopRecording,
                onReplayMission = { engine.replayMission(scope) },
                onStopReplay = engine::stopReplay,
                robustnessState = robustnessState,
                onScenarioSelected = robustnessEngine::selectScenario,
                onRunStressTest = { robustnessEngine.run(telemetry) },
                onClearStressReport = robustnessEngine::clearReport
            )
        }
    }
}
