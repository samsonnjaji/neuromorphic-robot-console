package com.samsonnjaji.nrc.domain.robustness

import com.samsonnjaji.nrc.domain.model.Detection
import com.samsonnjaji.nrc.domain.model.EventSample
import com.samsonnjaji.nrc.domain.model.LidarPoint
import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RobustnessEvaluatorTest {
    private val evaluator = RobustnessEvaluator()

    private fun baseline() = RobotTelemetry(
        sequence = 24,
        timestampMs = 2_400,
        inferenceLatencyMs = 10f,
        eventRateHz = 10_000,
        lidar = (0 until 10).map {
            LidarPoint(it / 10f * (2f * PI.toFloat()), 2f)
        },
        events = (0 until 100).map {
            EventSample(it / 100f, (it * 3 % 100) / 100f, if (it % 2 == 0) 1 else -1)
        },
        detections = listOf(
            Detection("Pedestrian", 0.9f, -10f),
            Detection("Obstacle", 0.7f, 15f)
        )
    )

    @Test
    fun clearBaselinePreservesSensorSamplesAndModelMetrics() {
        val report = evaluator.evaluate(baseline(), EnvironmentScenario.CLEAR)

        assertEquals(10, report.retainedLidarPoints)
        assertEquals(100, report.retainedEventSamples)
        assertEquals(0, report.addedNoiseEvents)
        assertTrue(abs((report.meanConfidenceBefore ?: 0f) - 0.8f) < 0.0001f)
        assertTrue(abs((report.meanConfidenceAfter ?: 0f) - 0.8f) < 0.0001f)
        assertEquals(10f, report.latencyAfterMs)
    }

    @Test
    fun manifestCapturesSourceModelScenarioAndProfileProvenance() {
        val report = evaluator.evaluate(
            baseline(),
            EnvironmentScenario.HEAVY_RAIN,
            experimentId = "NRC-TEST-0001"
        )

        assertEquals(1, report.manifest.manifestVersion)
        assertEquals("NRC-TEST-0001", report.manifest.experimentId)
        assertEquals(ExperimentSourceType.SIMULATOR, report.manifest.sourceType)
        assertEquals("nrc-demo-simulator", report.manifest.sourceId)
        assertEquals(24L, report.manifest.sourceSequence)
        assertEquals(2_400L, report.manifest.sourceTimestampMs)
        assertEquals("demo-perception-stack", report.manifest.modelIdentifier)
        assertEquals(EnvironmentScenario.HEAVY_RAIN, report.manifest.scenario)
        assertEquals(0.72f, report.manifest.lidarRetention)
    }

    @Test
    fun rainProfileAppliesDeterministicSensorAndInferencePerturbations() {
        val input = baseline()
        val first = evaluator.evaluate(input, EnvironmentScenario.HEAVY_RAIN)
        val second = evaluator.evaluate(input, EnvironmentScenario.HEAVY_RAIN)

        assertEquals(7, first.retainedLidarPoints)
        assertEquals(82, first.retainedEventSamples)
        assertEquals(12, first.addedNoiseEvents)
        assertEquals(11.2f, first.latencyAfterMs)
        assertEquals(first.stressedTelemetry, second.stressedTelemetry)
        assertTrue(first.meanConfidenceAfter!! < first.meanConfidenceBefore!!)
    }

    @Test
    fun emptySensorStreamsAreHandledWithoutDivisionByZero() {
        val report = evaluator.evaluate(
            RobotTelemetry(),
            EnvironmentScenario.FOG
        )

        assertEquals(null, report.lidarRetentionPercent)
        assertEquals(null, report.eventRetentionPercent)
        assertEquals(null, report.confidenceRetentionPercent)
        assertEquals(0, report.retainedLidarPoints)
        assertEquals(0, report.retainedEventSamples)
    }
}
