package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.artifacts.ArtifactRepository
import com.samsonnjaji.nrc.api.artifacts.InMemoryArtifactRepository
import com.samsonnjaji.nrc.api.artifacts.artifactUploadPlan
import com.samsonnjaji.nrc.api.artifacts.safeArtifactFileName
import com.samsonnjaji.nrc.api.artifacts.validateArtifactUploadRequest
import com.samsonnjaji.nrc.api.experiments.ExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryRecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.experiments.ExperimentRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRepository
import com.samsonnjaji.nrc.api.model.ApiError
import com.samsonnjaji.nrc.api.model.ArtifactDownloadUrlResponse
import com.samsonnjaji.nrc.api.model.ArtifactPartUploadUrlResponse
import com.samsonnjaji.nrc.api.model.ArtifactRecord
import com.samsonnjaji.nrc.api.model.ArtifactUploadInitiatedResponse
import com.samsonnjaji.nrc.api.model.CompleteArtifactUploadRequest
import com.samsonnjaji.nrc.api.model.CreateArtifactUploadRequest
import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.CreateExperimentRunRequest
import com.samsonnjaji.nrc.api.model.CreateRecordingSessionRequest
import com.samsonnjaji.nrc.api.model.HealthResponse
import com.samsonnjaji.nrc.api.model.TelemetryAcceptedResponse
import com.samsonnjaji.nrc.api.model.TransitionExperimentRunRequest
import com.samsonnjaji.nrc.api.model.TransitionRecordingSessionRequest
import com.samsonnjaji.nrc.api.telemetry.TelemetryHub
import com.samsonnjaji.nrc.api.storage.ArtifactObjectStorage
import com.samsonnjaji.nrc.api.storage.CompletedObjectPart
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import com.samsonnjaji.nrc.domain.robustness.ExperimentSourceType
import com.samsonnjaji.nrc.domain.transport.EnvelopeValidation
import com.samsonnjaji.nrc.domain.transport.TelemetryEnvelope
import com.samsonnjaji.nrc.domain.transport.TelemetryStreamGuard
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val API_VERSION = "v1"

val NrcJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
}

