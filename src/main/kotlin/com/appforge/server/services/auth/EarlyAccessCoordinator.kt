package com.appforge.server.services.auth

import com.appforge.server.config.ProductDetails
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.services.earlyaccess.repository.EarlyAccessRepository
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.templates.EmailTemplates

data class EarlyAccessApprovalOutcome(
    val email: String,
    val previousStatus: String?,
    val created: Boolean,
    val emailSent: Boolean
)

class EarlyAccessCoordinator(
    private val repository: EarlyAccessRepository,
    private val emailService: EmailService,
    private val runtimeOptions: RuntimeOptions
) {
    suspend fun approveAndInvite(email: String, forceSend: Boolean = false): EarlyAccessApprovalOutcome? {
        val result = repository.approveAccess(email) ?: return null
        val inviteUrl = ProductDetails.earlyAccessSignupUrl

        var emailSent = false
        if ((result.wasUpdated || forceSend) && runtimeOptions.earlyAccessEnabled) {
            val content = EmailTemplates.earlyAccessInvite(inviteUrl)
            emailService.sendEmail(
                to = result.normalizedEmail,
                subject = content.subject,
                content = content.html,
                isHtml = true
            )
            emailSent = true
        }

        return EarlyAccessApprovalOutcome(
            email = result.normalizedEmail,
            previousStatus = result.previousStatus,
            created = result.wasCreated,
            emailSent = emailSent
        )
    }
}
