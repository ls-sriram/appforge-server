package com.appforge.server.services.billing

import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.templates.EmailTemplates
import com.appforge.server.infrastructure.time.*

class BillingEmailCoordinator(
        private val repository: BillingRepositoryApi,
        private val authService: AuthService,
        private val emailService: EmailService,
) {
        /**
         * Payment confirmation policy:
         * - Send once per external reference id.
         * - Skip if already recorded as sent for that payment.
         */
        suspend fun sendPaymentConfirmationIfNeeded(
                userId: String,
                externalReferenceId: String?,
                amountCents: Long,
                currency: String,
                planName: String,
                now: AppTimestamp,
        ): AppTimestamp? {
                val existingEmailSentAt =
                        externalReferenceId
                                ?.let { repository.getPayment(userId, it) }
                                ?.emailSentAt
                if (existingEmailSentAt != null) return existingEmailSentAt

                val userEmail = authService.getUserEmail(userId)
                if (userEmail == null) return null

                return try {
                        emailService.sendPaymentConfirmation(
                                to = userEmail,
                                amount = String.format("%.2f", amountCents / 100.0),
                                currency = currency.uppercase(),
                                planName = planName,
                                transactionId =
                                        externalReferenceId
                                                ?: "manual-${now.toEpochMilli()}"
                        )
                        now
                } catch (e: Exception) {
                        null
                }
        }

        /**
         * Plan-change policy:
         * - Send only when plan value changes.
         * - Skip for first-time entitlement creation (no previous plan).
         * - Skip trial-source internal grants.
         */
        suspend fun sendPlanChangedIfNeeded(
                userId: String,
                previousPlan: Plan?,
                newPlan: Plan,
                source: BillingSource,
        ): Boolean {
                if (previousPlan == null || previousPlan == newPlan) return false
                if (source == BillingSource.TRIAL) return false

                val userEmail = authService.getUserEmail(userId) ?: return false
                val content = EmailTemplates.planChanged(
                        previousPlanName = previousPlan.displayName,
                        newPlanName = newPlan.displayName,
                )

                return try {
                        emailService.sendEmail(
                                to = userEmail,
                                subject = content.subject,
                                content = content.html,
                                isHtml = true,
                        )
                        true
                } catch (e: Exception) {
                        false
                }
        }
}
