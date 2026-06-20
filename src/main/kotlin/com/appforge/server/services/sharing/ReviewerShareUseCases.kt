package com.appforge.server.services.sharing

import com.appforge.server.api.sharing.CreateReviewerShareRequest
import com.appforge.server.api.sharing.ReviewerShareEntityResponse
import com.appforge.server.api.sharing.ReviewerShareResponse
import com.appforge.server.api.sharing.SubmitReviewerShareReviewRequest
import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.middleware.ForbiddenException
import com.appforge.server.middleware.GoneException
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.routing.ApiConverters
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.templates.EmailTemplates
import com.appforge.server.services.forms.repository.FormRepositoryApi
import com.appforge.server.services.forms.validation.ReviewFormValidator
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.sharing.models.ReviewerEntityShare
import com.appforge.server.services.sharing.models.ReviewerIdentity
import com.appforge.server.services.sharing.models.ReviewerShareStatus
import com.appforge.server.services.sharing.repository.ReviewerShareRepositoryApi
import com.appforge.server.services.sharing.repository.ShareEntityRepositoryApi
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

interface ReviewerShareUseCases {
    suspend fun createReviewerShare(
        userId: String,
        type: String,
        entityId: String,
        request: CreateReviewerShareRequest,
    ): ReviewerShareResponse
    suspend fun listReviewerSharesForEntity(
        userId: String,
        type: String,
        entityId: String,
    ): List<ReviewerShareResponse>
    suspend fun revokeReviewerShare(
        userId: String,
        shareId: String,
    )
    suspend fun listReviewerInbox(reviewerUserId: String): List<ReviewerShareResponse>
    suspend fun getReviewerShare(
        reviewerUserId: String,
        shareId: String,
    ): ReviewerShareEntityResponse
    suspend fun getReviewerShareReviewTemplate(
        reviewerUserId: String,
        shareId: String,
    ): ReviewTemplateResponse
    suspend fun submitReviewerShareReview(
        reviewerUserId: String,
        shareId: String,
        request: SubmitReviewerShareReviewRequest,
    ): ReviewResponse
}

