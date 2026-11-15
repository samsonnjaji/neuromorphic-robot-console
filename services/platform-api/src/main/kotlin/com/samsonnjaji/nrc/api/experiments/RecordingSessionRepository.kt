package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateRecordingSessionRequest
import com.samsonnjaji.nrc.api.model.RecordingSessionRecord
import com.samsonnjaji.nrc.api.model.TransitionRecordingSessionRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface RecordingSessionRepository {
    suspend fun create(
        experimentRunId: String,
        request: CreateRecordingSessionRequest
    ): RecordingSessionRecord
    suspend fun listByRun(experimentRunId: String): List<RecordingSessionRecord>
    suspend fun find(id: String): RecordingSessionRecord?
    suspend fun transition(
        id: String,
        request: TransitionRecordingSessionRequest
    ): RepositoryUpdate<RecordingSessionRecord>
}

class InMemoryRecordingSessionRepository : RecordingSessionRepository {
    private val records = ConcurrentHashMap<String, RecordingSessionRecord>()

    override suspend fun create(
        experimentRunId: String,
        request: CreateRecordingSessionRequest
    ): RecordingSessionRecord = synchronized(records) {
        val now = System.currentTimeMillis()
        RecordingSessionRecord(
            id = UUID.randomUUID().toString(),
            experimentRunId = experimentRunId,
            status = "CREATED",
            format = request.format,
            sourceId = request.sourceId.trim(),
            sampleCount = 0,
            metadata = request.metadata,
            createdAtUnixMs = now,
            updatedAtUnixMs = now
        ).also { records[it.id] = it }
    }

    override suspend fun listByRun(experimentRunId: String): List<RecordingSessionRecord> =
        records.values
            .filter { it.experimentRunId == experimentRunId }
            .sortedWith(compareByDescending<RecordingSessionRecord> { it.createdAtUnixMs }.thenByDescending { it.id })

    override suspend fun find(id: String): RecordingSessionRecord? = records[id]

    override suspend fun transition(
        id: String,
        request: TransitionRecordingSessionRequest
    ): RepositoryUpdate<RecordingSessionRecord> = synchronized(records) {
        val current = records[id] ?: return@synchronized RepositoryUpdate.NotFound
        if (!canTransitionRecordingSession(current.status, request.status)) {
            return@synchronized RepositoryUpdate.InvalidTransition
        }

        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = request.status,
            sampleCount = request.sampleCount ?: current.sampleCount,
            metadata = request.metadata ?: current.metadata,
            failureReason = if (request.status == "FAILED") request.failureReason.trim() else current.failureReason,
            startedAtUnixMs = if (request.status == "RECORDING") current.startedAtUnixMs ?: now else current.startedAtUnixMs,
            finishedAtUnixMs = if (request.status in setOf("FINALIZED", "FAILED", "CANCELLED")) now else current.finishedAtUnixMs,
            updatedAtUnixMs = now
        )
        records[id] = updated
        RepositoryUpdate.Updated(updated)
    }
}
