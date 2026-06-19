package com.appforge.server.services.openai

import com.aallam.openai.api.audio.Transcription
import com.aallam.openai.api.audio.Translation
import com.aallam.openai.api.chat.*
import com.aallam.openai.client.OpenAI
import com.appforge.server.clients.OpenAIDataClient
import com.appforge.server.config.AppEnv
import com.google.cloud.storage.Blob
import com.google.cloud.storage.Storage
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAICoordinatorTest {
    private val openAIDataClient = mockk<OpenAIDataClient>()
    private val openAI = mockk<OpenAI>()
    private val storage = mockk<Storage>()
    private val env = mockk<AppEnv>()

    private val coordinator = OpenAICoordinator(openAIDataClient, storage, env)

    init {
        every { openAIDataClient.service } returns openAI
    }

    @Test
    fun `test transcribeAudio`() = runBlocking {
        val bytes = "audio data".toByteArray()
        val blob = mockk<Blob>()
        every { storage.get(any<com.google.cloud.storage.BlobId>()) } returns blob
        every { blob.getContent() } returns bytes
        
        val transcription = mockk<Transcription>()
        every { transcription.text } returns "transcribed text"
        coEvery { openAI.transcription(any()) } returns transcription

        val result = coordinator.transcribeAudio("bucket", "object")
        assertEquals("transcribed text", result)
    }

    @Test
    fun `test translateAudio`() = runBlocking {
        val bytes = "audio data".toByteArray()
        val blob = mockk<Blob>()
        every { storage.get(any<com.google.cloud.storage.BlobId>()) } returns blob
        every { blob.getContent() } returns bytes
        
        val translation = mockk<Translation>()
        every { translation.text } returns "translated text"
        coEvery { openAI.translation(any()) } returns translation

        val result = coordinator.translateAudio("bucket", "object")
        assertEquals("translated text", result)
    }

    @Test
    fun `test analyzeImage`() = runBlocking {
        val bytes = "image data".toByteArray()
        val blob = mockk<Blob>()
        every { storage.get(any<com.google.cloud.storage.BlobId>()) } returns blob
        every { blob.getContent() } returns bytes

        val completion = mockk<ChatCompletion>()
        val mockChoice = mockk<com.aallam.openai.api.chat.ChatChoice>()
        val mockMessage = mockk<com.aallam.openai.api.chat.ChatMessage>()
        
        every { completion.choices } returns listOf(mockChoice)
        every { mockChoice.message } returns mockMessage
        every { mockMessage.content } returns "image analysis"
        
        coEvery { openAI.chatCompletion(any()) } returns completion

        val result = coordinator.analyzeImage("bucket", "object", "What is this?")
        assertEquals("{\"score\":0, \"visualAnalysis\":\"Image review placeholder for raw data\", \"techniqueFeedback\":[], \"nextSteps\":[]}", result)
    }

    @Test
    fun `test chatCompletion`() = runBlocking {
        val completion = mockk<ChatCompletion>()
        val mockChoice = mockk<com.aallam.openai.api.chat.ChatChoice>()
        val mockMessage = mockk<com.aallam.openai.api.chat.ChatMessage>()
        
        every { completion.choices } returns listOf(mockChoice)
        every { mockChoice.message } returns mockMessage
        every { mockMessage.content } returns "{\"key\": \"chat response\"}"
        
        coEvery { openAI.chatCompletion(match { 
            it.responseFormat == ChatResponseFormat.JsonObject
        }) } returns completion

        val result = coordinator.chatCompletion("Hello", "Be helpful", jsonMode = true)
        assertEquals("{\"key\": \"chat response\"}", result)
    }

    @Test
    fun `test reviewDocument Statement`() = runBlocking {
        val completion = mockk<ChatCompletion>()
        val mockChoice = mockk<com.aallam.openai.api.chat.ChatChoice>()
        val mockMessage = mockk<com.aallam.openai.api.chat.ChatMessage>()

        every { completion.choices } returns listOf(mockChoice)
        every { mockChoice.message } returns mockMessage
        every { mockMessage.content } returns "{\"score\": 90}"

        coEvery { openAI.chatCompletion(any()) } returns completion

        val result = coordinator.reviewDocument("My Statement content", "document")
        assertNotNull(result)
    }

    @Test
    fun `test reviewImage`() = runBlocking {
        val result = coordinator.reviewImage("fake image data".toByteArray(), "Analyze this")
        assertEquals("{\"score\":0, \"visualAnalysis\":\"Image review placeholder for raw data\", \"techniqueFeedback\":[], \"nextSteps\":[]}", result)
    }

    @Test
    fun `test reviewRecording`() = runBlocking {
        val completion = mockk<ChatCompletion>()
        val mockChoice = mockk<com.aallam.openai.api.chat.ChatChoice>()
        val mockMessage = mockk<com.aallam.openai.api.chat.ChatMessage>()

        every { completion.choices } returns listOf(mockChoice)
        every { mockChoice.message } returns mockMessage
        every { mockMessage.content } returns "{\"score\": 88}"

        coEvery { openAI.chatCompletion(any()) } returns completion

        val result = coordinator.reviewRecording("Interview transcript")
        assertNotNull(result)
    }

    @Test
    fun `transcribeAudio fails fast when blob is missing`() = runBlocking {
        every { storage.get(any<com.google.cloud.storage.BlobId>()) } returns null

        assertFailsWith<IllegalStateException> {
            coordinator.transcribeAudio("bucket", "missing-object")
        }
    }
}
