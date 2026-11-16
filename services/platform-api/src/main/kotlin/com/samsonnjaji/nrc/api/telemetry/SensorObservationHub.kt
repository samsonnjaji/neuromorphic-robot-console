package com.samsonnjaji.nrc.api.telemetry

import com.samsonnjaji.nrc.domain.transport.SensorObservationEnvelope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded in-memory fan-out for compact sensor observations.
 * It is a live view only; raw and high-bandwidth data belongs in MCAP/object storage.
 */
class SensorObservationHub {
    private val streams =
        ConcurrentHashMap<String, MutableSharedFlow<SensorObservationEnvelope>>()

    private fun mutableStream(robotId: String): MutableSharedFlow<SensorObservationEnvelope> =
        streams.computeIfAbsent(robotId) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

    fun stream(robotId: String): SharedFlow<SensorObservationEnvelope> =
        mutableStream(robotId)

    suspend fun publish(envelope: SensorObservationEnvelope) {
        mutableStream(envelope.robotId).emit(envelope)
    }
}
