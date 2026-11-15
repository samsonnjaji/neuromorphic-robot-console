package com.samsonnjaji.nrc

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NRC — Neuromorphic Robotics Console"
    ) {
        App()
    }
}
