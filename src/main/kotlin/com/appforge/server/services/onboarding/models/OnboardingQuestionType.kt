package com.appforge.server.services.onboarding.models

enum class OnboardingQuestionType(val wire: String) {
    SINGLE_SELECT("single_select"),
    MULTI_SELECT("multi_select"),
    TEXT("text");

    companion object {
        fun fromWire(raw: String): OnboardingQuestionType {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.wire == normalized }
                ?: throw IllegalArgumentException("Unsupported onboarding question type: $raw")
        }
    }
}
