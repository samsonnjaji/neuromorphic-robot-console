package com.samsonnjaji.nrc.api.artifacts

import com.samsonnjaji.nrc.api.model.CreateArtifactUploadRequest

data class ArtifactUploadPlan(
    val mode: String,
    val partSizeBytes: Long? = null,
    val partCount: Int? = null
)

const val SINGLE_PUT_THRESHOLD_BYTES = 64L * 1024 * 1024
const val DEFAULT_MULTIPART_PART_SIZE_BYTES = 64L * 1024 * 1024
const val MIN_MULTIPART_PART_SIZE_BYTES = 5L * 1024 * 1024
const val MAX_MULTIPART_PARTS = 10_000
const val MAX_ARTIFACT_SIZE_BYTES = 5L * 1024 * 1024 * 1024 * 1024
private const val PART_SIZE_ALIGNMENT_BYTES = 1024L * 1024

fun validateArtifactUploadRequest(request: CreateArtifactUploadRequest): String? {
    val fileName = request.fileName.trim()
    if (fileName.isBlank()) return "A file name is required."
    if (fileName.length > 255) return "File name must be at most 255 characters."
    if (fileName.any { it.code < 32 || it.code == 127 }) return "File name contains control characters."
    if (request.contentType.isBlank() || request.contentType.length > 255 ||
        request.contentType.any { it.code < 32 || it.code == 127 }
    ) {
        return "Content type must contain between 1 and 255 printable characters."
    }
    if (request.sizeBytes <= 0) return "Artifact size must be greater than zero."
    if (request.sizeBytes > MAX_ARTIFACT_SIZE_BYTES) return "Artifact exceeds the S3 maximum object size of 5 TiB."
    if (!request.sha256Hex.matches(Regex("^[0-9a-fA-F]{64}$"))) {
        return "SHA-256 must be supplied as 64 hexadecimal characters."
    }
    return null
}

fun safeArtifactFileName(value: String): String {
    val baseName = value.trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val sanitized = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.')
        .take(120)
    return sanitized.ifBlank { "recording.bin" }
}

fun artifactUploadPlan(sizeBytes: Long): ArtifactUploadPlan {
    require(sizeBytes > 0 && sizeBytes <= MAX_ARTIFACT_SIZE_BYTES)
    if (sizeBytes <= SINGLE_PUT_THRESHOLD_BYTES) {
        return ArtifactUploadPlan(mode = "SINGLE_PUT")
    }

    val minimumForPartLimit = ceilDiv(sizeBytes, MAX_MULTIPART_PARTS.toLong())
    val unalignedPartSize = maxOf(DEFAULT_MULTIPART_PART_SIZE_BYTES, minimumForPartLimit)
    val partSize = roundUp(unalignedPartSize, PART_SIZE_ALIGNMENT_BYTES)
        .coerceAtLeast(MIN_MULTIPART_PART_SIZE_BYTES)
    val partCount = ceilDiv(sizeBytes, partSize).toInt()
    require(partCount in 1..MAX_MULTIPART_PARTS)
    return ArtifactUploadPlan(
        mode = "MULTIPART",
        partSizeBytes = partSize,
        partCount = partCount
    )
}

private fun ceilDiv(value: Long, divisor: Long): Long =
    (value + divisor - 1) / divisor

private fun roundUp(value: Long, alignment: Long): Long =
    ceilDiv(value, alignment) * alignment
