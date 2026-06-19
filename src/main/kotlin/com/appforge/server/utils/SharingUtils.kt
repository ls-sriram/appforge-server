package com.appforge.server.utils

import com.appforge.server.services.reviews.models.EntityCategory
import java.security.SecureRandom
import java.util.Base64

object SharingUtils {
    const val DEFAULT_SHARE_EXPIRY_DAYS: Long = 21

    private val secureRandom = SecureRandom()

    fun generateToken(bytes: Int = 32): String {
        val buffer = ByteArray(bytes)
        secureRandom.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    fun hashEmail(email: String): String = Hashing.sha256Hex(email.lowercase().trim())

    fun shareEntityKey(category: EntityCategory, entityId: String): String {
        val safeEntityId = entityId.replace("/", "_")
        return "${category.value}:$safeEntityId"
    }
}
