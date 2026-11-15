package com.samsonnjaji.nrc.data.telemetry

import com.samsonnjaji.nrc.data.demo.DemoTelemetryEngine
import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import com.samsonnjaji.nrc.domain.transport.EnvelopeValidation
import com.samsonnjaji.nrc.domain.transport.TelemetryEnvelope
import com.samsonnjaji.nrc.domain.transport.TelemetryStreamGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * Desktop integration-test source.
 *
 * It publishes synthetic demo telemetry to the local NRC API and reads the
 * same snapshots back through the WebSocket. It validates the end-to-end
 * client/API/stream path; it is not a real robot or sensor connection.
 */
class ApiRelayTelemetrySource(
    baseUrl: String,
    private val robotId: String = "nrc-desktop-relay",
    private val demoEngine: DemoTelemetryEngine = DemoTelemetryEngine()
) : NrcTelemetrySource {
    private val httpBaseUrl = baseUrl.trimEnd('/')
    private val websocketBaseUrl = when {
        httpBaseUrl.startsWith("https://") -> "wss://" + httpBaseUrl.removePrefix("https://")
        httpBaseUrl.startsWith("http://") -> "ws://" + httpBaseUrl.removePrefix("http://")
        else -> throw IllegalArgumentException("NRC API URL must start with http:// or https://")
    }

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }
    private val sequence = AtomicLong(System.currentTimeMillis() * 1_000L)
    private val guard = TelemetryStreamGuard(robotId)

    private val _telemetry = MutableStateFlow(RobotTelemetry())
    override val telemetry: StateFlow<RobotTelemetry> = _telemetry.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val sourceLabel: String = "API RELAY · SYNTHETIC"

    private var publisherJob: Job? = null
    private var streamJob: Job? = null

    override fun start(scope: CoroutineScope) {
        if (publisherJob?.isActive == true || streamJob?.isActive == true) return
        demoEngine.start(scope)
        streamJob = scope.launch { consumeStream() }
        publisherJob = scope.launch {
            demoEngine.telemetry.collect { snapshot ->
                publishSnapshot(snapshot)
            }
        }
    }

    private suspend fun publishSnapshot(snapshot: RobotTelemetry) {
        val nextSequence = sequence.incrementAndGet()
        val payload = snapshot.copy(sequence = nextSequence)
        val envelope = TelemetryEnvelope(
            robotId = robotId,
            sequence = nextSequence,
            timestampUnixMs = System.currentTimeMillis(),
            payload = payload
        )

        try {
            val response = client.post("$httpBaseUrl/api/v1/robots/$robotId/telemetry") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(envelope))
            }
            if (response.status.value !in 200..299) {
                throw IllegalStateException("NRC API rejected telemetry: HTTP ${response.status.value}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            delay(1_000)
        }
    }

    private suspend fun consumeStream() {
        val streamUrl = "$websocketBaseUrl/api/v1/robots/$robotId/stream"
        while (currentCoroutineContext().isActive) {
            try {
                client.webSocket(urlString = streamUrl) {
                    _isConnected.value = true
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val envelope = json.decodeFromString<TelemetryEnvelope>(frame.readText())
                            if (guard.validate(envelope) == EnvelopeValidation.ACCEPTED) {
                                _telemetry.value = envelope.payload
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _isConnected.value = false
                delay(1_000)
            } finally {
                _isConnected.value = false
            }
        }
    }

    override fun stop() {
        publisherJob?.cancel()
        streamJob?.cancel()
        publisherJob = null
        streamJob = null
        demoEngine.stop()
        _isConnected.value = false
        client.close()
    }
}
