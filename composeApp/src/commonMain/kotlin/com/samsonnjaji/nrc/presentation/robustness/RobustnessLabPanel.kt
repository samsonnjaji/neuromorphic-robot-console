package com.samsonnjaji.nrc.presentation.robustness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsonnjaji.nrc.data.robustness.RobustnessLabState
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import com.samsonnjaji.nrc.domain.robustness.StressTestReport

private val Panel = Color(0xFF121D2A)
private val Muted = Color(0xFF91A3B5)
private val Mint = Color(0xFF73E0C1)
private val Blue = Color(0xFF80B9FF)
private val Amber = Color(0xFFFFC56E)
private val White = Color(0xFFE8F0F7)

@Composable
fun RobustnessLabPanel(
    state: RobustnessLabState,
    onScenarioSelected: (EnvironmentScenario) -> Unit,
    onRunTest: () -> Unit,
    onClearReport: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ENVIRONMENT ROBUSTNESS LAB", color = Blue, fontSize = 11.sp)
                Text("PROTOTYPE  /  SIMULATION", color = Amber, fontSize = 9.sp)
            }
            Text(
                "Test the same sensor snapshot under controlled environmental stress profiles.",
                color = Muted,
                fontSize = 11.sp
            )

            val scenarios = EnvironmentScenario.values()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                scenarios.take(3).forEach { scenario ->
                    ScenarioButton(
                        scenario = scenario,
                        selected = state.selectedScenario == scenario,
                        onClick = { onScenarioSelected(scenario) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                scenarios.drop(3).forEach { scenario ->
                    ScenarioButton(
                        scenario = scenario,
                        selected = state.selectedScenario == scenario,
                        onClick = { onScenarioSelected(scenario) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = Color(0xFF071610)
                    )
                ) {
                    Text("Run stress test")
                }
                if (state.latestReport != null) {
                    OutlinedButton(onClick = onClearReport) {
                        Text("Clear result", color = White)
                    }
                }
            }

            state.latestReport?.let { report ->
                StressReportView(report)
            } ?: Text(
                "Select a condition and run a test to compare sensor retention, detection confidence and inference latency.",
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ScenarioButton(
    scenario: EnvironmentScenario,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Mint.copy(alpha = 0.18f) else Color(0xFF1B2A39),
            contentColor = if (selected) Mint else White
        )
    ) {
        Text(scenario.displayName, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun StressReportView(report: StressTestReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(2.dp))
        Text("RESULT  /  " + report.scenario.displayName.uppercase(), color = Mint, fontSize = 12.sp)
        Text(
            report.manifest.experimentId + "  •  " +
                report.manifest.sourceType.name + "  •  " +
                report.manifest.modelIdentifier + " " + report.manifest.modelVersion,
            color = Muted,
            fontSize = 9.sp
        )
        Text(
            "Source frame " + report.manifest.sourceSequence +
                "  •  source timestamp " + report.manifest.sourceTimestampMs + " ms",
            color = Muted,
            fontSize = 9.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ResultMetric(
                "LiDAR retained",
                report.lidarRetentionPercent?.let { it.toString() + "%" } ?: "No samples",
                Modifier.weight(1f)
            )
            ResultMetric(
                "Events retained",
                report.eventRetentionPercent?.let { it.toString() + "%" } ?: "No samples",
                Modifier.weight(1f)
            )
            ResultMetric(
                "Noise added",
                report.addedNoiseEvents.toString(),
                Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ResultMetric(
                "Mean confidence",
                percent(report.meanConfidenceBefore) + " → " + percent(report.meanConfidenceAfter),
                Modifier.weight(1f)
            )
            ResultMetric(
                "Inference latency",
                number(report.latencyBeforeMs) + " → " + number(report.latencyAfterMs) + " ms",
                Modifier.weight(1f)
            )
        }
        Text(
            "Input: " + report.inputLidarPoints + " LiDAR points, " +
                report.inputEventSamples + " event samples. Output: " +
                report.retainedLidarPoints + " LiDAR points, " +
                report.retainedEventSamples + " retained events + " +
                report.addedNoiseEvents + " synthetic noise events.",
            color = Muted,
            fontSize = 10.sp
        )
        Text(
            "Important: coefficients are illustrative synthetic perturbations, not calibrated weather physics, measured model accuracy or a safety certification.",
            color = Amber,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ResultMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0B1520),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 9.sp)
            Text(value, color = White, fontSize = 13.sp)
        }
    }
}

private fun percent(value: Float?): String =
    value?.let { (it * 100f).toInt().toString() + "%" } ?: "N/A"

private fun number(value: Float): String =
    ((value * 100f).toInt() / 100f).toString()
