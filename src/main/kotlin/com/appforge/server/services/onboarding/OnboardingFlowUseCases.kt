package com.appforge.server.services.onboarding

import com.appforge.server.api.OnboardingFlowFieldResponse
import com.appforge.server.api.OnboardingFlowOptionResponse
import com.appforge.server.api.OnboardingFlowResponse
import com.appforge.server.api.OnboardingQuestionTypeDto
import com.appforge.server.api.OnboardingStepTypeDto
import com.appforge.server.api.OnboardingFlowStepResponse
import com.appforge.server.services.onboarding.repository.OnboardingRepositoryApi

interface OnboardingFlowUseCases {
    suspend fun getActiveFlow(): OnboardingFlowResponse
}

class OnboardingFlowUseCasesImpl(
    private val onboardingRepository: OnboardingRepositoryApi,
) : OnboardingFlowUseCases {
    override suspend fun getActiveFlow(): OnboardingFlowResponse {
        val questions = onboardingRepository.listActiveQuestions()
        val optionsByQuestion = onboardingRepository
            .listActiveQuestionOptions()
            .groupBy { it.questionId }

        val steps = questions.map { question ->
            OnboardingFlowStepResponse(
                type = when (question.stepType.lowercase()) {
                    "profile" -> OnboardingStepTypeDto.PROFILE
                    "personalization" -> OnboardingStepTypeDto.PERSONALIZATION
                    else -> OnboardingStepTypeDto.ONBOARDING
                },
                title = question.prompt,
                description = "",
                ctaLabel = "Continue",
                fields = listOf(
                    OnboardingFlowFieldResponse(
                        id = question.id,
                        label = question.prompt,
                        type = when (question.questionType.wire) {
                            "single_select" -> OnboardingQuestionTypeDto.SINGLE_SELECT
                            "multi_select" -> OnboardingQuestionTypeDto.MULTI_SELECT
                            else -> OnboardingQuestionTypeDto.TEXT
                        },
                        options = optionsByQuestion[question.id]
                            .orEmpty()
                            .sortedBy { it.displayOrder }
                            .map { option ->
                                OnboardingFlowOptionResponse(
                                    id = option.id,
                                    label = option.label,
                                )
                            },
                    )
                ),
            )
        }

        return OnboardingFlowResponse(
            id = "global-default",
            version = 1,
            name = "Global default onboarding",
            steps = steps,
        )
    }
}
