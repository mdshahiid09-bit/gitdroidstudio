package com.gitdroidstudio.shahid.app.model

sealed class BuildStatus {
    object Idle : BuildStatus()
    object Queued : BuildStatus()
    object InProgress : BuildStatus()
    object Success : BuildStatus()
    data class Failure(val message: String = "") : BuildStatus()

    val name: String
        get() = when (this) {
            is Idle -> "Idle"
            is Queued -> "Queued"
            is InProgress -> "InProgress"
            is Success -> "Success"
            is Failure -> "Failure"
        }
}