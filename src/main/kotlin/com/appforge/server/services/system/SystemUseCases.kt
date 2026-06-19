package com.appforge.server.services.system

import com.appforge.server.api.EarlyAccessApproveRequest
import com.appforge.server.api.EarlyAccessApproveResponse
import com.appforge.server.services.auth.EarlyAccessCoordinator

interface SystemUseCases {
    fun trigger(userId: String): Map<String, String>
    suspend fun approveEarlyAccess(request: EarlyAccessApproveRequest): EarlyAccessApproveResponse
}

class SystemUseCasesImpl(
    private val earlyAccessCoordinator: EarlyAccessCoordinator,
) : SystemUseCases {
    override fun trigger(userId: String): Map<String, String> {
        if (userId.isBlank()) {
            throw IllegalArgumentException("userId is required")
        }
        return mapOf("status" to "success")
    }

    override suspend fun approveEarlyAccess(request: EarlyAccessApproveRequest): EarlyAccessApproveResponse {
        val result = earlyAccessCoordinator.approveAndInvite(request.email, request.forceSend)
            ?: throw IllegalArgumentException("Email is required")
        return EarlyAccessApproveResponse(
            success = true,
            email = result.email,
            previousStatus = result.previousStatus,
            created = result.created,
            emailSent = result.emailSent
        )
    }
}
