package com.appforge.server.services.sharing

import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.api.reviews.SubmitReviewRequest
import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.api.sharing.PublicEntityResponse
import com.appforge.server.api.sharing.PublicShareMetadata
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.middleware.GoneException
import com.appforge.server.routing.ApiConverters
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.repository.RecordingRepository
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.forms.repository.FormRepositoryApi
import com.appforge.server.services.forms.validation.ReviewFormValidator
import com.appforge.server.services.sharing.repository.ShareEntityRepositoryApi
import com.appforge.server.services.sharing.services.ShareService
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository

interface PublicShareUseCases {
    suspend fun getPublicShare(token: String): PublicEntityResponse
    suspend fun getReviewTemplate(token: String): ReviewTemplateResponse
    suspend fun submitReview(token: String, request: SubmitReviewRequest): ReviewResponse
    suspend fun getSharedRecordingContent(token: String): RecordingContent
}

class PublicShareUseCasesImpl(
    private val shareService: ShareService,
    private val reviewService: ReviewService,
    private val contentResolver: ReviewContentResolver,
    private val shareEntityRepository: ShareEntityRepositoryApi,
    private val formRepository: FormRepositoryApi,
    private val uploadMetadataRepository: UploadMetadataRepository,
    private val signedGetUrlIssuer: SignedGetUrlIssuer,
    private val uploadExpirySeconds: Long,
    private val recordingRepository: RecordingRepository,
) : PublicShareUseCases {
    override suspend fun getPublicShare(token: String): PublicEntityResponse {
        val shareRes = shareService.getAndValidateShare(token)
        when (shareRes) {
            is Resource.Success -> {
                val share = shareRes.data
                val entity = resolveSharedEntity(
                    contentResolver = contentResolver,
                    ownerId = share.ownerId,
                    entityCategory = share.entityCategory.value,
                    entityId = share.entityId,
                    shareEntityRepository = shareEntityRepository,
                    uploadMetadataRepository = uploadMetadataRepository,
                    signedGetUrlIssuer = signedGetUrlIssuer,
                    uploadExpirySeconds = uploadExpirySeconds
                )

                return PublicEntityResponse(
                    share = PublicShareMetadata(
                        token = share.token,
                        entityType = share.entityCategory.value,
                        entityId = share.entityId,
                        accessMode = share.accessMode.wire,
                        expiresAt = instantToProtoTimestamp(share.expiresAt),
                        revokedAt = instantToProtoTimestamp(share.revokedAt),
                    ),
                    entity = entity
                )
            }
            is Resource.Error -> throw GoneException(shareRes.exception.message ?: "Expired or Revoked")
            else -> throw GoneException("Expired or Revoked")
        }
    }

    override suspend fun submitReview(token: String, request: SubmitReviewRequest): ReviewResponse {
        val shareRes = shareService.getAndValidateShare(token)
        if (shareRes !is Resource.Success) {
            throw GoneException("Expired or Revoked")
        }

        val share = shareRes.data
        val reviewForm = formRepository.getActiveReviewFormByEntityType(share.entityCategory.value)
            ?: throw IllegalArgumentException("No active review form configured for ${share.entityCategory.value}.")
        if (request.reviewFormId != reviewForm.id || request.reviewFormVersion != reviewForm.version) {
            throw IllegalArgumentException("Review form mismatch.")
        }
        val validationError = ReviewFormValidator.validateAnswers(reviewForm, request.answers)
        if (validationError != null) {
            throw IllegalArgumentException(validationError)
        }

        val content = mutableMapOf<String, Any?>()
        for (answer in request.answers) {
            val field = reviewForm.fields.firstOrNull { it.id == answer.fieldId } ?: continue
            when (field.type) {
                "text" -> content[field.id] = answer.textValue?.trim()
                "single_select" -> content[field.id] = answer.optionIds.firstOrNull()
                "multi_select" -> content[field.id] = answer.optionIds
            }
        }

        val result = reviewService.submitExternalReview(
            userId = share.ownerId,
            entityCategory = share.entityCategory,
            entityId = share.entityId,
            displayName = request.displayName,
            content = content
        )

        return when (result) {
            is Resource.Success -> ApiConverters.toReviewResponse(result.data)
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            else -> throw IllegalStateException("Error")
        }
    }

    override suspend fun getSharedRecordingContent(token: String): RecordingContent {
        val shareRes = shareService.getAndValidateShare(token)
        if (shareRes !is Resource.Success) {
            throw GoneException("Expired or Revoked")
        }

        val share = shareRes.data
        if (share.entityCategory.value != "recording") {
            throw GoneException("Recording share not found")
        }

        return recordingRepository.getByIdAndUser(share.entityId, share.ownerId)
            ?: throw GoneException("Recording share not found")
    }

    override suspend fun getReviewTemplate(token: String): ReviewTemplateResponse {
        val shareRes = shareService.getAndValidateShare(token)
        if (shareRes !is Resource.Success) {
            throw GoneException("Expired or Revoked")
        }
        val share = shareRes.data
        val reviewForm = formRepository.getActiveReviewFormByEntityType(share.entityCategory.value)
            ?: throw GoneException("Review form not found")
        return ReviewTemplateResponse(
            reviewForm = reviewForm,
        )
    }
}
