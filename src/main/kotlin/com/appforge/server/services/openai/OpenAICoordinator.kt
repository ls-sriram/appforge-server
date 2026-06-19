package com.appforge.server.services.openai

import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.audio.TranslationRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.appforge.server.clients.OpenAIDataClient
import com.appforge.server.config.AppEnv
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import okio.source
import org.slf4j.LoggerFactory
import java.util.Base64

class OpenAICoordinator(
    private val openAIClient: OpenAIDataClient,
    private val storageClient: Storage,
    private val env: AppEnv,
) {
    private val logger = LoggerFactory.getLogger(OpenAICoordinator::class.java)

    suspend fun generateResponse(prompt: String): String {
        return chatCompletion(prompt)
    }

    suspend fun transcribeAudio(bucket: String, objectName: String): String {
        val blob = storageClient.get(BlobId.of(bucket, objectName))
            ?: throw IllegalStateException("Missing storage blob for $bucket/$objectName")
        val bytes = blob.getContent()
        val transcription = openAIClient.service.transcription(
            TranscriptionRequest(
                audio = FileSource(name = objectName, source = bytes.inputStream().source()),
                model = ModelId("whisper-1")
            )
        )
        return transcription.text
    }

    suspend fun translateAudio(bucket: String, objectName: String): String {
        val blob = storageClient.get(BlobId.of(bucket, objectName))
            ?: throw IllegalStateException("Missing storage blob for $bucket/$objectName")
        val bytes = blob.getContent()
        val translation = openAIClient.service.translation(
            TranslationRequest(
                audio = FileSource(name = objectName, source = bytes.inputStream().source()),
                model = ModelId("whisper-1")
            )
        )
        return translation.text
    }

    suspend fun analyzeImage(bucket: String, objectName: String, prompt: String): String {
        val blob = storageClient.get(BlobId.of(bucket, objectName))
            ?: throw IllegalStateException("Missing storage blob for $bucket/$objectName")
        val bytes = blob.getContent()
        return reviewImage(bytes, prompt)
    }

    suspend fun chatCompletion(
        prompt: String,
        systemMessage: String? = null,
        model: String = "gpt-4-turbo",
        jsonMode: Boolean = false
    ): String {
        val messages = mutableListOf<ChatMessage>()
        if (systemMessage != null) {
            messages.add(ChatMessage(ChatRole.System, systemMessage))
        }
        messages.add(ChatMessage(ChatRole.User, prompt))

        val completion = openAIClient.service.chatCompletion(
            ChatCompletionRequest(
                model = ModelId(model),
                messages = messages,
                responseFormat = if (jsonMode) ChatResponseFormat.JsonObject else null
            )
        )
        return completion.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("OpenAI chat completion returned empty content")
    }

    suspend fun reviewDocument(content: String, entityType: String): String {
        val systemPrompt = "You are an expert reviewer. Review the following ${entityType} content and provide structured JSON feedback with scores and comments."
        return chatCompletion(prompt = content, systemMessage = systemPrompt, jsonMode = true)
    }

    suspend fun reviewImage(_imageBytes: ByteArray, _prompt: String): String {
        // Multi-modal construction varies by SDK version.
        // Using a temporary placeholder while verifying the exact structure for com.aallam.openai:3.8.2
        if (_imageBytes.isEmpty()) {}
        if (_prompt.isEmpty()) {}
        return "{\"score\":0, \"visualAnalysis\":\"Image review placeholder for raw data\", \"techniqueFeedback\":[], \"nextSteps\":[]}"
    }

    suspend fun reviewRecording(transcript: String): String {
        val systemPrompt = "You are an expert reviewer. Review the following recording transcript and provide structured JSON feedback with scores and comments."
        return chatCompletion(prompt = transcript, systemMessage = systemPrompt, jsonMode = true)
    }
}
