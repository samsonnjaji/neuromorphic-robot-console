package com.samsonnjaji.nrc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.samsonnjaji.nrc.data.demo.DemoTelemetryEngine
import com.samsonnjaji.nrc.data.robustness.RobustnessLabEngine
import com.samsonnjaji.nrc.data.telemetry.NrcTelemetrySource
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
fun App(apiTelemetrySource: NrcTelemetrySource? = null) {
    MaterialTheme(colorScheme = NrcColors) {
        val demoEngine = remember { DemoTelemetryEngine() }
        val telemetrySource = apiTelemetrySource ?: demoEngine
        val readOnly = apiTelemetrySource != null
        val robustnessEngine = remember { RobustnessLabEngine() }
        val scope = rememberCoroutineScope()
        val telemetry by telemetrySource.telemetry.collectAsState()
        val sourceConnected by telemetrySource.isConnected.collectAsState()
        val missionState by demoEngine.missionState.collectAsState()
        val robustnessState by robustnessEngine.state.collectAsState()

        LaunchedEffect(telemetrySource) { telemetrySource.start(scope) }
        DisposableEffect(telemetrySource) {
            onDispose { telemetrySource.stop() }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            DashboardScreen(
                telemetry = telemetry,
                sourceLabel = telemetrySource.sourceLabel,
                sourceConnected = sourceConnected,
                readOnly = readOnly,
                onSpeedChange = demoEngine::setTargetSpeed,
                onEmergencyStop = demoEngine::triggerEmergencyStop,
                onResetEmergencyStop = demoEngine::resetEmergencyStop,
                onToggleMission = demoEngine::toggleMission,
                missionState = missionState,
                onStartRecording = demoEngine::startRecording,
                onStopRecording = demoEngine::stopRecording,
                onReplayMission = { demoEngine.replayMission(scope) },
                onStopReplay = demoEngine::stopReplay,
                robustnessState = robustnessState,
                onScenarioSelected = robustnessEngine::selectScenario,
                onRunStressTest = { robustnessEngine.run(telemetry) },
                onClearStressReport = robustnessEngine::clearReport
            )
        }
    }
}
