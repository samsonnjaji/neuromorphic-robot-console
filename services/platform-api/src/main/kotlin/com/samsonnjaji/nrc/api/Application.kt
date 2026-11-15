package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.experiments.ExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryRecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RepositoryUpdate
import com.samsonnjaji.nrc.api.experiments.ExperimentRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRepository
import com.samsonnjaji.nrc.api.model.ApiError
import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.CreateExperimentRunRequest
import com.samsonnjaji.nrc.api.model.CreateRecordingSessionRequest
import com.samsonnjaji.nrc.api.model.HealthResponse
import com.samsonnjaji.nrc.api.model.TelemetryAcceptedResponse
import com.samsonnjaji.nrc.api.model.TransitionExperimentRunRequest
import com.samsonnjaji.nrc.api.model.TransitionRecordingSessionRequest
import com.samsonnjaji.nrc.api.telemetry.TelemetryHub
import com.samsonnjaji.nrc.domain.robustness.EnvironmentScenario
import com.samsonnjaji.nrc.domain.robustness.ExperimentSourceType
import com.samsonnjaji.nrc.domain.transport.EnvelopeValidation
import com.samsonnjaji.nrc.domain.transport.TelemetryEnvelope
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
