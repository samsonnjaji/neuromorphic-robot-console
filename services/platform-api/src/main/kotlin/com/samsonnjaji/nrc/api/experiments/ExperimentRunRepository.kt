package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateExperimentRunRequest
import com.samsonnjaji.nrc.api.model.ExperimentRunRecord
import com.samsonnjaji.nrc.api.model.TransitionExperimentRunRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface ExperimentRunRepository {
    suspend fun create(experimentId: String, request: CreateExperimentRunRequest): ExperimentRunRecord
    suspend fun listByExperiment(experimentId: String): List<ExperimentRunRecord>
    suspend fun find(id: String): ExperimentRunRecord?
    suspend fun transition(
        id: String,
        request: TransitionExperimentRunRequest
    ): RepositoryUpdate<ExperimentRunRecord>
}

class InMemoryExperimentRunRepository : ExperimentRunRepository {
    private val records = ConcurrentHashMap<String, ExperimentRunRecord>()

    override suspend fun create(
        experimentId: String,
        request: CreateExperimentRunRequest
    ): ExperimentRunRecord = synchronized(records) {
        val now = System.currentTimeMillis()
        ExperimentRunRecord(
            id = UUID.randomUUID().toString(),
            experimentId = experimentId,
            status = "CREATED",
            sourceRevision = request.sourceRevision.trim(),
            configuration = request.configuration,
            createdAtUnixMs = now,
            updatedAtUnixMs = now
        ).also { records[it.id] = it }
    }

    override suspend fun listByExperiment(experimentId: String): List<ExperimentRunRecord> =
        records.values
            .filter { it.experimentId == experimentId }
            .sortedWith(compareByDescending<ExperimentRunRecord> { it.createdAtUnixMs }.thenByDescending { it.id })

    override suspend fun find(id: String): ExperimentRunRecord? = records[id]

    override suspend fun transition(
        id: String,
        request: TransitionExperimentRunRequest
    ): RepositoryUpdate<ExperimentRunRecord> = synchronized(records) {
        val current = records[id] ?: return@synchronized RepositoryUpdate.NotFound
        if (!canTransitionExperimentRun(current.status, request.status)) {
            return@synchronized RepositoryUpdate.InvalidTransition
        }

        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = request.status,
            resultSummary = request.resultSummary ?: current.resultSummary,
            failureReason = if (request.status == "FAILED") request.failureReason.trim() else current.failureReason,
            startedAtUnixMs = if (request.status == "RUNNING") current.startedAtUnixMs ?: now else current.startedAtUnixMs,
            finishedAtUnixMs = if (request.status in setOf("COMPLETED", "FAILED", "CANCELLED")) now else current.finishedAtUnixMs,
            updatedAtUnixMs = now
        )
        records[id] = updated
        RepositoryUpdate.Updated(updated)
    }
}
