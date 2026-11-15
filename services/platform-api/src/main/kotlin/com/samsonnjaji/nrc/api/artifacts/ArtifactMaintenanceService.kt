package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.storage.ArtifactObjectStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val DEFAULT_PENDING_UPLOAD_TTL_MS = 24L * 60 * 60 * 1_000
const val DEFAULT_CLEANUP_GRACE_MS = 60L * 60 * 1_000
const val DEFAULT_CLEANUP_INTERVAL_MS = 15L * 60 * 1_000
const val DEFAULT_CLEANUP_BATCH_SIZE = 100

data class ArtifactMaintenanceConfig(
    val pendingUploadTtlMs: Long = DEFAULT_PENDING_UPLOAD_TTL_MS,
    val cleanupGraceMs: Long = DEFAULT_CLEANUP_GRACE_MS,
    val cleanupIntervalMs: Long = DEFAULT_CLEANUP_INTERVAL_MS,
    val cleanupBatchSize: Int = DEFAULT_CLEANUP_BATCH_SIZE
) {
    init {
        require(pendingUploadTtlMs in 60_000L..(30L * 24 * 60 * 60 * 1_000))
        require(cleanupGraceMs in 0L..(7L * 24 * 60 * 60 * 1_000))
        require(cleanupIntervalMs in 1_000L..(24L * 60 * 60 * 1_000))
        require(cleanupBatchSize in 1..1_000)
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ArtifactMaintenanceConfig {
            fun seconds(name: String, defaultMs: Long, allowed: LongRange): Long {
                val raw = environment[name]?.takeIf { it.isNotBlank() } ?: return defaultMs
                val value = requireNotNull(raw.toLongOrNull()) { "$name must be an integer number of seconds." }
                require(value in allowed) { "$name is outside the supported range." }
                return value * 1_000
            }
            return ArtifactMaintenanceConfig(
                pendingUploadTtlMs = seconds("NRC_ARTIFACT_PENDING_UPLOAD_TTL_SECONDS", DEFAULT_PENDING_UPLOAD_TTL_MS, 60L..2_592_000L),
                cleanupGraceMs = seconds("NRC_ARTIFACT_CLEANUP_GRACE_SECONDS", DEFAULT_CLEANUP_GRACE_MS, 0L..604_800L),
                cleanupIntervalMs = seconds("NRC_ARTIFACT_CLEANUP_INTERVAL_SECONDS", DEFAULT_CLEANUP_INTERVAL_MS, 1L..86_400L),
                cleanupBatchSize = environment["NRC_ARTIFACT_CLEANUP_BATCH_SIZE"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        val value = requireNotNull(raw.toIntOrNull()) { "NRC_ARTIFACT_CLEANUP_BATCH_SIZE must be an integer." }
                        require(value in 1..1_000) { "NRC_ARTIFACT_CLEANUP_BATCH_SIZE must be between 1 and 1000." }
                        value
                    } ?: DEFAULT_CLEANUP_BATCH_SIZE
            )
        }
    }
}

data class ArtifactCleanupSummary(
    val candidates: Int,
    val newlyAborted: Int,
    val cleaned: Int,
    val failed: Int
)

class ArtifactMaintenanceService(
    private val repository: ArtifactRepository,
    private val storage: ArtifactObjectStorage,
    private val config: ArtifactMaintenanceConfig = ArtifactMaintenanceConfig()
) {
    private val runMutex = Mutex()

    suspend fun runOnce(nowUnixMs: Long = System.currentTimeMillis()): ArtifactCleanupSummary =
        runMutex.withLock {
            val expiredBefore = nowUnixMs - config.cleanupGraceMs
            val candidates = repository.listCleanupCandidates(expiredBefore, config.cleanupBatchSize)
            var newlyAborted = 0
            var cleaned = 0
            var failed = 0

            for (candidate in candidates) {
                val cleanupRecord = when (candidate.status) {
                    "PENDING_UPLOAD" -> when (
                        val result = repository.abortIfExpired(candidate.id, expiredBefore)
                    ) {
                        is RepositoryUpdate.Updated -> {
                            newlyAborted++
                            result.value
                        }
                        RepositoryUpdate.NotFound, RepositoryUpdate.InvalidTransition -> null
                    }
                    "ABORTED", "FAILED" -> candidate.takeIf { it.cleanupCompletedAtUnixMs == null }
                    else -> null
                } ?: continue

                try {
                    val uploadId = cleanupRecord.multipartUploadId
                    if (cleanupRecord.uploadMode == "MULTIPART" && !uploadId.isNullOrBlank()) {
                        storage.abortMultipartUpload(cleanupRecord.objectKey, uploadId)
                    }
                    storage.deleteObject(cleanupRecord.objectKey)
                    repository.markCleanupCompleted(cleanupRecord.id, nowUnixMs)
                    cleaned++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failed++
                    System.err.println(
                        "NRC artifact cleanup failed for " + cleanupRecord.id +
                            ": " + failure.javaClass.simpleName
                    )
                }
            }

            ArtifactCleanupSummary(candidates.size, newlyAborted, cleaned, failed)
        }

    suspend fun runPeriodically() {
        while (currentCoroutineContext().isActive) {
            try {
                val summary = runOnce()
                if (summary.candidates > 0) {
                    println(
                        "NRC artifact cleanup: candidates=" + summary.candidates +
                            ", aborted=" + summary.newlyAborted +
                            ", cleaned=" + summary.cleaned +
                            ", failed=" + summary.failed
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                System.err.println("NRC artifact cleanup cycle failed: " + failure.javaClass.simpleName)
            }
            delay(config.cleanupIntervalMs)
        }
    }
}
