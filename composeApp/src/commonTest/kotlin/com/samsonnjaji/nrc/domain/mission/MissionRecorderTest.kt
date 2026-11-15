package com.samsonnjaji.nrc.domain.mission

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionRecorderTest {
    @Test
    fun capturesFramesWithRelativeTimestamps() {
        val recorder = MissionRecorder()
        recorder.start()

        assertTrue(recorder.append(RobotTelemetry(sequence = 1, timestampMs = 1_000)))
        assertTrue(recorder.append(RobotTelemetry(sequence = 2, timestampMs = 1_100)))
        val recording = assertNotNull(recorder.stop())

        assertEquals(2, recording.frameCount)
        assertEquals(0L, recording.frames.first().offsetMs)
        assertEquals(100L, recording.frames.last().offsetMs)
        assertEquals(100L, recording.durationMs)
    }

    @Test
    fun doesNotCaptureWhenStopped() {
        val recorder = MissionRecorder()
        assertFalse(recorder.append(RobotTelemetry(timestampMs = 100)))
        assertNull(recorder.stop())
    }

    @Test
    fun rejectsOutOfOrderTimestampsWithoutCorruptingRecording() {
        val recorder = MissionRecorder()
        recorder.start()
        assertTrue(recorder.append(RobotTelemetry(timestampMs = 200)))
        assertFalse(recorder.append(RobotTelemetry(timestampMs = 150)))
        assertTrue(recorder.append(RobotTelemetry(timestampMs = 300)))

        val recording = assertNotNull(recorder.stop())
        assertEquals(listOf(0L, 100L), recording.frames.map { it.offsetMs })
    }

    @Test
    fun stopsAtConfiguredFrameLimit() {
        val recorder = MissionRecorder(maxFrames = 2)
        recorder.start()
        assertTrue(recorder.append(RobotTelemetry(timestampMs = 0)))
        assertTrue(recorder.append(RobotTelemetry(timestampMs = 100)))
        assertFalse(recorder.isRecording)
        assertFalse(recorder.append(RobotTelemetry(timestampMs = 200)))
        assertEquals(2, assertNotNull(recorder.stop()).frameCount)
    }
}
