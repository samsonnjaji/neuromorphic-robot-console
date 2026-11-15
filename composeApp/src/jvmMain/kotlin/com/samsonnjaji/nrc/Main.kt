package com.samsonnjaji.nrc

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.samsonnjaji.nrc.data.telemetry.ApiRelayTelemetrySource

fun main() {
    val apiBaseUrl = System.getenv("NRC_API_BASE_URL")?.takeIf { it.isNotBlank() }
    val robotId = System.getenv("NRC_ROBOT_ID")?.takeIf { it.isNotBlank() } ?: "nrc-desktop-relay"
    val apiSource = apiBaseUrl?.let { ApiRelayTelemetrySource(it, robotId) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "NRC — Neuromorphic Robotics Console"
        ) {
            App(apiTelemetrySource = apiSource)
        }
    }
}
