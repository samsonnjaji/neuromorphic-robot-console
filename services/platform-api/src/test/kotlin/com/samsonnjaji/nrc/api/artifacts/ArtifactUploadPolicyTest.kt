package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.model.CreateArtifactUploadRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactUploadPolicyTest {

    @Test
    fun smallAndLargeObjectsChooseCorrectMode() {
        assertEquals("SINGLE_PUT", artifactUploadPlan(SINGLE_PUT_THRESHOLD_BYTES).mode)

        val plan = artifactUploadPlan(SINGLE_PUT_THRESHOLD_BYTES + 1)
        assertEquals("MULTIPART", plan.mode)
        assertEquals(2, plan.partCount)
        assertTrue(requireNotNull(plan.partSizeBytes) >= MIN_MULTIPART_PART_SIZE_BYTES)
    }

    @Test
    fun maximumObjectSizeStaysWithinS3PartLimit() {
        val plan = artifactUploadPlan(MAX_ARTIFACT_SIZE_BYTES)
        assertEquals("MULTIPART", plan.mode)
        assertTrue(requireNotNull(plan.partCount) in 1..MAX_MULTIPART_PARTS)
    }

    @Test
    fun fileNamesCannotEscapeRecordingPrefix() {
        assertEquals("scene.mcap", safeArtifactFileName("../../scene.mcap"))
        assertEquals("recording.bin", safeArtifactFileName("../../"))
    }

    @Test
    fun uploadRequestValidatesSizeChecksumAndContentType() {
        val valid = CreateArtifactUploadRequest(
            fileName = "scene.mcap",
            contentType = "application/octet-stream",
            sizeBytes = 10,
            sha256Hex = "a".repeat(64)
        )
        assertNull(validateArtifactUploadRequest(valid))
        assertNotNull(validateArtifactUploadRequest(valid.copy(sizeBytes = 0)))
        assertNotNull(validateArtifactUploadRequest(valid.copy(sha256Hex = "bad")))
        assertNotNull(
            validateArtifactUploadRequest(
                valid.copy(contentType = "application/octet-stream\nX: bad")
            )
        )
    }
}
