package com.samsonnjaji.nrc.api.telemetry

import com.samsonnjaji.nrc.domain.transport.TelemetryEnvelope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory live fan-out for the local MVP. It retains only the latest snapshot
 * plus a bounded buffer; it is not a recording store or durable message broker.
 */
class TelemetryHub {
    private val streams = ConcurrentHashMap<String, MutableSharedFlow<TelemetryEnvelope>>()

    private fun mutableStream(robotId: String): MutableSharedFlow<TelemetryEnvelope> =
        streams.computeIfAbsent(robotId) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

    fun stream(robotId: String): SharedFlow<TelemetryEnvelope> = mutableStream(robotId)

    suspend fun publish(envelope: TelemetryEnvelope) {
        mutableStream(envelope.robotId).emit(envelope)
    }
}
