package com.samsonnjaji.nrc.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    // Bind to loopback by default. Set NRC_API_HOST explicitly before exposing
    // the development API to another machine or network.
    val host = System.getenv("NRC_API_HOST") ?: "127.0.0.1"
    val port = System.getenv("NRC_API_PORT")?.toIntOrNull() ?: 8080

    embeddedServer(
        Netty,
        host = host,
        port = port,
        module = { module() }
    ).start(wait = true)
}
