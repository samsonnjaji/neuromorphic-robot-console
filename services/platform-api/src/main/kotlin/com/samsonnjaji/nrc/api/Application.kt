package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.experiments.ExperimentRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRepository
import com.samsonnjaji.nrc.api.model.ApiError
import com.samsonnjaji.nrc.api.model.CreateExperimentRequest
import com.samsonnjaji.nrc.api.model.HealthResponse
import com.samsonnjaji.nrc.api.model.TelemetryAcceptedResponse
import com.samsonnjaji.nrc.api.telemetry.TelemetryHub
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