class ReviewerShareUseCasesImpl(
    private val reviewerShareRepository: ReviewerShareRepositoryApi,
    private val userRepository: UserRepositoryApi,
    private val authService: AuthService,
    private val emailService: EmailService,
    private val publicBaseUrl: String,
    private val reviewService: ReviewService,
    private val contentResolver: ReviewContentResolver,
    private val shareEntityRepository: ShareEntityRepositoryApi,
    private val formRepository: FormRepositoryApi,
    private val uploadMetadataRepository: UploadMetadataRepository,
    private val signedGetUrlIssuer: SignedGetUrlIssuer,
    private val uploadExpirySeconds: Long,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) : ReviewerShareUseCases {
    override suspend fun createReviewerShare(
        userId: String,
        type: String,
        entityId: String,
        request: CreateReviewerShareRequest,
    ): ReviewerShareResponse {
        val reviewerEmail = request.reviewerEmail.trim()
        if (reviewerEmail.isBlank()) throw IllegalArgumentException("Reviewer email is required.")
        val owner = userRepository.getUser(userId) ?: throw IllegalArgumentException("Owner profile not found.")
        val normalizedReviewerEmail = normalizeEmail(reviewerEmail)
        if (normalizeEmail(owner.email) == normalizedReviewerEmail) {
            throw IllegalArgumentException("Reviewer email cannot match owner email.")
        }
        val category = EntityCategory(type)
        val existing = reviewerShareRepository.findActiveByOwnerEntityReviewer(userId, category, entityId, normalizedReviewerEmail)
        if (existing is Resource.Success && existing.data != null) {
            return existing.data.toResponse(ownerEmail = owner.email, ownerName = owner.displayName)
        }
        if (existing is Resource.Error) throw IllegalStateException(existing.exception.message ?: "Error")

        val now = timestampProvider.now()
        val share = ReviewerEntityShare(
            id = IdentifierProvider.newUuid(),
            ownerId = userId,
            entityCategory = category,
            entityId = entityId,
            reviewerEmail = reviewerEmail,
            reviewerEmailNormalized = normalizedReviewerEmail,
            createdBy = userId,
            expiresAt = now.plus(21, ChronoUnit.DAYS),
            createdAt = now,
        )
        when (val result = reviewerShareRepository.create(share)) {
            is Resource.Success -> {
                val reviewerUrl = "$publicBaseUrl/web/reviewer/shares/${share.id}"
                val emailContent = EmailTemplates.reviewShareInvite(
                    shareUrl = reviewerUrl,
                    entityCategory = share.entityCategory.value,
                )
                emailService.sendEmail(
                    to = reviewerEmail,
                    subject = emailContent.subject,
                    content = emailContent.html,
                    isHtml = true,
                )
                return result.data.toResponse(ownerEmail = owner.email, ownerName = owner.displayName)
            }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> throw IllegalStateException("Error")
        }
    }

    override suspend fun listReviewerSharesForEntity(
        userId: String,
        type: String,
        entityId: String,
    ): List<ReviewerShareResponse> {
        val owner = userRepository.getUser(userId) ?: throw IllegalArgumentException("Owner profile not found.")
        return when (val result = reviewerShareRepository.listActiveByOwnerEntity(userId, EntityCategory(type), entityId)) {
            is Resource.Success -> result.data.map { it.toResponse(ownerEmail = owner.email, ownerName = owner.displayName) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> emptyList()
        }
    }

    override suspend fun revokeReviewerShare(userId: String, shareId: String) {
        when (val result = reviewerShareRepository.revokeByIdAndOwner(ownerId = userId, shareId = shareId, revokedAt = timestampProvider.now(), revokedBy = userId)) {
            is Resource.Success -> return
            is Resource.Error -> {
                if (result.exception.message == "Unauthorized") throw ForbiddenException("Unauthorized")
                throw IllegalStateException(result.exception.message ?: "Error")
            }
            Resource.Loading -> return
        }
    }

    override suspend fun listReviewerInbox(reviewerUserId: String): List<ReviewerShareResponse> {
        val reviewerIdentity = resolveReviewerIdentity(reviewerUserId)
        return when (val result = reviewerShareRepository.listActiveForReviewer(reviewerIdentity.emailNormalized())) {
            is Resource.Success -> result.data.map { share ->
                val owner = userRepository.getUser(share.ownerId)
                share.toResponse(
                    ownerEmail = owner?.email,
                    ownerName = owner?.displayName,
                )
            }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> emptyList()
        }
    }

    override suspend fun getReviewerShare(
        reviewerUserId: String,
        shareId: String,
    ): ReviewerShareEntityResponse {
        val reviewerIdentity = resolveReviewerIdentity(reviewerUserId)
        val share = loadActiveReviewerShare(shareId, reviewerIdentity.emailNormalized())
        val owner = userRepository.getUser(share.ownerId)
        val entity = resolveSharedEntity(
            contentResolver = contentResolver,
            ownerId = share.ownerId,
            entityCategory = share.entityCategory.value,
            entityId = share.entityId,
            shareEntityRepository = shareEntityRepository,
            uploadMetadataRepository = uploadMetadataRepository,
            signedGetUrlIssuer = signedGetUrlIssuer,
            uploadExpirySeconds = uploadExpirySeconds,
        )
        return ReviewerShareEntityResponse(
            share = share.toResponse(ownerEmail = owner?.email, ownerName = owner?.displayName),
            entity = entity,
        )
    }

    override suspend fun getReviewerShareReviewTemplate(
        reviewerUserId: String,
        shareId: String,
    ): ReviewTemplateResponse {
        val reviewerIdentity = resolveReviewerIdentity(reviewerUserId)
        val share = loadActiveReviewerShare(shareId, reviewerIdentity.emailNormalized())
        val reviewForm = formRepository.getActiveReviewFormByEntityType(share.entityCategory.value)
            ?: throw GoneException("Review form not found")
        return ReviewTemplateResponse(reviewForm = reviewForm)
    }

    override suspend fun submitReviewerShareReview(
        reviewerUserId: String,
        shareId: String,
        request: SubmitReviewerShareReviewRequest,
    ): ReviewResponse {
        val reviewerIdentity = resolveReviewerIdentity(reviewerUserId)
        val share = loadActiveReviewerShare(shareId, reviewerIdentity.emailNormalized())
        val reviewForm = formRepository.getActiveReviewFormByEntityType(share.entityCategory.value)
            ?: throw IllegalArgumentException("No active review form configured for ${share.entityCategory.value}.")
        if (request.reviewFormId != reviewForm.id || request.reviewFormVersion != reviewForm.version) {
            throw IllegalArgumentException("Review form mismatch.")
        }
        val validationError = ReviewFormValidator.validateAnswers(reviewForm, request.answers)
        if (validationError != null) throw IllegalArgumentException(validationError)

        val content = mutableMapOf<String, Any?>()
        for (answer in request.answers) {
            val field = reviewForm.fields.firstOrNull { it.id == answer.fieldId } ?: continue
            when (field.type) {
                "text" -> content[field.id] = answer.textValue?.trim()
                "single_select" -> content[field.id] = answer.optionIds.firstOrNull()
                "multi_select" -> content[field.id] = answer.optionIds
            }
        }

        val displayName = reviewerIdentity.displayName?.takeIf { it.isNotBlank() }
            ?: reviewerIdentity.email.substringBefore("@")

        val result = reviewService.submitExternalReview(
            userId = share.ownerId,
            entityCategory = share.entityCategory,
            entityId = share.entityId,
            displayName = displayName,
            authorEmail = reviewerIdentity.email,
            content = content,
        )
        return when (result) {
            is Resource.Success -> ApiConverters.toReviewResponse(result.data)
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> throw IllegalStateException("Error")
        }
    }

    private suspend fun resolveReviewerIdentity(reviewerUserId: String): ReviewerIdentity {
        val user = userRepository.getUser(reviewerUserId)
        val email = user?.email ?: authService.getUserEmail(reviewerUserId)
        if (email.isNullOrBlank()) throw ForbiddenException("Reviewer account is missing email.")
        return ReviewerIdentity(
            userId = reviewerUserId,
            email = email,
            displayName = user?.displayName,
        )
    }

    private suspend fun loadActiveReviewerShare(
        shareId: String,
        reviewerEmailNormalized: String,
    ): ReviewerEntityShare {
        return when (val result = reviewerShareRepository.getActiveForReviewer(shareId, reviewerEmailNormalized)) {
            is Resource.Success -> result.data ?: throw GoneException("Reviewer share not found")
            is Resource.Error -> throw GoneException(result.exception.message ?: "Reviewer share not found")
            Resource.Loading -> throw GoneException("Reviewer share not found")
        }
    }
}

private fun normalizeEmail(value: String): String =
    value.trim().lowercase(Locale.US)

private fun ReviewerIdentity.emailNormalized(): String = normalizeEmail(email)

private fun ReviewerEntityShare.toResponse(
    ownerEmail: String?,
    ownerName: String?,
): ReviewerShareResponse {
    val status = when {
        revokedAt != null -> ReviewerShareStatus.REVOKED.wire
        expiresAt != null && !expiresAt.isAfter(Instant.now()) -> ReviewerShareStatus.EXPIRED.wire
        else -> ReviewerShareStatus.ACTIVE.wire
    }
    return ReviewerShareResponse(
        id = id,
        entityType = entityCategory.value,
        entityId = entityId,
        reviewerEmail = reviewerEmail,
        status = status,
        createdAt = instantToProtoTimestamp(createdAt) ?: error("reviewer share createdAt is required"),
        expiresAt = instantToProtoTimestamp(expiresAt),
        revokedAt = instantToProtoTimestamp(revokedAt),
        ownerUid = ownerId,
        ownerName = ownerName,
        ownerEmail = ownerEmail,
    )
}