fun Application.module(
    experimentRepository: ExperimentRepository = InMemoryExperimentRepository(),
    experimentRunRepository: ExperimentRunRepository = InMemoryExperimentRunRepository(),
    recordingSessionRepository: RecordingSessionRepository = InMemoryRecordingSessionRepository(),
    artifactRepository: ArtifactRepository = InMemoryArtifactRepository(),
    artifactObjectStorage: ArtifactObjectStorage? = null,
    telemetryHub: TelemetryHub = TelemetryHub(),
    jsonFormat: Json = NrcJson
) {
    install(ContentNegotiation) {
        json(jsonFormat)
    }
    install(WebSockets)

    val streamGuards = ConcurrentHashMap<String, TelemetryStreamGuard>()
    val streamLocks = ConcurrentHashMap<String, Mutex>()

    routing {
        route("/api/$API_VERSION") {
            get("/health") {
                call.respond(
                    HealthResponse(
                        status = "ok",
                        service = "nrc-platform-api",
                        apiVersion = API_VERSION
                    )
                )
            }

            route("/experiments") {
                get {
                    call.respond(experimentRepository.list())
                }

                post {
                    val request = call.receive<CreateExperimentRequest>()
                    val validationError = validateExperimentRequest(request)
                    if (validationError != null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("invalid_experiment", validationError)
                        )
                        return@post
                    }

                    val created = experimentRepository.create(request)
                    call.respond(HttpStatusCode.Created, created)
                }

                get("/{experimentId}") {
                    val experimentId = call.parameters["experimentId"]
                    if (experimentId.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("missing_experiment_id", "An experiment ID is required.")
                        )
                        return@get
                    }

                    val record = experimentRepository.find(experimentId)
                    if (record == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiError("experiment_not_found", "No experiment exists with that ID.")
                        )
                    } else {
                        call.respond(record)
                    }
                }
            }

            route("/experiments/{experimentId}/runs") {
                get {
                    val experimentId = call.parameters["experimentId"].orEmpty()
                    if (experimentRepository.find(experimentId) == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("experiment_not_found", "No experiment exists with that ID."))
                        return@get
                    }
                    call.respond(experimentRunRepository.listByExperiment(experimentId))
                }

                post {
                    val experimentId = call.parameters["experimentId"].orEmpty()
                    if (experimentRepository.find(experimentId) == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("experiment_not_found", "No experiment exists with that ID."))
                        return@post
                    }

                    val request = call.receive<CreateExperimentRunRequest>()
                    val validationError = validateExperimentRunRequest(request)
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_experiment_run", validationError))
                        return@post
                    }

                    call.respond(
                        HttpStatusCode.Created,
                        experimentRunRepository.create(experimentId, request)
                    )
                }
            }

            route("/runs/{runId}") {
                get {
                    val runId = call.parameters["runId"].orEmpty()
                    if (!isUuid(runId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_run_id", "Run ID must be a UUID."))
                        return@get
                    }
                    val record = experimentRunRepository.find(runId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("experiment_run_not_found", "No experiment run exists with that ID."))
                    } else {
                        call.respond(record)
                    }
                }

                post("/transition") {
                    val runId = call.parameters["runId"].orEmpty()
                    if (!isUuid(runId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_run_id", "Run ID must be a UUID."))
                        return@post
                    }
                    val request = call.receive<TransitionExperimentRunRequest>()
                    val validationError = validateExperimentRunTransition(request)
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_run_transition", validationError))
                        return@post
                    }

                    when (val result = experimentRunRepository.transition(runId, request)) {
                        is RepositoryUpdate.Updated -> call.respond(result.value)
                        RepositoryUpdate.NotFound -> call.respond(
                            HttpStatusCode.NotFound,
                            ApiError("experiment_run_not_found", "No experiment run exists with that ID.")
                        )
                        RepositoryUpdate.InvalidTransition -> call.respond(
                            HttpStatusCode.Conflict,
                            ApiError("invalid_run_transition", "The requested experiment run state transition is not allowed.")
                        )
                    }
                }

                get("/recordings") {
                    val runId = call.parameters["runId"].orEmpty()
                    if (!isUuid(runId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_run_id", "Run ID must be a UUID."))
                        return@get
                    }
                    if (experimentRunRepository.find(runId) == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("experiment_run_not_found", "No experiment run exists with that ID."))
                        return@get
                    }
                    call.respond(recordingSessionRepository.listByRun(runId))
                }

                post("/recordings") {
                    val runId = call.parameters["runId"].orEmpty()
                    if (!isUuid(runId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_run_id", "Run ID must be a UUID."))
                        return@post
                    }
                    if (experimentRunRepository.find(runId) == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("experiment_run_not_found", "No experiment run exists with that ID."))
                        return@post
                    }

                    val request = call.receive<CreateRecordingSessionRequest>()
                    val validationError = validateRecordingSessionRequest(request)
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_session", validationError))
                        return@post
                    }

                    call.respond(
                        HttpStatusCode.Created,
                        recordingSessionRepository.create(runId, request)
                    )
                }
            }

            route("/recordings/{recordingId}") {
                get {
                    val recordingId = call.parameters["recordingId"].orEmpty()
                    if (!isUuid(recordingId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_id", "Recording ID must be a UUID."))
                        return@get
                    }
                    val record = recordingSessionRepository.find(recordingId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("recording_session_not_found", "No recording session exists with that ID."))
                    } else {
                        call.respond(record)
                    }
                }

                post("/transition") {
                    val recordingId = call.parameters["recordingId"].orEmpty()
                    if (!isUuid(recordingId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_id", "Recording ID must be a UUID."))
                        return@post
                    }
                    val request = call.receive<TransitionRecordingSessionRequest>()
                    val validationError = validateRecordingSessionTransition(request)
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_transition", validationError))
                        return@post
                    }

                    when (val result = recordingSessionRepository.transition(recordingId, request)) {
                        is RepositoryUpdate.Updated -> call.respond(result.value)
                        RepositoryUpdate.NotFound -> call.respond(
                            HttpStatusCode.NotFound,
                            ApiError("recording_session_not_found", "No recording session exists with that ID.")
                        )
                        RepositoryUpdate.InvalidTransition -> call.respond(
                            HttpStatusCode.Conflict,
                            ApiError("invalid_recording_transition", "The requested recording session state transition is not allowed.")
                        )
                    }
                }
            }


            route("/recordings/{recordingId}/artifacts") {
                get {
                    val recordingId = call.parameters["recordingId"].orEmpty()
                    if (!isUuid(recordingId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_id", "Recording ID must be a UUID."))
                        return@get
                    }
                    if (recordingSessionRepository.find(recordingId) == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("recording_session_not_found", "No recording session exists with that ID."))
                        return@get
                    }
                    call.respond(artifactRepository.listByRecordingSession(recordingId))
                }

                post {
                    val recordingId = call.parameters["recordingId"].orEmpty()
                    if (!isUuid(recordingId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_recording_id", "Recording ID must be a UUID."))
                        return@post
                    }
                    val recording = recordingSessionRepository.find(recordingId)
                    if (recording == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("recording_session_not_found", "No recording session exists with that ID."))
                        return@post
                    }
                    if (recording.status != "FINALIZED") {
                        call.respond(HttpStatusCode.Conflict, ApiError("recording_not_finalized", "Artifacts can only be attached to a FINALIZED recording session."))
                        return@post
                    }
                    val storage = artifactObjectStorage
                    if (storage == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiError("object_storage_unavailable", "S3-compatible object storage is not configured."))
                        return@post
                    }

                    val request = call.receive<CreateArtifactUploadRequest>()
                    val validationError = validateArtifactUploadRequest(request)
                    if (validationError != null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact", validationError))
                        return@post
                    }

                    val plan = artifactUploadPlan(request.sizeBytes)
                    val artifactId = UUID.randomUUID().toString()
                    val fileName = safeArtifactFileName(request.fileName)
                    val objectKey = "recordings/$recordingId/$artifactId/$fileName"
                    val multipartUploadId = if (plan.mode == "MULTIPART") {
                        storage.initiateMultipartUpload(objectKey, request.contentType.trim())
                    } else {
                        null
                    }
                    val signedUpload = if (plan.mode == "SINGLE_PUT") {
                        storage.createSingleUpload(objectKey, request.contentType.trim(), request.sizeBytes)
                    } else {
                        null
                    }
                    val record = ArtifactRecord(
                        id = artifactId,
                        recordingSessionId = recordingId,
                        status = "PENDING_UPLOAD",
                        uploadMode = plan.mode,
                        fileName = fileName,
                        contentType = request.contentType.trim(),
                        sizeBytes = request.sizeBytes,
                        sha256Hex = request.sha256Hex.lowercase(),
                        bucket = storage.bucket,
                        objectKey = objectKey,
                        multipartUploadId = multipartUploadId,
                        partSizeBytes = plan.partSizeBytes,
                        partCount = plan.partCount,
                        createdAtUnixMs = System.currentTimeMillis()
                    )

                    try {
                        artifactRepository.create(record)
                    } catch (failure: Throwable) {
                        if (multipartUploadId != null) {
                            runCatching { storage.abortMultipartUpload(objectKey, multipartUploadId) }
                        }
                        throw failure
                    }

                    call.respond(
                        HttpStatusCode.Created,
                        ArtifactUploadInitiatedResponse(
                            artifact = record,
                            uploadUrl = signedUpload?.url,
                            requiredHeaders = signedUpload?.requiredHeaders.orEmpty(),
                            partSizeBytes = plan.partSizeBytes,
                            partCount = plan.partCount,
                            uploadUrlExpiresAtUnixMs = signedUpload?.expiresAtUnixMs
                        )
                    )
                }
            }

            route("/artifacts/{artifactId}") {
                get {
                    val artifactId = call.parameters["artifactId"].orEmpty()
                    if (!isUuid(artifactId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact_id", "Artifact ID must be a UUID."))
                        return@get
                    }
                    val record = artifactRepository.find(artifactId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                    } else {
                        call.respond(record)
                    }
                }

                post("/upload-url") {
                    val artifactId = call.parameters["artifactId"].orEmpty()
                    if (!isUuid(artifactId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact_id", "Artifact ID must be a UUID."))
                        return@post
                    }
                    val record = artifactRepository.find(artifactId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                        return@post
                    }
                    if (record.status != "PENDING_UPLOAD" || record.uploadMode != "SINGLE_PUT") {
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_upload_not_available", "A single-part upload URL is not available for this artifact."))
                        return@post
                    }
                    val storage = artifactObjectStorage
                    if (storage == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiError("object_storage_unavailable", "S3-compatible object storage is not configured."))
                        return@post
                    }
                    val signed = storage.createSingleUpload(record.objectKey, record.contentType, record.sizeBytes)
                    call.respond(
                        ArtifactUploadInitiatedResponse(
                            artifact = record,
                            uploadUrl = signed.url,
                            requiredHeaders = signed.requiredHeaders,
                            uploadUrlExpiresAtUnixMs = signed.expiresAtUnixMs
                        )
                    )
                }

                post("/parts/{partNumber}/upload-url") {
                    val artifactId = call.parameters["artifactId"].orEmpty()
                    if (!isUuid(artifactId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact_id", "Artifact ID must be a UUID."))
                        return@post
                    }
                    val partNumber = call.parameters["partNumber"]?.toIntOrNull()
                    if (partNumber == null || partNumber < 1) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_part_number", "Part number must be a positive integer."))
                        return@post
                    }
                    val record = artifactRepository.find(artifactId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                        return@post
                    }
                    if (record.status != "PENDING_UPLOAD" || record.uploadMode != "MULTIPART") {
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_upload_not_available", "Multipart upload is not active for this artifact."))
                        return@post
                    }
                    if (partNumber > (record.partCount ?: 0)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_part_number", "Part number exceeds the expected part count."))
                        return@post
                    }
                    val storage = artifactObjectStorage
                    if (storage == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiError("object_storage_unavailable", "S3-compatible object storage is not configured."))
                        return@post
                    }
                    val uploadId = record.multipartUploadId
                    if (uploadId.isNullOrBlank()) {
                        call.respond(HttpStatusCode.Conflict, ApiError("multipart_upload_missing", "Multipart upload state is unavailable."))
                        return@post
                    }
                    val signed = storage.createPartUpload(record.objectKey, uploadId, partNumber)
                    call.respond(
                        ArtifactPartUploadUrlResponse(
                            artifactId = record.id,
                            partNumber = partNumber,
                            uploadUrl = signed.url,
                            requiredHeaders = signed.requiredHeaders,
                            uploadUrlExpiresAtUnixMs = signed.expiresAtUnixMs
                        )
                    )
                }

                post("/complete") {
                    val artifactId = call.parameters["artifactId"].orEmpty()
                    if (!isUuid(artifactId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact_id", "Artifact ID must be a UUID."))
                        return@post
                    }
                    val record = artifactRepository.find(artifactId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                        return@post
                    }
                    if (record.status != "PENDING_UPLOAD") {
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_not_pending", "Only pending artifacts can be completed."))
                        return@post
                    }
                    val storage = artifactObjectStorage
                    if (storage == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiError("object_storage_unavailable", "S3-compatible object storage is not configured."))
                        return@post
                    }
                    val request = call.receive<CompleteArtifactUploadRequest>()

                    if (record.uploadMode == "MULTIPART") {
                        val expectedPartCount = record.partCount ?: 0
                        val orderedParts = request.parts.sortedBy { it.partNumber }
                        if (orderedParts.size != expectedPartCount ||
                            orderedParts.map { it.partNumber } != (1..expectedPartCount).toList() ||
                            orderedParts.any { it.etag.isBlank() }
                        ) {
                            call.respond(HttpStatusCode.BadRequest, ApiError("invalid_multipart_parts", "Provide exactly one non-empty ETag for every expected part number."))
                            return@post
                        }
                        val uploadId = record.multipartUploadId
                        if (uploadId.isNullOrBlank()) {
                            call.respond(HttpStatusCode.Conflict, ApiError("multipart_upload_missing", "Multipart upload state is unavailable."))
                            return@post
                        }
                        try {
                            storage.completeMultipartUpload(
                                record.objectKey,
                                uploadId,
                                orderedParts.map { CompletedObjectPart(it.partNumber, it.etag) }
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            call.respond(HttpStatusCode.Conflict, ApiError("multipart_completion_failed", "Object storage could not complete the multipart upload. Check the part ETags and retry."))
                            return@post
                        }
                    } else if (request.parts.isNotEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("unexpected_multipart_parts", "Single-part uploads must not include multipart ETags."))
                        return@post
                    }

                    val verification = storage.verifyObject(record.objectKey)
                    if (verification == null) {
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_not_uploaded", "The uploaded object was not found. Complete the upload before finalizing it."))
                        return@post
                    }
                    if (verification.sizeBytes != record.sizeBytes) {
                        artifactRepository.updateStatus(
                            id = record.id,
                            status = "FAILED",
                            etag = verification.etag,
                            failureReason = "Uploaded size does not match the declared size."
                        )
                        bestEffortDelete(storage, record.objectKey)
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_size_mismatch", "Uploaded object size does not match the declared size."))
                        return@post
                    }
                    if (!verification.sha256Hex.equals(record.sha256Hex, ignoreCase = true)) {
                        artifactRepository.updateStatus(
                            id = record.id,
                            status = "FAILED",
                            etag = verification.etag,
                            failureReason = "SHA-256 checksum verification failed."
                        )
                        bestEffortDelete(storage, record.objectKey)
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_checksum_mismatch", "Uploaded object SHA-256 does not match the declared checksum."))
                        return@post
                    }

                    when (val updated = artifactRepository.updateStatus(
                        id = record.id,
                        status = "VERIFIED",
                        etag = verification.etag,
                        verifiedAtUnixMs = System.currentTimeMillis()
                    )) {
                        is RepositoryUpdate.Updated -> call.respond(updated.value)
                        RepositoryUpdate.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                        RepositoryUpdate.InvalidTransition -> call.respond(HttpStatusCode.Conflict, ApiError("artifact_not_pending", "Artifact is no longer pending verification."))
                    }
                }

                post("/download-url") {
                    val artifactId = call.parameters["artifactId"].orEmpty()
                    if (!isUuid(artifactId)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("invalid_artifact_id", "Artifact ID must be a UUID."))
                        return@post
                    }
                    val record = artifactRepository.find(artifactId)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("artifact_not_found", "No artifact exists with that ID."))
                        return@post
                    }
                    if (record.status != "VERIFIED") {
                        call.respond(HttpStatusCode.Conflict, ApiError("artifact_not_verified", "Only checksum-verified artifacts can be downloaded."))
                        return@post
                    }
                    val storage = artifactObjectStorage
                    if (storage == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiError("object_storage_unavailable", "S3-compatible object storage is not configured."))
                        return@post
                    }
                    val signed = storage.createDownloadUrl(record.objectKey)
                    call.respond(
                        ArtifactDownloadUrlResponse(
                            artifactId = record.id,
                            downloadUrl = signed.url,
                            expiresAtUnixMs = signed.expiresAtUnixMs
                        )
                    )
                }
            }

            post("/robots/{robotId}/telemetry") {
                val robotId = call.parameters["robotId"]
                if (robotId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("missing_robot_id", "A robot ID is required.")
                    )
                    return@post
                }

                val envelope = call.receive<TelemetryEnvelope>()
                if (envelope.robotId != robotId) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            "robot_id_mismatch",
                            "The route robot ID must match the telemetry envelope robot ID."
                        )
                    )
                    return@post
                }

                val guard = streamGuards.computeIfAbsent(robotId) {
                    TelemetryStreamGuard(robotId)
                }
                val lock = streamLocks.computeIfAbsent(robotId) { Mutex() }
                val validation = lock.withLock { guard.validate(envelope) }

                if (validation != EnvelopeValidation.ACCEPTED) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            code = "invalid_telemetry",
                            message = "Telemetry rejected: ${validation.name.lowercase()}."
                        )
                    )
                    return@post
                }

                telemetryHub.publish(envelope)
                call.respond(
                    HttpStatusCode.Accepted,
                    TelemetryAcceptedResponse(
                        status = "accepted",
                        robotId = envelope.robotId,
                        sequence = envelope.sequence
                    )
                )
            }

            webSocket("/robots/{robotId}/stream") {
                val robotId = call.parameters["robotId"]
                if (robotId.isNullOrBlank()) return@webSocket

                telemetryHub.stream(robotId).collect { envelope ->
                    send(Frame.Text(jsonFormat.encodeToString(envelope)))
                }
            }
        }
    }
}

