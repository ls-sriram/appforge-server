package com.appforge.server.services.openai

import com.appforge.server.clients.OpenAIDataClient
import com.appforge.server.config.AppEnv
import com.google.cloud.storage.Storage
import kotlinx.coroutines.runBlocking

class OpenAIService(
    openAIClient: OpenAIDataClient,
    storageClient: Storage,
    env: AppEnv,
) {
    private val coordinator = OpenAICoordinator(
        openAIClient = openAIClient,
        storageClient = storageClient,
        env = env,
    )

    fun generateResponse(prompt: String): String {
        return runBlocking {
            coordinator.generateResponse(prompt)
        }
    }

    fun transcribeAudio(bucket: String, objectName: String): String {
        return runBlocking {
            coordinator.transcribeAudio(bucket, objectName)
        }
    }

    fun translateAudio(bucket: String, objectName: String): String {
        return runBlocking {
            coordinator.translateAudio(bucket, objectName)
        }
    }

    fun analyzeImage(bucket: String, objectName: String, prompt: String): String {
        return runBlocking {
            coordinator.analyzeImage(bucket, objectName, prompt)
        }
    }

    fun chatCompletion(prompt: String, systemMessage: String? = null, model: String = "gpt-4-turbo"): String {
        return runBlocking {
            coordinator.chatCompletion(prompt, systemMessage, model)
        }
    }

    fun reviewDocument(content: String, entityType: String): String {
        return runBlocking {
            coordinator.reviewDocument(content, entityType)
        }
    }

    fun reviewImage(imageBytes: ByteArray, prompt: String): String {
        return runBlocking {
            coordinator.reviewImage(imageBytes, prompt)
        }
    }

    fun reviewRecording(transcript: String): String {
        return runBlocking {
            coordinator.reviewRecording(transcript)
        }
    }
}
