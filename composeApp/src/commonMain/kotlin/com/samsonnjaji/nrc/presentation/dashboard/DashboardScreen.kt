package com.samsonnjaji.nrc.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsonnjaji.nrc.data.robustness.RobustnessLabState
import com.samsonnjaji.nrc.domain.mission.MissionConsoleState
import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import com.samsonnjaji.nrc.presentation.robustness.RobustnessLabPanel
import kotlin.math.cos
import kotlin.math.sin

private val Background = Color(0xFF090F18)
private val Panel = Color(0xFF121D2A)
private val Muted = Color(0xFF91A3B5)
private val Mint = Color(0xFF73E0C1)
private val Blue = Color(0xFF80B9FF)
private val Amber = Color(0xFFFFC56E)
private val Red = Color(0xFFFF6577)
private val White = Color(0xFFE8F0F7)

@Composable
fun DashboardScreen(
    telemetry: RobotTelemetry,
    onSpeedChange: (Float) -> Unit,
    onEmergencyStop: () -> Unit,
    onResetEmergencyStop: () -> Unit,
    onToggleMission: () -> Unit,
    missionState: MissionConsoleState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReplayMission: () -> Unit,
    onStopReplay: () -> Unit,
    robustnessState: RobustnessLabState,
    onScenarioSelected: (EnvironmentScenario) -> Unit,
    onRunStressTest: () -> Unit,
    onClearStressReport: () -> Unit
) {
    val sensorTelemetry = robustnessState.latestReport?.stressedTelemetry ?: telemetry

    Column(
        modifier = Modifier.fillMaxSize().background(Background)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NRC  /  NEUROMORPHIC ROBOTICS CONSOLE", color = Mint, fontSize = 11.sp)
                Text("Robot Intelligence Monitor", color = White, fontSize = 25.sp)
                Text("Research workspace  •  Deterministic demo telemetry", color = Muted, fontSize = 12.sp)
            }
            StatusPill(if (telemetry.connected) "SIMULATOR ONLINE" else "OFFLINE", Mint)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("BATTERY", telemetry.batteryPercent.toString() + "%", "Power system", Mint, Modifier.weight(1f))
            MetricCard("VELOCITY", format(telemetry.linearVelocityMps) + " m/s", "Linear speed", Blue, Modifier.weight(1f))
            MetricCard("INFERENCE", format(telemetry.inferenceLatencyMs) + " ms", "Live simulator", Amber, Modifier.weight(1f))
            MetricCard("EVENT RATE", (telemetry.eventRateHz / 1000).toString() + "k Hz", "Live event stream", Mint, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PanelCard(
                if (robustnessState.latestReport == null) "EVENT CAMERA  /  640 × 480" else "EVENT CAMERA  /  STRESS OUTPUT",
                Modifier.weight(1f)
            ) {
                EventView(sensorTelemetry)
                Text("Positive and negative brightness-change events", color = Muted, fontSize = 10.sp)
            }
            PanelCard(
                if (robustnessState.latestReport == null) "LiDAR  /  360° SCAN" else "LiDAR  /  STRESS OUTPUT",
                Modifier.weight(1f)
            ) {
                LidarView(sensorTelemetry)
                Text("Range returns after selected scenario perturbation", color = Muted, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PanelCard("PERCEPTION + SNN ACTIVITY", Modifier.weight(1f)) {
                sensorTelemetry.detections.forEach { detection ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(detection.label, color = White, fontSize = 12.sp)
                        Text(
                            (detection.confidence * 100).toInt().toString() + "%  •  " +
                                detection.bearingDegrees.toInt().toString() + "°",
                            color = Mint,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                sensorTelemetry.snnLayers.forEach { layer ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(layer.layerName, color = White, fontSize = 11.sp)
                            Text(layer.spikesPerSecond.toInt().toString() + " spikes/s", color = Mint, fontSize = 10.sp)
                        }
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .background(Color(0xFF26384A), RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(layer.activeRatio).height(6.dp)
                                    .background(Mint, RoundedCornerShape(6.dp))
                            )
                        }
                        Text(
                            layer.activeNeurons.toString() + "/" + layer.neuronCount.toString() +
                                " active • mean membrane " + format(layer.meanMembranePotentialMv) + " mV",
                            color = Muted,
                            fontSize = 9.sp
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "IMU  ax " + format(sensorTelemetry.imu.accelerationX) +
                        "  ay " + format(sensorTelemetry.imu.accelerationY) +
                        "  az " + format(sensorTelemetry.imu.accelerationZ) + " m/s²",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            PanelCard("ROBOT CONTROL  /  SIMULATION ONLY", Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target speed", color = Muted, fontSize = 12.sp)
                    Text(format(telemetry.targetSpeedMps) + " m/s", color = Mint, fontSize = 13.sp)
                }
                Slider(
                    value = telemetry.targetSpeedMps,
                    onValueChange = onSpeedChange,
                    valueRange = 0f..0.5f,
                    enabled = !telemetry.emergencyStop
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onToggleMission,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color(0xFF07121D))
                    ) {
                        Text(if (telemetry.missionRunning) "Pause mission" else "Resume mission")
                    }
                    if (telemetry.emergencyStop) {
                        OutlinedButton(onClick = onResetEmergencyStop) { Text("Reset E-stop", color = Amber) }
                    } else {
                        Button(
                            onClick = onEmergencyStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) { Text("EMERGENCY STOP") }
                    }
                }
                Text("All commands are simulated. No physical robot is connected.", color = Muted, fontSize = 10.sp)
            }
        }

        RobustnessLabPanel(
            state = robustnessState,
            onScenarioSelected = onScenarioSelected,
            onRunTest = onRunStressTest,
            onClearReport = onClearStressReport
        )

        PanelCard("MISSION CAPTURE  /  DETERMINISTIC REPLAY", Modifier.fillMaxWidth()) {
            Text(
                when {
                    missionState.isReplaying -> "REPLAYING RECORDED TELEMETRY"
                    missionState.isRecording -> "RECORDING MISSION"
                    missionState.availableFrames > 0 -> "LAST MISSION READY"
                    else -> "NO MISSION RECORDED"
                },
                color = if (missionState.isRecording || missionState.isReplaying) Mint else Muted,
                fontSize = 12.sp
            )
            Text(
                "Captured " + missionState.recordedFrames + " frames  •  Available " +
                    missionState.availableFrames + " frames  •  " +
                    (missionState.durationMs / 1000).toString() + " s",
                color = Muted,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (missionState.isRecording) {
                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber)
                    ) { Text("Stop recording", color = Color(0xFF17120A)) }
                } else {
                    Button(onClick = onStartRecording, enabled = !missionState.isReplaying) {
                        Text("Record mission")
                    }
                }
                if (missionState.isReplaying) {
                    OutlinedButton(onClick = onStopReplay) { Text("Stop replay") }
                } else {
                    OutlinedButton(onClick = onReplayMission, enabled = missionState.availableFrames > 0) {
                        Text("Replay last")
                    }
                }
            }
            Text(
                "Mission snapshots are stored in memory in this MVP. Hardware control is not enabled.",
                color = Muted,
                fontSize = 10.sp
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "POSE  x " + format(telemetry.xMeters) + " m   y " + format(telemetry.yMeters) +
                    " m   θ " + format(telemetry.headingRadians) + " rad",
                color = Muted,
                fontSize = 10.sp
            )
            Text("FRAME " + telemetry.sequence.toString().padStart(6, '0'), color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, accent: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Muted, fontSize = 10.sp)
            Text(value, color = accent, fontSize = 19.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PanelCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Blue, fontSize = 11.sp)
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun EventView(telemetry: RobotTelemetry) {
    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .background(Color(0xFF08111B), RoundedCornerShape(8.dp)).padding(8.dp)
    ) {
        telemetry.events.forEach { event ->
            val pointColor = if (event.polarity > 0) Mint else Red
            drawCircle(
                color = pointColor,
                radius = 2.2f,
                center = Offset(event.x * size.width, event.y * size.height)
            )
        }
    }
}

@Composable
private fun LidarView(telemetry: RobotTelemetry) {
    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .background(Color(0xFF08111B), RoundedCornerShape(8.dp)).padding(8.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.43f
        for (ring in 1..3) {
            drawCircle(
                color = Color(0xFF26384A),
                radius = maxRadius * ring / 3f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
        }
        drawLine(Color(0xFF26384A), Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1f)
        drawLine(Color(0xFF26384A), Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1f)
        telemetry.lidar.forEach { point ->
            val radius = (point.distanceMeters / 3f).coerceIn(0f, 1f) * maxRadius
            val position = Offset(
                center.x + cos(point.angleRadians) * radius,
                center.y + sin(point.angleRadians) * radius
            )
            drawLine(Mint.copy(alpha = 0.75f), center, position, 1f, cap = StrokeCap.Round)
            drawCircle(Mint, 2.2f, position)
        }
        drawCircle(Blue, 4f, center)
    }
}

private fun format(value: Float): String = ((value * 100).toInt() / 100f).toString()
