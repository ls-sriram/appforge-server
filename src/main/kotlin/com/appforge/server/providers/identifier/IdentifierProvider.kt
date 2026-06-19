package com.appforge.server.providers.identifier

import java.security.SecureRandom
import com.appforge.server.infrastructure.time.*
import java.util.Base64
import java.util.UUID

/**
 * Centralized ID generation for the entire platform.
 *
 * Every document ID in the system flows through here. No scattered UUID calls.
 *
 * Strategy:
 *   - newUuid()        → UUID v4 for reviews, uploads, analytics
 *   - newShareToken()  → 32-byte SecureRandom, Base64 URL-safe (unguessable)
 *   - paymentId(ts)    → epoch millis (ordered, one-per-timestamp)
 *   - BILLING_ENTITLEMENT_ID → literal "entitlement" (one-per-user singleton)
 */
object IdentifierProvider {
    private val secureRandom = SecureRandom()

    /** UUID v4 — for reviews, uploads, analytics, any globally unique ID. */
    fun newUuid(): String = UUID.randomUUID().toString()

    /** Legacy alias for backwards compatibility. */
    fun newId(): String = newUuid()

    /** 32 random bytes → Base64 URL-safe, no padding (~43 chars). Used for share tokens. */
    fun newShareToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Epoch millis as string. For payment records — ordered, one-per-timestamp. */
    fun paymentId(timestamp: AppTimestamp): String = timestamp.toEpochMilli().toString()

    /** Singleton document ID for the per-user billing entitlement. */
    const val BILLING_ENTITLEMENT_ID = "entitlement"
}
