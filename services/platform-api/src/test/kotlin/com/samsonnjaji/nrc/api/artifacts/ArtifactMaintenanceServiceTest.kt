package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.model.ArtifactRecord
import com.samsonnjaji.nrc.api.storage.ArtifactObjectStorage
import com.samsonnjaji.nrc.api.storage.CompletedObjectPart
import com.samsonnjaji.nrc.api.storage.ObjectVerification
import com.samsonnjaji.nrc.api.storage.SignedObjectUpload
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ArtifactMaintenanceServiceTest {
    @Test
    fun expiredSingleUploadIsAbortedAndDeleted() = runBlocking {
        val now = 10_000_000L
        val repository = InMemoryArtifactRepository()
        val storage = RecordingStorage()
        val artifact = artifact("single", "SINGLE_PUT", now - 90_000, null)
        repository.create(artifact)
        val summary = service(repository, storage).runOnce(now)
        assertEquals(1, summary.candidates)
        assertEquals(1, summary.newlyAborted)
        assertEquals(1, summary.cleaned)
        assertEquals(0, summary.failed)
        assertEquals("ABORTED", repository.find(artifact.id)?.status)
        assertEquals(now, repository.find(artifact.id)?.cleanupCompletedAtUnixMs)
        assertEquals(listOf(artifact.objectKey), storage.deletedKeys)
        assertEquals(emptyList(), storage.abortedUploads)
    }

    @Test
    fun expiredMultipartUploadIsAbortedBeforeObjectDeletion() = runBlocking {
        val now = 10_000_000L
        val repository = InMemoryArtifactRepository()
        val storage = RecordingStorage()
        val artifact = artifact("multipart", "MULTIPART", now - 90_000, "upload-123")
        repository.create(artifact)
        val summary = service(repository, storage).runOnce(now)
        assertEquals(1, summary.cleaned)
        assertEquals(listOf(artifact.objectKey to "upload-123"), storage.abortedUploads)
        assertEquals(listOf(artifact.objectKey), storage.deletedKeys)
    }

    @Test
    fun failedCleanupRemainsRetryable() = runBlocking {
        val now = 10_000_000L
        val repository = InMemoryArtifactRepository()
        val storage = RecordingStorage().apply { failuresRemaining = 1 }
        val artifact = artifact("retry", "SINGLE_PUT", now - 90_000, null)
        repository.create(artifact)
        val service = service(repository, storage)
        val first = service.runOnce(now)
        assertEquals(1, first.failed)
        assertEquals("ABORTED", repository.find(artifact.id)?.status)
        assertNull(repository.find(artifact.id)?.cleanupCompletedAtUnixMs)
        val second = service.runOnce(now + 1)
        assertEquals(1, second.cleaned)
        assertNotNull(repository.find(artifact.id)?.cleanupCompletedAtUnixMs)
        assertEquals(2, storage.deletedKeys.size)
    }

    @Test
    fun activeUploadsAreNotSelectedForCleanup() = runBlocking {
        val now = 10_000_000L
        val repository = InMemoryArtifactRepository()
        val storage = RecordingStorage()
        val artifact = artifact("active", "SINGLE_PUT", now + 60_000, null)
        repository.create(artifact)
        val summary = service(repository, storage).runOnce(now)
        assertEquals(0, summary.candidates)
        assertEquals("PENDING_UPLOAD", repository.find(artifact.id)?.status)
        assertEquals(emptyList(), storage.deletedKeys)
    }

    private fun service(repository: InMemoryArtifactRepository, storage: RecordingStorage) =
        ArtifactMaintenanceService(
            repository, storage,
            ArtifactMaintenanceConfig(
                pendingUploadTtlMs = 60_000,
                cleanupGraceMs = 0,
                cleanupIntervalMs = 1_000,
                cleanupBatchSize = 20
            )
        )

    private fun artifact(id: String, mode: String, expiry: Long, uploadId: String?) = ArtifactRecord(
        id = id,
        recordingSessionId = "recording-$id",
        status = "PENDING_UPLOAD",
        uploadMode = mode,
        fileName = "$id.mcap",
        contentType = "application/octet-stream",
        sizeBytes = 10,
        sha256Hex = "a".repeat(64),
        bucket = "nrc-test-artifacts",
        objectKey = "recordings/$id/$id.mcap",
        multipartUploadId = uploadId,
        partSizeBytes = if (mode == "MULTIPART") 5L * 1024 * 1024 else null,
        partCount = if (mode == "MULTIPART") 1 else null,
        createdAtUnixMs = expiry - 60_000,
        uploadUrlExpiresAtUnixMs = expiry
    )

    private class RecordingStorage : ArtifactObjectStorage {
        override val bucket = "nrc-test-artifacts"
        val deletedKeys = mutableListOf<String>()
        val abortedUploads = mutableListOf<Pair<String, String>>()
        var failuresRemaining = 0
        override fun ensureBucket() = Unit
        override suspend fun createSingleUpload(objectKey: String, contentType: String, sizeBytes: Long) =
            SignedObjectUpload("https://upload.invalid/$objectKey", mapOf("Content-Type" to contentType), 0)
        override suspend fun initiateMultipartUpload(objectKey: String, contentType: String) = "upload"
        override suspend fun createPartUpload(objectKey: String, uploadId: String, partNumber: Int) =
            SignedObjectUpload("https://upload.invalid/$objectKey/$partNumber", emptyMap(), 0)
        override suspend fun completeMultipartUpload(objectKey: String, uploadId: String, parts: List<CompletedObjectPart>) = Unit
        override suspend fun abortMultipartUpload(objectKey: String, uploadId: String) {
            abortedUploads += objectKey to uploadId
        }
        override suspend fun deleteObject(objectKey: String) {
            deletedKeys += objectKey
            if (failuresRemaining > 0) {
                failuresRemaining--
                error("simulated object-store outage")
            }
        }
        override suspend fun verifyObject(objectKey: String): ObjectVerification? = null
        override suspend fun createDownloadUrl(objectKey: String) =
            SignedObjectUpload("https://download.invalid/$objectKey", emptyMap(), 0)
        override fun close() = Unit
    }
}
