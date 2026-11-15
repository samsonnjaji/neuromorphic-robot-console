package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.model.ArtifactRecord
import java.util.concurrent.ConcurrentHashMap

interface ArtifactRepository {
    suspend fun create(record: ArtifactRecord): ArtifactRecord
    suspend fun find(id: String): ArtifactRecord?
    suspend fun listByRecordingSession(recordingSessionId: String): List<ArtifactRecord>

    suspend fun updateStatus(
        id: String,
        status: String,
        etag: String = "",
        failureReason: String = "",
        verifiedAtUnixMs: Long? = null
    ): RepositoryUpdate<ArtifactRecord>

    suspend fun updateUploadUrlExpiry(id: String, expiresAtUnixMs: Long): ArtifactRecord?
    suspend fun listCleanupCandidates(expiredBeforeUnixMs: Long, limit: Int): List<ArtifactRecord>
    suspend fun abortIfExpired(id: String, expiredBeforeUnixMs: Long): RepositoryUpdate<ArtifactRecord>
    suspend fun markCleanupCompleted(id: String, completedAtUnixMs: Long): Boolean
}

class InMemoryArtifactRepository : ArtifactRepository {
    private val records = ConcurrentHashMap<String, ArtifactRecord>()

    override suspend fun create(record: ArtifactRecord): ArtifactRecord =
        record.also { records[it.id] = it }

    override suspend fun find(id: String): ArtifactRecord? = records[id]

    override suspend fun listByRecordingSession(recordingSessionId: String): List<ArtifactRecord> =
        records.values
            .filter { it.recordingSessionId == recordingSessionId }
            .sortedWith(compareByDescending<ArtifactRecord> { it.createdAtUnixMs }.thenByDescending { it.id })

    override suspend fun updateStatus(
        id: String,
        status: String,
        etag: String,
        failureReason: String,
        verifiedAtUnixMs: Long?
    ): RepositoryUpdate<ArtifactRecord> = synchronized(records) {
        val current = records[id] ?: return@synchronized RepositoryUpdate.NotFound
        if (current.status != "PENDING_UPLOAD" || status !in setOf("VERIFIED", "FAILED", "ABORTED")) {
            return@synchronized RepositoryUpdate.InvalidTransition
        }
        val updated = current.copy(
            status = status,
            etag = etag,
            failureReason = failureReason,
            verifiedAtUnixMs = verifiedAtUnixMs
        )
        records[id] = updated
        RepositoryUpdate.Updated(updated)
    }

    override suspend fun updateUploadUrlExpiry(id: String, expiresAtUnixMs: Long): ArtifactRecord? =
        synchronized(records) {
            val current = records[id] ?: return@synchronized null
            if (current.status != "PENDING_UPLOAD") return@synchronized null
            current.copy(
                uploadUrlExpiresAtUnixMs = maxOf(current.uploadUrlExpiresAtUnixMs, expiresAtUnixMs)
            ).also { records[id] = it }
        }

    override suspend fun listCleanupCandidates(
        expiredBeforeUnixMs: Long,
        limit: Int
    ): List<ArtifactRecord> = records.values
        .filter {
            (it.status == "PENDING_UPLOAD" && it.uploadUrlExpiresAtUnixMs <= expiredBeforeUnixMs) ||
                (it.status == "FAILED" && it.uploadUrlExpiresAtUnixMs <= expiredBeforeUnixMs && it.cleanupCompletedAtUnixMs == null) ||
                (it.status == "ABORTED" && it.cleanupCompletedAtUnixMs == null)
        }
        .sortedWith(compareBy<ArtifactRecord> { it.uploadUrlExpiresAtUnixMs }.thenBy { it.createdAtUnixMs })
        .take(limit.coerceAtLeast(0))

    override suspend fun abortIfExpired(
        id: String,
        expiredBeforeUnixMs: Long
    ): RepositoryUpdate<ArtifactRecord> = synchronized(records) {
        val current = records[id] ?: return@synchronized RepositoryUpdate.NotFound
        if (current.status != "PENDING_UPLOAD" || current.uploadUrlExpiresAtUnixMs > expiredBeforeUnixMs) {
            return@synchronized RepositoryUpdate.InvalidTransition
        }
        val updated = current.copy(
            status = "ABORTED",
            failureReason = "Upload expired before verification."
        )
        records[id] = updated
        RepositoryUpdate.Updated(updated)
    }

    override suspend fun markCleanupCompleted(id: String, completedAtUnixMs: Long): Boolean =
        synchronized(records) {
            val current = records[id] ?: return@synchronized false
            if (current.status !in setOf("ABORTED", "FAILED") || current.cleanupCompletedAtUnixMs != null) {
                return@synchronized false
            }
            records[id] = current.copy(cleanupCompletedAtUnixMs = completedAtUnixMs)
            true
        }
}
