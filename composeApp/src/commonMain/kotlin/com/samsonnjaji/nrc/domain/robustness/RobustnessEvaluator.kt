package com.samsonnjaji.nrc.domain.robustness

import com.samsonnjaji.nrc.domain.model.EventSample
import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlin.math.roundToInt

/**
 * Applies reproducible, synthetic sensor perturbations to a telemetry snapshot.
 *
 * This is an engineering prototype for validating the NRC experiment workflow.
 * It does not simulate calibrated atmospheric optics, sensor hardware, or model accuracy.
 */
class RobustnessEvaluator {
    fun evaluate(
        baseline: RobotTelemetry,
        scenario: EnvironmentScenario
    ): StressTestReport {
        val retainedLidar = retainDeterministically(baseline.lidar, scenario.lidarRetention)
            .mapIndexed { index, point ->
                val signedOffset = when (index % 3) {
                    0 -> -1f
                    1 -> 0f
                    else -> 1f
                }
                point.copy(
                    distanceMeters = (point.distanceMeters +
                        signedOffset * scenario.lidarNoiseMeters).coerceAtLeast(0.05f)
                )
            }

        val retainedEvents = retainDeterministically(baseline.events, scenario.eventRetention)
        val noiseCount = (baseline.events.size * scenario.addedEventNoiseFraction).roundToInt()
        val noiseEvents = List(noiseCount) { index ->
            EventSample(
                x = ((index * 37 + 11) % 100) / 100f,
                y = ((index * 53 + 17) % 100) / 100f,
                polarity = if (index % 2 == 0) 1 else -1
            )
        }

        val beforeConfidence = meanConfidence(baseline)
        val stressed = baseline.copy(
            lidar = retainedLidar,
            events = retainedEvents + noiseEvents,
            detections = baseline.detections.map { detection ->
                detection.copy(
                    confidence = (detection.confidence * scenario.detectionConfidenceScale)
                        .coerceIn(0f, 1f)
                )
            },
            inferenceLatencyMs = baseline.inferenceLatencyMs * scenario.inferenceLatencyScale,
            eventRateHz = (baseline.eventRateHz *
                (scenario.eventRetention + scenario.addedEventNoiseFraction)).roundToInt()
        )

        return StressTestReport(
            scenario = scenario,
            stressedTelemetry = stressed,
            inputLidarPoints = baseline.lidar.size,
            retainedLidarPoints = retainedLidar.size,
            inputEventSamples = baseline.events.size,
            retainedEventSamples = retainedEvents.size,
            addedNoiseEvents = noiseEvents.size,
            meanConfidenceBefore = beforeConfidence,
            meanConfidenceAfter = meanConfidence(stressed),
            latencyBeforeMs = baseline.inferenceLatencyMs,
            latencyAfterMs = stressed.inferenceLatencyMs
        )
    }

    private fun <T> retainDeterministically(items: List<T>, retention: Float): List<T> {
        if (items.isEmpty() || retention <= 0f) return emptyList()
        if (retention >= 1f) return items

        val keepCount = (items.size * retention).roundToInt().coerceIn(0, items.size)
        if (keepCount == 0) return emptyList()

        return items.filterIndexed { index, _ ->
            ((index + 1) * keepCount / items.size) > (index * keepCount / items.size)
        }
    }

    private fun meanConfidence(telemetry: RobotTelemetry): Float? =
        telemetry.detections.takeIf { it.isNotEmpty() }
            ?.map { it.confidence }
            ?.average()
            ?.toFloat()
}
