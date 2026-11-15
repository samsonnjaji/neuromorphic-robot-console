package com.samsonnjaji.nrc.api.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val apiVersion: String
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

@Serializable
data class CreateExperimentRequest(
    val name: String,
    val description: String = "",
    val scenario: String,
    val sourceType: String,
    val sourceId: String,
    val modelIdentifier: String,
    val modelVersion: String
)

@Serializable
data class ExperimentRecord(
    val id: String,
    val name: String,
    val description: String,
    val scenario: String,
    val sourceType: String,
    val sourceId: String,
    val modelIdentifier: String,
    val modelVersion: String,
    val createdAtUnixMs: Long
)

@Serializable
data class TelemetryAcceptedResponse(
    val status: String,
    val robotId: String,
    val sequence: Long
)
