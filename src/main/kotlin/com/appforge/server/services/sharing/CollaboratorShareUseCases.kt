package com.appforge.server.services.sharing

import com.appforge.server.api.sharing.CollaboratorShareEntityResponse
import com.appforge.server.api.sharing.CollaboratorShareResponse
import com.appforge.server.api.sharing.CreateCollaboratorShareRequest
import com.appforge.server.api.sharing.SubmitCollaboratorReviewRequest
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
import com.appforge.server.services.sharing.models.CollaboratorEntityShare
import com.appforge.server.services.sharing.models.CollaboratorIdentity
import com.appforge.server.services.sharing.models.CollaboratorShareStatus
import com.appforge.server.services.sharing.repository.CollaboratorShareRepositoryApi
import com.appforge.server.services.sharing.repository.ShareEntityRepositoryApi
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

interface CollaboratorShareUseCases {
    suspend fun createCollaboratorShare(
        userId: String,
        type: String,
        entityId: String,
        request: CreateCollaboratorShareRequest,
    ): CollaboratorShareResponse
    suspend fun listCollaboratorSharesForEntity(
        userId: String,
        type: String,
        entityId: String,
    ): List<CollaboratorShareResponse>
    suspend fun revokeCollaboratorShare(
        userId: String,
        shareId: String,
    )
    suspend fun listCollaboratorInbox(collaboratorUserId: String): List<CollaboratorShareResponse>
    suspend fun getCollaboratorShare(
        collaboratorUserId: String,
        shareId: String,
    ): CollaboratorShareEntityResponse
    suspend fun getCollaboratorReviewTemplate(
        collaboratorUserId: String,
        shareId: String,
    ): ReviewTemplateResponse
    suspend fun submitCollaboratorReview(
        collaboratorUserId: String,
        shareId: String,
        request: SubmitCollaboratorReviewRequest,
    ): ReviewResponse
}

