package com.appforge.server.services.earlyaccess

import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.services.earlyaccess.repository.EarlyAccessRepository

sealed class EarlyAccessDecision {
    data object Allowed : EarlyAccessDecision()
    data class Blocked(val message: String) : EarlyAccessDecision()
}

interface EarlyAccessService {
    fun isEnabled(): Boolean
    suspend fun enforceLoginAccess(email: String?): EarlyAccessDecision
    suspend fun autoApproveWhenOpen(email: String?)
    suspend fun hasAccess(email: String): Boolean
    suspend fun joinWaitlist(email: String): Boolean
}

class EarlyAccessServiceImpl(
    private val repository: EarlyAccessRepository,
    private val runtimeOptions: RuntimeOptions,
) : EarlyAccessService {
    override fun isEnabled(): Boolean = runtimeOptions.earlyAccessEnabled

    override suspend fun enforceLoginAccess(email: String?): EarlyAccessDecision {
        val normalizedEmail = email?.trim()
        if (normalizedEmail.isNullOrBlank()) {
            return EarlyAccessDecision.Blocked("Authenticated identity is missing email.")
        }
        if (!isEnabled()) {
            autoApproveWhenOpen(normalizedEmail)
            return EarlyAccessDecision.Allowed
        }
        if (repository.isEmailApproved(normalizedEmail)) {
            return EarlyAccessDecision.Allowed
        }
        repository.joinWaitlist(normalizedEmail)
        return EarlyAccessDecision.Blocked(
            "Early access required. You have been added to our waitlist, and we'll notify you at $normalizedEmail as soon as a seat opens."
        )
    }

    override suspend fun autoApproveWhenOpen(email: String?) {
        val normalizedEmail = email?.trim()
        if (isEnabled() || normalizedEmail.isNullOrBlank()) return
        repository.approveAccess(normalizedEmail)
    }

    override suspend fun hasAccess(email: String): Boolean {
        return if (isEnabled()) repository.isEmailApproved(email) else true
    }

    override suspend fun joinWaitlist(email: String): Boolean {
        return repository.joinWaitlist(email)
    }
}

