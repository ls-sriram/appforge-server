package com.appforge.server.services.earlyaccess

import com.appforge.server.api.EarlyAccessCheckRequest
import com.appforge.server.api.EarlyAccessCheckResponse
import com.appforge.server.api.EarlyAccessJoinRequest
import com.appforge.server.api.EarlyAccessJoinResponse
import com.appforge.server.services.auth.AuthResponse

interface EarlyAccessAppService {
    suspend fun check(request: EarlyAccessCheckRequest): AuthResponse<EarlyAccessCheckResponse>
    suspend fun join(request: EarlyAccessJoinRequest): AuthResponse<EarlyAccessJoinResponse>
}

class EarlyAccessAppServiceImpl(
    private val earlyAccessService: EarlyAccessService,
) : EarlyAccessAppService {
    override suspend fun check(request: EarlyAccessCheckRequest): AuthResponse<EarlyAccessCheckResponse> {
        val hasAccess = earlyAccessService.hasAccess(request.email)
        return AuthResponse.Ok(EarlyAccessCheckResponse(hasAccess = hasAccess))
    }

    override suspend fun join(request: EarlyAccessJoinRequest): AuthResponse<EarlyAccessJoinResponse> {
        if (!earlyAccessService.isEnabled()) {
            return AuthResponse.Forbidden("Early access is disabled.")
        }
        val success = earlyAccessService.joinWaitlist(request.email)
        return AuthResponse.Ok(EarlyAccessJoinResponse(success = success))
    }
}

