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
}

class InMemoryArtifactRepository : ArtifactRepository {
    private val records = ConcurrentHashMap<String, ArtifactRecord>()

    override suspend fun create(record: ArtifactRecord): ArtifactRecord =
        record.also { records[it.id] = it }

    override suspend fun find(id: String): ArtifactRecord? = records[id]

    override suspend fun listByRecordingSession(recordingSessionId: String): List<ArtifactRecord> =
        records.values
            .filter { it.recordingSessionId == recordingSessionId }
            .sortedWith(
                compareByDescending<ArtifactRecord> { it.createdAtUnixMs }
                    .thenByDescending { it.id }
            )

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
}
