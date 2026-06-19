package com.appforge.server.infrastructure

/**
 * Result wrapper for database operations.
 *
 * ## Current Usage
 * - `Success<T>` — returned by all implementations (SQL, InMemory)
 * - `Error` — returned on any failure
 * - `Loading` — **never returned** by any current implementation. Reserved for
 *   future streaming query support. Treat as unreachable in pattern matches.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val exception: Exception) : Resource<Nothing>()

    /**
     * This state is never returned by current implementations.
     * If encountered in a `when` branch, treat it as an error.
     */
    object Loading : Resource<Nothing>()
}
