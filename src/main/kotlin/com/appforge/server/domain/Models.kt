/**
 * ─────────────────────────────────────────────────────────────────
 * PLATFORM RECORDS — Strongly-typed domain models for all
 * platform records / database documents.
 *
 * Every document in the system maps to one of these types.
 * The Database layer (SQL JSONB) stores them as raw maps,
 * but the service layer always works with these typed models.
 * ─────────────────────────────────────────────────────────────────
 */

package com.appforge.server.domain

// ─── Domain Models ──────────────────────────────────────────────────────

/** User-submitted review (external or AI-generated). */
data class Review(
    val id: String,
    val entityId: String,
    val entityCategory: String,
    val entityType: String,
    val authorRole: String,
    val authorId: String,
    val authorName: String,
    val authorEmail: String,
    val content: ReviewContent,
    val createdAtTimestamp: Long,
)

data class ReviewContent(
    val rating: Int?,
    val summary: String?,
    val pros: List<String>?,
    val cons: List<String>?,
    val fullText: String?,
)

/** Billing entitlement — what plan a user is on and what features they have. */
data class BillingEntitlement(
    val customerId: String,
    val plan: String,               // "free", "trial", "pro"
    val status: String,             // "active", "trialing", "cancel_pending", "past_due", "canceled"
    val expiresAtTimestamp: Long?,
    val startedAtTimestamp: Long,
    val features: Map<String, FeatureUsage>,
    val source: String,             // "dodo_payments", "trial", "manual"
    val billingType: String,        // "subscription", "one_time"
    val externalCustomerId: String?,
    val externalReferenceId: String?,
    val lastPaymentAmountCents: Long?,
    val lastPaymentCurrency: String?,
    val createdAtTimestamp: Long,
    val updatedAtTimestamp: Long,
)

data class FeatureUsage(
    val limit: Int,
    val used: Int,
    val unlocked: Boolean,
)

/** Individual payment record. */
data class PaymentRecord(
    val date: String,               // ISO 8601
    val amountCents: Long,
    val currency: String,
    val planId: String,
    val emailSentAt: String?,       // ISO 8601
)

/** Audit log for billing webhook events. */
data class BillingAuditRecord(
    val payload: Map<String, Any?>,
    val timestamp: String,          // ISO 8601
    val webhookId: String?,
    val source: String,
)

/** Share token — grants access to an entity. */
data class Share(
    val token: String,
    val entityId: String,
    val entityCategory: String,
    val entityPath: String,
    val ownerId: String,
    val expiresAtTimestamp: Long,
    val revokedAtTimestamp: Long?,
)

/** User profile — aggregated identity info. */
data class Profile(
    val userId: String,
    val displayName: String?,
    val email: String,
    val emailNormalized: String,
    val lastSeenAt: String,         // ISO 8601
)

/** Upload metadata — tracks file upload lifecycle. */
data class UploadRecord(
    val uploadId: String,
    val assetId: String?,
    val uid: String,
    val type: String,
    val entityId: String?,
    val bucket: String,
    val objectName: String,
    val contentType: String,
    val sizeBytes: Long,
    val status: String,             // "pending", "processing", "complete", "failed"
    val createdAtTimestamp: Long,
    val expiresAtTimestamp: Long,
)

/** API call analytics record. */
data class ApiCallRecord(
    val requestId: String,
    val timestamp: Long,
    val method: String,
    val path: String,
    val rawPath: String,
    val status: Int,
    val durationMs: Long,
    val userId: String?,
    val appId: String?,
    val teamId: String?,
    val userAgent: String?,
    val error: String?,
)

/** Early access waitlist entry. */
data class EarlyAccessEntry(
    val email: String,
    val emailNormalized: String,
    val status: String,             // "waitlist", "approved"
    val createdAt: Long,
    val updatedAt: Long?,
    val approvedAt: Long?,
)
