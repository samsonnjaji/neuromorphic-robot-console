package com.samsonnjaji.nrc.data.telemetry

import com.samsonnjaji.nrc.domain.model.RobotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only telemetry source consumed by the dashboard.
 *
 * Sources may be local simulators or remote streams. Actuation remains a separate
 * capability and is intentionally not part of this interface.
 */
interface NrcTelemetrySource {
    val telemetry: StateFlow<RobotTelemetry>
    val isConnected: StateFlow<Boolean>
    val sourceLabel: String

    fun start(scope: CoroutineScope)
    fun stop()
}