private fun validateExperimentRequest(request: CreateExperimentRequest): String? {
    if (request.name.isBlank()) return "Experiment name is required."
    if (request.sourceId.isBlank()) return "Source ID is required."
    if (request.modelIdentifier.isBlank()) return "Model identifier is required."
    if (request.modelVersion.isBlank()) return "Model version is required."
    if (EnvironmentScenario.values().none { it.name == request.scenario }) {
        return "Unsupported environment scenario."
    }
    if (ExperimentSourceType.values().none { it.name == request.sourceType }) {
        return "Unsupported experiment source type."
    }
    return null
}

private fun validateExperimentRunRequest(request: CreateExperimentRunRequest): String? {
    if (request.sourceRevision.length > 128) return "Source revision must be at most 128 characters."
    return null
}

private fun validateExperimentRunTransition(request: TransitionExperimentRunRequest): String? {
    if (request.status !in setOf("RUNNING", "COMPLETED", "FAILED", "CANCELLED")) {
        return "Unsupported experiment run status."
    }
    if (request.status == "FAILED" && request.failureReason.isBlank()) {
        return "A failure reason is required when marking a run as FAILED."
    }
    return null
}

private fun validateRecordingSessionRequest(request: CreateRecordingSessionRequest): String? {
    if (request.format !in setOf("MCAP", "ROSBAG2", "NRC_JSONL", "OTHER")) {
        return "Unsupported recording format."
    }
    if (request.sourceId.isBlank()) return "Recording source ID is required."
    if (request.sourceId.length > 255) return "Recording source ID must be at most 255 characters."
    return null
}

private fun validateRecordingSessionTransition(request: TransitionRecordingSessionRequest): String? {
    if (request.status !in setOf("RECORDING", "FINALIZED", "FAILED", "CANCELLED")) {
        return "Unsupported recording session status."
    }
    if (request.sampleCount != null && request.sampleCount < 0) {
        return "Sample count cannot be negative."
    }
    if (request.status == "FAILED" && request.failureReason.isBlank()) {
        return "A failure reason is required when marking a recording session as FAILED."
    }
    return null
}

private fun isUuid(value: String): Boolean = runCatching {
    java.util.UUID.fromString(value)
}.isSuccess

private suspend fun bestEffortDelete(storage: ArtifactObjectStorage, objectKey: String) {
    try { storage.deleteObject(objectKey) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* orphan cleanup is handled by storage maintenance */ }
}
