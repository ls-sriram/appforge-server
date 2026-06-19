package com.appforge.server.services.auth

import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.onboarding.repository.OnboardingRepositoryApi
import com.appforge.server.infrastructure.time.*
import org.slf4j.LoggerFactory

class UserLifecycleCoordinator(
    private val repository: UserRepositoryApi,
    private val onboardingRepository: OnboardingRepositoryApi,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) {
    private val logger = LoggerFactory.getLogger(UserLifecycleCoordinator::class.java)

    suspend fun ensureUserCreated(uid: String, email: String?, displayName: String?) {
        if (email.isNullOrBlank()) {
            logger.warn("Skipping user creation for uid={} because email is blank", uid)
            return
        }

        val now = timestampProvider.now()
        repository.upsertUser(uid = uid, email = email, displayName = displayName, lastLoginAt = now)
        repository.upsertProfile(uid = uid, email = email, displayName = displayName, lastSeenAt = now)
        onboardingRepository.initializeState(userId = uid, now = now, version = 1)
    }

    suspend fun hasExistingAccount(uid: String): Boolean {
        return repository.getUser(uid) != null
    }

    suspend fun persistOnboardingAnswer(
        uid: String,
        questionId: String,
        optionIds: List<String>,
        textValue: String?,
        answeredAt: AppTimestamp,
    ) {
        onboardingRepository.replaceAnswers(
            userId = uid,
            questionId = questionId,
            optionIds = optionIds,
            textValue = textValue,
            now = answeredAt,
        )
    }

    suspend fun markOnboardingCompleted(uid: String, completedAt: AppTimestamp) {
        onboardingRepository.markCompleted(uid, completedAt, version = 1)
    }

    suspend fun hasCompletedOnboarding(uid: String): Boolean {
        return onboardingRepository.hasCompleted(uid)
    }

    suspend fun questionExists(questionId: String): Boolean {
        return onboardingRepository.questionExists(questionId)
    }

    suspend fun optionBelongsToQuestion(optionId: String, questionId: String): Boolean {
        return onboardingRepository.optionBelongsToQuestion(optionId, questionId)
    }
}
