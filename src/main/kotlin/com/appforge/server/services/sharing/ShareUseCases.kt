package com.appforge.server.services.sharing

import com.appforge.server.api.sharing.CreateShareRequest
import com.appforge.server.api.sharing.SendShareEmailRequest
import com.appforge.server.api.sharing.ShareResponse
import com.appforge.server.api.sharing.ShareSummaryResponse
import com.appforge.server.infrastructure.Resource
import com.appforge.server.middleware.ForbiddenException
import com.appforge.server.routing.ApiConverters
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.templates.EmailTemplates
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.services.ShareService

interface ShareUseCases {
    suspend fun createShare(userId: String, type: String, entityId: String, request: CreateShareRequest): ShareResponse
    suspend fun listShares(userId: String, type: String, entityId: String): List<ShareSummaryResponse>
    suspend fun listOwnerShares(userId: String): List<ShareSummaryResponse>
    suspend fun revokeShare(userId: String, token: String)
    suspend fun sendShareEmail(userId: String, token: String, request: SendShareEmailRequest)
}

class ShareUseCasesImpl(
    private val shareService: ShareService,
    private val emailService: EmailService,
    private val publicBaseUrl: String,
) : ShareUseCases {
    override suspend fun createShare(
        userId: String,
        type: String,
        entityId: String,
        request: CreateShareRequest
    ): ShareResponse {
        val category = EntityCategory(type)
        return when (val res = shareService.createShare(
            ownerId = userId,
            entityId = entityId,
            entityCategory = category,
        )) {
            is Resource.Success -> ApiConverters.toShareResponse(res.data, publicBaseUrl)
            is Resource.Error -> throw IllegalStateException(res.exception.message ?: "Error")
            else -> throw IllegalStateException("Error")
        }
    }

    override suspend fun listShares(userId: String, type: String, entityId: String): List<ShareSummaryResponse> {
        val category = EntityCategory(type)
        return when (val result = shareService.listSharesForEntity(userId, category, entityId)) {
            is Resource.Success -> result.data.map { ApiConverters.toShareSummaryResponse(it, publicBaseUrl) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            else -> emptyList()
        }
    }

    override suspend fun listOwnerShares(userId: String): List<ShareSummaryResponse> {
        return when (val result = shareService.listSharesForOwner(userId)) {
            is Resource.Success -> result.data.map { ApiConverters.toShareSummaryResponse(it, publicBaseUrl) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            else -> emptyList()
        }
    }

    override suspend fun revokeShare(userId: String, token: String) {
        when (val result = shareService.revokeShare(userId, token)) {
            is Resource.Success -> return
            is Resource.Error -> {
                if (result.exception.message == "Unauthorized") {
                    throw ForbiddenException("Unauthorized")
                }
                throw IllegalStateException(result.exception.message ?: "Error")
            }
            else -> return
        }
    }

    override suspend fun sendShareEmail(userId: String, token: String, request: SendShareEmailRequest) {
        val shareRes = shareService.getShare(token)
        when (shareRes) {
            is Resource.Success -> {
                val share = shareRes.data
                if (share.ownerId != userId) {
                    throw ForbiddenException("Unauthorized")
                }
                val shareUrl = "$publicBaseUrl/web/shares/${share.token}"
                val emailContent = EmailTemplates.reviewShareInvite(
                    shareUrl = shareUrl,
                    entityCategory = share.entityCategory.value
                )
                emailService.sendEmail(
                    to = request.toEmail,
                    subject = emailContent.subject,
                    content = emailContent.html,
                    isHtml = true
                )
            }
            is Resource.Error -> throw IllegalStateException(shareRes.exception.message ?: "Error")
            else -> return
        }
    }
}
