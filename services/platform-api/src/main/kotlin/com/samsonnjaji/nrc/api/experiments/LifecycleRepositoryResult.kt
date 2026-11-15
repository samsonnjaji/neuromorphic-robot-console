package com.samsonnjaji.nrc.api.experiments

sealed interface RepositoryUpdate<out T> {
    data class Updated<T>(val value: T) : RepositoryUpdate<T>
    data object NotFound : RepositoryUpdate<Nothing>
    data object InvalidTransition : RepositoryUpdate<Nothing>
}
