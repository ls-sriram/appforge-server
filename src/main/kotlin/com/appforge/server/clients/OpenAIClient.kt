package com.appforge.server.clients

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.appforge.server.config.AppEnv
import kotlin.time.Duration.Companion.seconds

interface OpenAIDataClient {
    val service: OpenAI
}

class OpenAIClient(env: AppEnv, serviceOverride: OpenAI? = null) : OpenAIDataClient {
    companion object {
        @Volatile
        private var instance: OpenAIClient? = null

        fun getInstance(env: AppEnv, serviceOverride: OpenAI? = null): OpenAIClient =
            instance ?: synchronized(this) {
                instance ?: OpenAIClient(env, serviceOverride).also { instance = it }
            }
    }

    override val service: OpenAI = serviceOverride ?: OpenAI(
        config = OpenAIConfig(
            token = env.openai.apiKey,
            timeout = Timeout(socket = 60.seconds)
        )
    )
}
