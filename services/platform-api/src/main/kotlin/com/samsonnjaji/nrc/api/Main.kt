package com.samsonnjaji.nrc.api

import com.samsonnjaji.nrc.api.artifacts.ArtifactRepository
import com.samsonnjaji.nrc.api.artifacts.InMemoryArtifactRepository
import com.samsonnjaji.nrc.api.artifacts.JdbcArtifactRepository
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
import com.samsonnjaji.nrc.api.storage.S3CompatibleArtifactObjectStorage
import com.samsonnjaji.nrc.api.storage.S3ObjectStorageConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway
import java.net.URI

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

    val artifactRepository: ArtifactRepository = if (dataSource != null) {
        JdbcArtifactRepository(dataSource)
    } else {
        InMemoryArtifactRepository()
    }

    val objectStorageEndpoint = System.getenv("NRC_OBJECT_STORE_ENDPOINT")?.takeIf { it.isNotBlank() }
    val artifactObjectStorage = objectStorageEndpoint?.let { endpoint ->
        val accessKey = requireNotNull(System.getenv("NRC_OBJECT_STORE_ACCESS_KEY")) {
            "NRC_OBJECT_STORE_ACCESS_KEY is required when object storage is configured."
        }
        val secretKey = requireNotNull(System.getenv("NRC_OBJECT_STORE_SECRET_KEY")) {
            "NRC_OBJECT_STORE_SECRET_KEY is required when object storage is configured."
        }
        S3CompatibleArtifactObjectStorage(
            S3ObjectStorageConfig(
                endpoint = URI.create(endpoint),
                bucket = System.getenv("NRC_OBJECT_STORE_BUCKET")?.takeIf { it.isNotBlank() } ?: "nrc-artifacts",
                accessKey = accessKey,
                secretKey = secretKey,
                region = System.getenv("NRC_OBJECT_STORE_REGION") ?: "us-east-1",
                pathStyleAccess = System.getenv("NRC_OBJECT_STORE_PATH_STYLE")?.toBooleanStrictOrNull() ?: true,
                presignTtlSeconds = System.getenv("NRC_OBJECT_STORE_PRESIGN_TTL_SECONDS")?.toLongOrNull() ?: 900
            )
        ).also { storage ->
            storage.ensureBucket()
        }
    }

    if (artifactObjectStorage == null) {
        println("NRC_OBJECT_STORE_ENDPOINT is not configured; artifact upload/download endpoints will return 503.")
    }

    val host = System.getenv("NRC_API_HOST") ?: "127.0.0.1"
    val port = System.getenv("NRC_API_PORT")?.toIntOrNull() ?: 8080

    if (dataSource != null || artifactObjectStorage != null) {
        Runtime.getRuntime().addShutdownHook(Thread {
            artifactObjectStorage?.close()
            dataSource?.close()
        })
    }

    embeddedServer(
        Netty,
        host = host,
        port = port,
        module = {
            module(
                experimentRepository = repository,
                experimentRunRepository = experimentRunRepository,
                recordingSessionRepository = recordingSessionRepository,
                artifactRepository = artifactRepository,
                artifactObjectStorage = artifactObjectStorage
            )
        }
    ).start(wait = true)
}
