package com.samsonnjaji.nrc.api.experiments

import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.ExperimentRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface ExperimentRepository {
    suspend fun create(request: CreateExperimentRequest): ExperimentRecord
    suspend fun list(): List<ExperimentRecord>
    suspend fun find(id: String): ExperimentRecord?
}

/**
 * Development-only repository. Replace with PostgreSQL-backed persistence before
 * multi-user or production deployment.
 */
class InMemoryExperimentRepository : ExperimentRepository {
    private val records = ConcurrentHashMap<String, ExperimentRecord>()

    override suspend fun create(request: CreateExperimentRequest): ExperimentRecord {
        val record = ExperimentRecord(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            description = request.description.trim(),
            scenario = request.scenario,
            sourceType = request.sourceType,
            sourceId = request.sourceId.trim(),
            modelIdentifier = request.modelIdentifier.trim(),
            modelVersion = request.modelVersion.trim(),
            createdAtUnixMs = System.currentTimeMillis()
        )
        records[record.id] = record
        return record
    }

    override suspend fun list(): List<ExperimentRecord> =
        records.values.sortedByDescending { it.createdAtUnixMs }

    override suspend fun find(id: String): ExperimentRecord? = records[id]
}
