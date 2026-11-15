package com.samsonnjaji.nrc.api.experiments

private val experimentRunTransitions = mapOf(
    "CREATED" to setOf("RUNNING", "CANCELLED"),
    "RUNNING" to setOf("COMPLETED", "FAILED", "CANCELLED")
)

private val recordingSessionTransitions = mapOf(
    "CREATED" to setOf("RECORDING", "CANCELLED"),
    "RECORDING" to setOf("FINALIZED", "FAILED", "CANCELLED")
)

fun canTransitionExperimentRun(from: String, to: String): Boolean =
    to in experimentRunTransitions[from].orEmpty()

fun canTransitionRecordingSession(from: String, to: String): Boolean =
    to in recordingSessionTransitions[from].orEmpty()
