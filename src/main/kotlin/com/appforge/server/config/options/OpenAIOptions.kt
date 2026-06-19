package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader

data class OpenAIOptions(
    val enabled: Boolean,
    val apiKey: String,
) {
    companion object {
        fun load(reader: ConfigReader): OpenAIOptions {
            val enabled = reader.bool("OPENAI_ENABLED") ?: true
            return OpenAIOptions(
                enabled = enabled,
                apiKey = if (enabled) {
                    reader.requiredString("OPENAI_API_KEY")
                } else {
                    reader.string("OPENAI_API_KEY") ?: ""
                }
            )
        }
    }
}
