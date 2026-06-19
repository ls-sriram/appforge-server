package com.appforge.server.api.reviews

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ReviewResponse(
    val id: String,
    val authorRole: String,
    val authorId: String?,
    val authorName: String?,
    val authorEmail: String?,
    val content: Map<String, JsonElement>,
    val createdAtTimestamp: Long // epoch millis
)

@Serializable
data class SubmitReviewRequest(
    val displayName: String,
    val reviewFormId: String,
    val reviewFormVersion: Int,
    val answers: List<ReviewAnswerRequest> = emptyList(),
)

@Serializable
data class ReviewAnswerRequest(
    val fieldId: String,
    val optionIds: List<String> = emptyList(),
    val textValue: String? = null,
)

@Serializable
data class ReviewTemplateResponse(
    val reviewForm: ReviewTemplate,
)

@Serializable
data class ReviewTemplate(
    val id: String,
    val version: Int,
    val entityType: String,
    val name: String,
    val fields: List<ReviewTemplateField>,
)

@Serializable
data class ReviewTemplateField(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val options: List<ReviewTemplateOption> = emptyList(),
)

@Serializable
data class ReviewTemplateOption(
    val id: String,
    val label: String,
)
