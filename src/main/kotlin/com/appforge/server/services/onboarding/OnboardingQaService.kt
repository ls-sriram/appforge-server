package com.appforge.server.services.onboarding

import com.appforge.server.api.OnboardingSubmitAnswerRequest
import com.appforge.server.api.OnboardingSubmitRequest
import com.appforge.server.api.OnboardingSubmitResponse
import com.appforge.server.infrastructure.time.protoTimestampToInstant
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.UserLifecycleCoordinator

interface OnboardingQaService {
    suspend fun submitOnboarding(userId: String, request: OnboardingSubmitRequest): AuthResponse<OnboardingSubmitResponse>
    suspend fun onboardingCompleted(userId: String): Boolean
}

class OnboardingQaServiceImpl(
    private val userLifecycleCoordinator: UserLifecycleCoordinator,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) : OnboardingQaService {
    override suspend fun submitOnboarding(userId: String, request: OnboardingSubmitRequest): AuthResponse<OnboardingSubmitResponse> {
        when (val persist = persistOnboardingAnswers(userId, request.answers, request.completedAt)) {
            is AuthResponse.BadRequest -> return persist
            else -> Unit
        }
        return AuthResponse.Ok(OnboardingSubmitResponse(success = true, uid = userId))
    }

    override suspend fun onboardingCompleted(userId: String): Boolean {
        return userLifecycleCoordinator.hasCompletedOnboarding(userId)
    }

    private suspend fun persistOnboardingAnswers(
        uid: String,
        answers: List<OnboardingSubmitAnswerRequest>,
        completedAt: com.appforge.server.api.ProtoTimestamp?,
    ): AuthResponse<Nothing?> {
        val completionTime = protoTimestampToInstant(completedAt) ?: timestampProvider.now()
        answers.forEach { answer ->
            val questionId = answer.questionId.trim()
            if (questionId.isBlank()) return AuthResponse.BadRequest("questionId is required.")
            if (!userLifecycleCoordinator.questionExists(questionId)) {
                return AuthResponse.BadRequest("Unknown onboarding questionId: $questionId")
            }
            val optionIds = answer.optionIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val textValue = answer.textValue?.trim()?.ifBlank { null }
            if (optionIds.isEmpty() && textValue == null) {
                // Empty answers are allowed; skip persistence for unanswered questions.
                return@forEach
            }
            optionIds.forEach { optionId ->
                if (!userLifecycleCoordinator.optionBelongsToQuestion(optionId, questionId)) {
                    return AuthResponse.BadRequest("optionId does not belong to questionId: $questionId")
                }
            }
            userLifecycleCoordinator.persistOnboardingAnswer(
                uid = uid,
                questionId = questionId,
                optionIds = optionIds,
                textValue = textValue,
                answeredAt = completionTime,
            )
        }
        userLifecycleCoordinator.markOnboardingCompleted(uid, completionTime)
        return AuthResponse.Ok(null)
    }
}
