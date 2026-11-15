package com.samsonnjaji.nrc.data.transport

import com.samsonnjaji.nrc.domain.transport.TelemetryEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class TelemetryConnectionConfig(
    val endpoint: String,
    val robotId: String
)

enum class TelemetryConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

/**
 * Transport boundary for live telemetry sources.
 *
 * Implementations own networking and wire-format decoding. The shared domain and UI consume
 * validated, typed envelopes and never import ROS 2 middleware.
 */
interface TelemetryTransport {
    val connectionState: StateFlow<TelemetryConnectionState>
    val snapshots: Flow<TelemetryEnvelope>

    suspend fun connect(config: TelemetryConnectionConfig)
    suspend fun disconnect()
}
