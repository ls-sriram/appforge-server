package com.appforge.server.services.reviews.ai

import com.appforge.server.services.openai.OpenAIService
import com.appforge.server.services.reviews.models.EntityCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

interface ReviewPipeline {
    suspend fun generateReview(userId: String, entityId: String, versionId: String? = null): Map<String, Any?>
}

/**
 * Generic text-based review pipeline. Works with any entity that has text content.
 */
class TextDocumentPipeline(
    private val openAIService: OpenAIService,
    private val contentResolver: ReviewContentResolver,
    private val category: EntityCategory
) : ReviewPipeline {
    override suspend fun generateReview(userId: String, entityId: String, versionId: String?): Map<String, Any?> {
        val content = contentResolver.resolveText(userId, category, entityId, versionId)
        if (content.isBlank()) return mapOf("error" to "No content found for entity $entityId")

        val jsonResponse = openAIService.reviewDocument(content, category.value)
        return try {
            parseJsonToMap(jsonResponse)
        } catch (e: Exception) {
            mapOf("raw_feedback" to jsonResponse, "parse_error" to e.message)
        }
    }
}

/**
 * Generic audio review pipeline. Works with any entity that has an audio asset.
 */
class AudioRecordingPipeline(
    private val openAIService: OpenAIService,
    private val contentResolver: ReviewContentResolver
) : ReviewPipeline {
    override suspend fun generateReview(userId: String, entityId: String, versionId: String?): Map<String, Any?> {
        val bytes = contentResolver.resolveBytes(userId, EntityCategory(""), entityId)
        if (bytes == null) return mapOf("error" to "Could not fetch asset bytes for $entityId")

        val transcript = contentResolver.resolveTranscript(userId, EntityCategory(""), entityId)
            ?: "Transcript unavailable for $entityId"

        val jsonResponse = openAIService.reviewRecording(transcript)
        return try {
            parseJsonToMap(jsonResponse)
        } catch (e: Exception) {
            mapOf("raw_feedback" to jsonResponse, "parse_error" to e.message)
        }
    }
}

/**
 * Generic image review pipeline. Works with any entity that has an image asset.
 */
class ImageReviewPipeline(
    private val openAIService: OpenAIService,
    private val contentResolver: ReviewContentResolver
) : ReviewPipeline {
    override suspend fun generateReview(userId: String, entityId: String, versionId: String?): Map<String, Any?> {
        val bytes = contentResolver.resolveBytes(userId, EntityCategory(""), entityId)
        if (bytes == null) return mapOf("error" to "Could not fetch image bytes for $entityId")

        val jsonResponse = openAIService.reviewImage(bytes, "Review this image.")
        return try {
            parseJsonToMap(jsonResponse)
        } catch (e: Exception) {
            mapOf("raw_feedback" to jsonResponse, "parse_error" to e.message)
        }
    }
}

private fun parseJsonToMap(json: String): Map<String, Any?> {
    val element = Json.parseToJsonElement(json)
    return element.jsonObject.entries.associate { (key, value) ->
        key to jsonElementToValue(value)
    }
}

private fun jsonElementToValue(element: JsonElement): Any? {
    return when (element) {
        is JsonPrimitive -> {
            if (element.isString) element.content
            else element.contentOrNull?.let {
                it.toIntOrNull() ?: it.toLongOrNull() ?: it.toDoubleOrNull() ?: (if (it == "true" || it == "false") it.toBoolean() else null) ?: it
            }
        }
        is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToValue(it) }
        is kotlinx.serialization.json.JsonObject -> element.entries.associate { it.key to jsonElementToValue(it.value) }
        else -> null
    }
}

/**
 * Generic pipeline factory. Selects pipeline based on entity category hints.
 * Frontends pass category strings like "document", "audio", "image" — no domain coupling.
 */
class ReviewPipelineFactory(
    private val openAIService: OpenAIService,
    private val contentResolver: ReviewContentResolver
) {
    fun getPipeline(category: EntityCategory): ReviewPipeline {
        return when (category.value.lowercase()) {
            "document", "text" -> TextDocumentPipeline(openAIService, contentResolver, category)
            "audio", "recording" -> AudioRecordingPipeline(openAIService, contentResolver)
            "image", "photo" -> ImageReviewPipeline(openAIService, contentResolver)
            else -> TextDocumentPipeline(openAIService, contentResolver, category)
        }
    }
}