class CollaboratorShareUseCasesImpl(
    private val collaboratorShareRepository: CollaboratorShareRepositoryApi,
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
) : CollaboratorShareUseCases {
    override suspend fun createCollaboratorShare(
        userId: String,
        type: String,
        entityId: String,
        request: CreateCollaboratorShareRequest,
    ): CollaboratorShareResponse {
        val collaboratorEmail = request.collaboratorEmail.trim()
        if (collaboratorEmail.isBlank()) throw IllegalArgumentException("Collaborator email is required.")
        val owner = userRepository.getUser(userId) ?: throw IllegalArgumentException("Owner profile not found.")
        val normalizedCollaboratorEmail = normalizeEmail(collaboratorEmail)
        if (normalizeEmail(owner.email) == normalizedCollaboratorEmail) {
            throw IllegalArgumentException("Collaborator email cannot match owner email.")
        }
        val category = EntityCategory(type)
        val existing = collaboratorShareRepository.findActiveByOwnerEntityCollaborator(userId, category, entityId, normalizedCollaboratorEmail)
        if (existing is Resource.Success && existing.data != null) {
            return existing.data.toResponse(ownerEmail = owner.email, ownerName = owner.displayName)
        }
        if (existing is Resource.Error) throw IllegalStateException(existing.exception.message ?: "Error")

        val now = timestampProvider.now()
        val share = CollaboratorEntityShare(
            id = IdentifierProvider.newUuid(),
            ownerId = userId,
            entityCategory = category,
            entityId = entityId,
            collaboratorEmail = collaboratorEmail,
            collaboratorEmailNormalized = normalizedCollaboratorEmail,
            createdBy = userId,
            expiresAt = now.plus(21, ChronoUnit.DAYS),
            createdAt = now,
        )
        when (val result = collaboratorShareRepository.create(share)) {
            is Resource.Success -> {
                val collaboratorUrl = "$publicBaseUrl/web/collaborator/shares/${share.id}"
                val emailContent = EmailTemplates.reviewShareInvite(
                    shareUrl = collaboratorUrl,
                    entityCategory = share.entityCategory.value,
                )
                emailService.sendEmail(
                    to = collaboratorEmail,
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

    override suspend fun listCollaboratorSharesForEntity(
        userId: String,
        type: String,
        entityId: String,
    ): List<CollaboratorShareResponse> {
        val owner = userRepository.getUser(userId) ?: throw IllegalArgumentException("Owner profile not found.")
        return when (val result = collaboratorShareRepository.listActiveByOwnerEntity(userId, EntityCategory(type), entityId)) {
            is Resource.Success -> result.data.map { it.toResponse(ownerEmail = owner.email, ownerName = owner.displayName) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> emptyList()
        }
    }

    override suspend fun revokeCollaboratorShare(userId: String, shareId: String) {
        when (val result = collaboratorShareRepository.revokeByIdAndOwner(ownerId = userId, shareId = shareId, revokedAt = timestampProvider.now(), revokedBy = userId)) {
            is Resource.Success -> return
            is Resource.Error -> {
                if (result.exception.message == "Unauthorized") throw ForbiddenException("Unauthorized")
                throw IllegalStateException(result.exception.message ?: "Error")
            }
            Resource.Loading -> return
        }
    }

    override suspend fun listCollaboratorInbox(collaboratorUserId: String): List<CollaboratorShareResponse> {
        val collaboratorIdentity = resolveCollaboratorIdentity(collaboratorUserId)
        return when (val result = collaboratorShareRepository.listActiveForCollaborator(collaboratorIdentity.emailNormalized())) {
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

    override suspend fun getCollaboratorShare(
        collaboratorUserId: String,
        shareId: String,
    ): CollaboratorShareEntityResponse {
        val collaboratorIdentity = resolveCollaboratorIdentity(collaboratorUserId)
        val share = loadActiveCollaboratorShare(shareId, collaboratorIdentity.emailNormalized())
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
        return CollaboratorShareEntityResponse(
            share = share.toResponse(ownerEmail = owner?.email, ownerName = owner?.displayName),
            entity = entity,
        )
    }

    override suspend fun getCollaboratorReviewTemplate(
        collaboratorUserId: String,
        shareId: String,
    ): ReviewTemplateResponse {
        val collaboratorIdentity = resolveCollaboratorIdentity(collaboratorUserId)
        val share = loadActiveCollaboratorShare(shareId, collaboratorIdentity.emailNormalized())
        val reviewForm = formRepository.getActiveReviewFormByEntityType(share.entityCategory.value)
            ?: throw GoneException("Review form not found")
        return ReviewTemplateResponse(reviewForm = reviewForm)
    }

    override suspend fun submitCollaboratorReview(
        collaboratorUserId: String,
        shareId: String,
        request: SubmitCollaboratorReviewRequest,
    ): ReviewResponse {
        val collaboratorIdentity = resolveCollaboratorIdentity(collaboratorUserId)
        val share = loadActiveCollaboratorShare(shareId, collaboratorIdentity.emailNormalized())
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

        val displayName = collaboratorIdentity.displayName?.takeIf { it.isNotBlank() }
            ?: collaboratorIdentity.email.substringBefore("@")

        val result = reviewService.submitExternalReview(
            userId = share.ownerId,
            entityCategory = share.entityCategory,
            entityId = share.entityId,
            displayName = displayName,
            authorEmail = collaboratorIdentity.email,
            content = content,
        )
        return when (result) {
            is Resource.Success -> ApiConverters.toReviewResponse(result.data)
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            Resource.Loading -> throw IllegalStateException("Error")
        }
    }

    private suspend fun resolveCollaboratorIdentity(collaboratorUserId: String): CollaboratorIdentity {
        val user = userRepository.getUser(collaboratorUserId)
        val email = user?.email ?: authService.getUserEmail(collaboratorUserId)
        if (email.isNullOrBlank()) throw ForbiddenException("Collaborator account is missing email.")
        return CollaboratorIdentity(
            userId = collaboratorUserId,
            email = email,
            displayName = user?.displayName,
        )
    }

    private suspend fun loadActiveCollaboratorShare(
        shareId: String,
        collaboratorEmailNormalized: String,
    ): CollaboratorEntityShare {
        return when (val result = collaboratorShareRepository.getActiveForCollaborator(shareId, collaboratorEmailNormalized)) {
            is Resource.Success -> result.data ?: throw GoneException("Collaborator share not found")
            is Resource.Error -> throw GoneException(result.exception.message ?: "Collaborator share not found")
            Resource.Loading -> throw GoneException("Collaborator share not found")
        }
    }
}

private fun normalizeEmail(value: String): String =
    value.trim().lowercase(Locale.US)

private fun CollaboratorIdentity.emailNormalized(): String = normalizeEmail(email)

private fun CollaboratorEntityShare.toResponse(
    ownerEmail: String?,
    ownerName: String?,
): CollaboratorShareResponse {
    val status = when {
        revokedAt != null -> CollaboratorShareStatus.REVOKED.wire
        expiresAt != null && !expiresAt.isAfter(Instant.now()) -> CollaboratorShareStatus.EXPIRED.wire
        else -> CollaboratorShareStatus.ACTIVE.wire
    }
    return CollaboratorShareResponse(
        id = id,
        entityType = entityCategory.value,
        entityId = entityId,
        collaboratorEmail = collaboratorEmail,
        status = status,
        createdAt = instantToProtoTimestamp(createdAt) ?: error("collaborator share createdAt is required"),
        expiresAt = instantToProtoTimestamp(expiresAt),
        revokedAt = instantToProtoTimestamp(revokedAt),
        ownerUid = ownerId,
        ownerName = ownerName,
        ownerEmail = ownerEmail,
    )
}
