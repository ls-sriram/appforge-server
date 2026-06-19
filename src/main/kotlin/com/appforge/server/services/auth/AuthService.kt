package com.appforge.server.services.auth

import com.appforge.server.config.AppEnv
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.SessionCookieOptions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthService(
    private val firebaseAuth: FirebaseAuth,
    private val env: AppEnv,
) {
    val sessionCookieName: String = env.session.sessionCookieName
    private val sessionExpiryDays: Int = env.session.sessionExpiryDays.coerceIn(1, 14)
    val cookieSecure: Boolean = env.session.cookieSecure
    val cookieSameSite: String = env.session.cookieSameSite
    val internalSecret: String = env.runtime.internalSecret
    private val firebaseCallTimeoutMillis: Long = 5000

    private fun <T> runWithTimeout(block: () -> T): T? {
        val future = FIREBASE_EXECUTOR.submit<T> { block() }
        return try {
            future.get(firebaseCallTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            null
        }
    }

    fun sessionExpirySeconds(): Int = sessionExpiryDays * 24 * 60 * 60

    suspend fun createSessionCookie(idToken: String): String {
        val expiresInMillis = sessionExpirySeconds().toLong() * 1000 - 1L
        val options = SessionCookieOptions.builder()
            .setExpiresIn(expiresInMillis)
            .build()
        return firebaseAuth.createSessionCookie(idToken, options)
    }

    fun verifySessionCookie(sessionCookie: String): FirebaseToken? {
        return try {
            firebaseAuth.verifySessionCookie(sessionCookie, true)
        } catch (_: Exception) {
            null
        }
    }

    fun verifyIdToken(idToken: String): FirebaseToken? {
        return runWithTimeout { firebaseAuth.verifyIdToken(idToken) }
    }

    fun revokeSession(sessionCookie: String): Boolean {
        val decoded = verifySessionCookie(sessionCookie) ?: return false
        return try {
            firebaseAuth.revokeRefreshTokens(decoded.uid)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getUserEmail(userId: String): String? {
        return try {
            firebaseAuth.getUser(userId).email
        } catch (_: Exception) {
            null
        }
    }

    fun generatePasswordResetLink(email: String): String? {
        return try {
            firebaseAuth.generatePasswordResetLink(email)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instances: MutableMap<String, AuthService> = mutableMapOf()
        private val FIREBASE_EXECUTOR = Executors.newCachedThreadPool()

        fun getInstance(firebaseAuth: FirebaseAuth, env: AppEnv): AuthService =
            synchronized(this) {
                val cacheKey = env.firebase.firebaseProjectId.trim()
                instances[cacheKey] ?: AuthService(firebaseAuth, env).also { instances[cacheKey] = it }
            }
    }
}
