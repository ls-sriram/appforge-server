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
                val entity = resolvePublicEntity(
                    contentResolver = contentResolver,
                    ownerId = share.ownerId,
                    share = share,
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

private suspend fun resolvePublicEntity(
    contentResolver: ReviewContentResolver,
    ownerId: String,
    share: com.appforge.server.services.sharing.models.Share,
    shareEntityRepository: ShareEntityRepositoryApi,
    uploadMetadataRepository: UploadMetadataRepository,
    signedGetUrlIssuer: SignedGetUrlIssuer,
    uploadExpirySeconds: Long
): PublicEntity {
    val category = share.entityCategory
    val entityId = share.entityId

    // Generic resolution: try document content first, then asset URL
    val doc = when (val res = shareEntityRepository.getEntityDoc(ownerId, category, entityId)) {
        is Resource.Success -> res.data
        else -> null
    }

    val content = contentResolver.resolveText(ownerId, category, entityId, null)

    val title = doc?.get("title") as? String ?: doc?.get("name") as? String ?: "Entity"
    val subtitle = doc?.get("subtitle") as? String
    val assetId = doc?.get("assetId") as? String

    val assetUrl = if (!assetId.isNullOrBlank()) {
        resolveShareAssetUrl(
            uploadMetadataRepository = uploadMetadataRepository,
            signedGetUrlIssuer = signedGetUrlIssuer,
            uploadExpirySeconds = uploadExpirySeconds,
            ownerId = ownerId,
            assetId = assetId
        )
    } else null

    return PublicEntity(
        id = entityId,
        category = category.value,
        title = title,
        subtitle = subtitle,
        content = content.takeIf { it.isNotBlank() },
        question = doc?.get("question") as? String,
        assetUrl = assetUrl
    )
}

private suspend fun resolveShareAssetUrl(
    uploadMetadataRepository: UploadMetadataRepository,
    signedGetUrlIssuer: SignedGetUrlIssuer,
    uploadExpirySeconds: Long,
    ownerId: String,
    assetId: String?
): String? {
    val resolvedAssetId = assetId?.takeIf { it.isNotBlank() } ?: return null
    val record = uploadMetadataRepository.getByAssetId(resolvedAssetId) ?: return null
    if (record.uid != ownerId) return null
    return try {
        signedGetUrlIssuer.issue(
            bucket = record.bucket,
            objectPath = record.objectName,
            expiresInSeconds = uploadExpirySeconds
        )
    } catch (e: Exception) {
        null
    }
}
