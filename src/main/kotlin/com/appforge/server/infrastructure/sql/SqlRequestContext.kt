package com.appforge.server.infrastructure.sql

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Request-scoped SQL context.
 * Keeps user scoping in infrastructure instead of feature repositories.
 */
object SqlRequestContext {
    private val currentUserId = ThreadLocal<String?>()

    fun currentUserId(): String? = currentUserId.get()

    suspend fun <T> withUserId(userId: String, block: suspend () -> T): T {
        return withContext(SqlUserIdContext(userId)) { block() }
    }

    private class SqlUserIdContext(
        private val userId: String,
    ) : ThreadContextElement<String?>, AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<SqlUserIdContext>

        override fun updateThreadContext(context: CoroutineContext): String? {
            val previous = currentUserId.get()
            currentUserId.set(userId)
            return previous
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
            currentUserId.set(oldState)
        }
    }
}
