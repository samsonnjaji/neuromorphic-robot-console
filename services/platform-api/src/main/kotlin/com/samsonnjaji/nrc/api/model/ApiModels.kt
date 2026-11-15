package com.samsonnjaji.nrc.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val apiVersion: String
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

@Serializable
data class CreateExperimentRequest(
    val name: String,
    val description: String = "",
    val scenario: String,
    val sourceType: String,
    val sourceId: String,
    val modelIdentifier: String,
    val modelVersion: String
)

@Serializable
data class ExperimentRecord(
    val id: String,
    val name: String,
    val description: String,
    val scenario: String,
    val sourceType: String,
    val sourceId: String,
    val modelIdentifier: String,
    val modelVersion: String,
    val createdAtUnixMs: Long
)

@Serializable
data class CreateExperimentRunRequest(
    val sourceRevision: String = "",
    val configuration: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class ExperimentRunRecord(
    val id: String,
    val experimentId: String,
    val status: String,
    val sourceRevision: String,
    val configuration: JsonObject,
    val resultSummary: JsonObject? = null,
    val failureReason: String = "",
    val createdAtUnixMs: Long,
    val startedAtUnixMs: Long? = null,
    val finishedAtUnixMs: Long? = null,
    val updatedAtUnixMs: Long
)

@Serializable
data class TransitionExperimentRunRequest(
    val status: String,
    val resultSummary: JsonObject? = null,
    val failureReason: String = ""
)

@Serializable
data class CreateRecordingSessionRequest(
    val format: String,
    val sourceId: String,
    val metadata: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class RecordingSessionRecord(
    val id: String,
    val experimentRunId: String,
    val status: String,
    val format: String,
    val sourceId: String,
    val sampleCount: Long,
    val metadata: JsonObject,
    val failureReason: String = "",
    val createdAtUnixMs: Long,
    val startedAtUnixMs: Long? = null,
    val finishedAtUnixMs: Long? = null,
    val updatedAtUnixMs: Long
)

@Serializable
data class TransitionRecordingSessionRequest(
    val status: String,
    val sampleCount: Long? = null,
    val metadata: JsonObject? = null,
    val failureReason: String = ""
)

@Serializable
data class CreateArtifactUploadRequest(
    val fileName: String,
    val contentType: String = "application/octet-stream",
    val sizeBytes: Long,
    val sha256Hex: String
)

@Serializable
data class ArtifactRecord(
    val id: String,
    val recordingSessionId: String,
    val status: String,
    val uploadMode: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val bucket: String,
    val objectKey: String,
    @Transient val multipartUploadId: String? = null,
    val partSizeBytes: Long? = null,
    val partCount: Int? = null,
    val etag: String = "",
    val failureReason: String = "",
    val createdAtUnixMs: Long,
    val verifiedAtUnixMs: Long? = null
)

@Serializable
data class ArtifactUploadInitiatedResponse(
    val artifact: ArtifactRecord,
    val uploadUrl: String? = null,
    val uploadMethod: String = "PUT",
    val requiredHeaders: Map<String, String> = emptyMap(),
    val partSizeBytes: Long? = null,
    val partCount: Int? = null,
    val uploadUrlExpiresAtUnixMs: Long? = null
)

@Serializable
data class ArtifactPartUploadUrlResponse(
    val artifactId: String,
    val partNumber: Int,
    val uploadUrl: String,
    val uploadMethod: String = "PUT",
    val requiredHeaders: Map<String, String> = emptyMap(),
    val uploadUrlExpiresAtUnixMs: Long
)

@Serializable
data class CompletedUploadPart(
    val partNumber: Int,
    val etag: String
)

@Serializable
data class CompleteArtifactUploadRequest(
    val parts: List<CompletedUploadPart> = emptyList()
)

@Serializable
data class ArtifactDownloadUrlResponse(
    val artifactId: String,
    val downloadUrl: String,
    val expiresAtUnixMs: Long
)

@Serializable
data class TelemetryAcceptedResponse(
    val status: String,
    val robotId: String,
    val sequence: Long
)
