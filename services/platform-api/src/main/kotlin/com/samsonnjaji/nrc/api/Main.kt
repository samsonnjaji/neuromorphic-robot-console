package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.database.createNrcDataSource
import com.samsonnjaji.nrc.api.experiments.ExperimentRepository
import com.samsonnjaji.nrc.api.experiments.ExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.InMemoryRecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.JdbcExperimentRepository
import com.samsonnjaji.nrc.api.experiments.JdbcExperimentRunRepository
import com.samsonnjaji.nrc.api.experiments.JdbcRecordingSessionRepository
import com.samsonnjaji.nrc.api.experiments.RecordingSessionRepository
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway

fun main() {
    val databaseUrl = System.getenv("NRC_DATABASE_URL")?.takeIf { it.isNotBlank() }

    val dataSource = databaseUrl?.let { url ->
        val username = System.getenv("NRC_DATABASE_USER") ?: "nrc"
        val password = requireNotNull(System.getenv("NRC_DATABASE_PASSWORD")) {
            "NRC_DATABASE_PASSWORD is required when NRC_DATABASE_URL is configured."
        }
        createNrcDataSource(url, username, password).also { source ->
            Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }

    val repository: ExperimentRepository = if (dataSource != null) {
        JdbcExperimentRepository(dataSource)
    } else {
        println(
            "NRC_DATABASE_URL is not configured; using in-memory experiment storage. " +
                "Records will be lost when the process exits."
        )
        InMemoryExperimentRepository()
    }

    val experimentRunRepository: ExperimentRunRepository = if (dataSource != null) {
        JdbcExperimentRunRepository(dataSource)
    } else {
        InMemoryExperimentRunRepository()
    }

    val recordingSessionRepository: RecordingSessionRepository = if (dataSource != null) {
        JdbcRecordingSessionRepository(dataSource)
    } else {
        InMemoryRecordingSessionRepository()
    }

    val host = System.getenv("NRC_API_HOST") ?: "127.0.0.1"
    val port = System.getenv("NRC_API_PORT")?.toIntOrNull() ?: 8080

    if (dataSource != null) {
        Runtime.getRuntime().addShutdownHook(Thread { dataSource.close() })
    }

    embeddedServer(
        Netty,
        host = host,
        port = port,
        module = {
            module(
                experimentRepository = repository,
                experimentRunRepository = experimentRunRepository,
                recordingSessionRepository = recordingSessionRepository
            )
        }
    ).start(wait = true)
}
