package com.appforge.server.services.reviews.ai

import com.appforge.server.services.openai.OpenAIService
import com.appforge.server.services.reviews.models.EntityCategory
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class ReviewPipelineTest {
    private val openAIService = mockk<OpenAIService>()
    private val contentResolver = mockk<ReviewContentResolver>()

    @Test
    fun `TextDocumentPipeline resolves specific version when provided`() = runBlocking {
        val pipeline = TextDocumentPipeline(openAIService, contentResolver, EntityCategory("document"))
        val userId = "u1"
        val entityId = "e1"
        val versionId = "v1"
        val content = "My Statement version 1"
        val jsonResponse = "{\"score\": 95}"

        coEvery { contentResolver.resolveText(userId, EntityCategory("document"), entityId, versionId) } returns content
        coEvery { openAIService.reviewDocument(content, "document") } returns jsonResponse

        val result = pipeline.generateReview(userId, entityId, versionId)

        assertEquals(95, result["score"])
        coVerify { contentResolver.resolveText(userId, EntityCategory("document"), entityId, versionId) }
    }

    @Test
    fun `TextDocumentPipeline resolves latest version when versionId is null`() = runBlocking {
        val pipeline = TextDocumentPipeline(openAIService, contentResolver, EntityCategory("document"))
        val userId = "u1"
        val entityId = "e1"
        val content = "My Latest Statement"
        val jsonResponse = "{\"score\": 80}"

        coEvery { contentResolver.resolveText(userId, EntityCategory("document"), entityId, null) } returns content
        coEvery { openAIService.reviewDocument(content, "document") } returns jsonResponse

        val result = pipeline.generateReview(userId, entityId, null)

        assertEquals(80, result["score"])
        coVerify { contentResolver.resolveText(userId, EntityCategory("document"), entityId, null) }
    }

    @Test
    fun `AudioRecordingPipeline resolves bytes and transcript then calls reviewRecording`() = runBlocking {
        val pipeline = AudioRecordingPipeline(openAIService, contentResolver, EntityCategory("recording"))
        val userId = "u2"
        val entityId = "e2"
        val transcript = "Optimized transcript"
        val jsonResponse = "{\"overallScore\": 88}"

        coEvery { contentResolver.resolveTranscript(userId, EntityCategory("recording"), entityId) } returns transcript
        coEvery { openAIService.reviewRecording(transcript) } returns jsonResponse

        val result = pipeline.generateReview(userId, entityId)

        assertEquals(88, result["overallScore"])
    }

    @Test
    fun `ImageReviewPipeline resolves bytes and calls reviewImage`() = runBlocking {
        val pipeline = ImageReviewPipeline(openAIService, contentResolver, EntityCategory("image"))
        val userId = "u3"
        val entityId = "e3"
        val bytes = "image data".toByteArray()
        val jsonResponse = "{\"score\": 75}"

        coEvery { contentResolver.resolveBytes(userId, EntityCategory("image"), entityId) } returns bytes
        coEvery { openAIService.reviewImage(bytes, any()) } returns jsonResponse

        val result = pipeline.generateReview(userId, entityId)

        assertEquals(75, result["score"])
    }

    @Test
    fun `TextDocumentPipeline fails when content is missing`() {
        val pipeline = TextDocumentPipeline(openAIService, contentResolver, EntityCategory("document"))
        val userId = "u1"
        val entityId = "e1"

        coEvery { contentResolver.resolveText(userId, EntityCategory("document"), entityId) } returns ""

        val error = assertThrows<IllegalStateException> {
            runBlocking { pipeline.generateReview(userId, entityId) }
        }
        assertEquals("No content found for entity e1", error.message)
    }

    @Test
    fun `TextDocumentPipeline handles JSON parse errors`() = runBlocking {
        val pipeline = TextDocumentPipeline(openAIService, contentResolver, EntityCategory("document"))
        val userId = "u1"
        val entityId = "e1"
        val content = "My Statement text"
        val invalidJson = "Not a JSON"

        coEvery { contentResolver.resolveText(userId, EntityCategory("document"), entityId) } returns content
        coEvery { openAIService.reviewDocument(content, "document") } returns invalidJson

        val result = pipeline.generateReview(userId, entityId)

        assertEquals(invalidJson, result["raw_feedback"])
        assertTrue { result.containsKey("parse_error") }
    }

    @Test
    fun `AudioRecordingPipeline fails when transcript is unavailable`() {
        val pipeline = AudioRecordingPipeline(openAIService, contentResolver, EntityCategory("recording"))
        val userId = "u2"
        val entityId = "e2"

        coEvery { contentResolver.resolveTranscript(userId, EntityCategory("recording"), entityId) } returns null

        val error = assertThrows<IllegalStateException> {
            runBlocking { pipeline.generateReview(userId, entityId) }
        }
        assertEquals("Transcript unavailable for e2", error.message)
    }
    
    private fun assertTrue(block: () -> Boolean) {
        if (!block()) throw AssertionError("Expected true")
    }
}
