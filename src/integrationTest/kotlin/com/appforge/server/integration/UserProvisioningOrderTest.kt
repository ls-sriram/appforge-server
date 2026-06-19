package com.appforge.server.integration

import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.services.auth.UserLifecycleCoordinator
import com.appforge.server.services.auth.repository.AppUserRecord
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.services.onboarding.repository.OnboardingQuestionOptionRecord
import com.appforge.server.services.onboarding.repository.OnboardingQuestionRecord
import com.appforge.server.services.onboarding.repository.OnboardingRepositoryApi
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserProvisioningOrderTest {

    @Test
    fun `ensureUserCreated writes app user before profile then onboarding state`() {
        val callOrder = mutableListOf<String>()
        val repository = object : UserRepositoryApi {
            override suspend fun upsertUser(uid: String, email: String, displayName: String?, lastLoginAt: Instant) {
                callOrder += "upsertUser"
            }

            override suspend fun upsertProfile(uid: String, email: String, displayName: String?, lastSeenAt: Instant) {
                callOrder += "upsertProfile"
            }

            override suspend fun getUser(uid: String): AppUserRecord? = null

            override suspend fun updateDisplayName(uid: String, displayName: String, updatedAt: Instant): Boolean = false

            override suspend fun deleteUserAccount(uid: String): Boolean = false
        }

        val onboardingRepository = object : OnboardingRepositoryApi {
            override suspend fun initializeState(userId: String, now: Instant, version: Int) {
                callOrder += "initializeState"
            }

            override suspend fun replaceAnswers(
                userId: String,
                questionId: String,
                optionIds: List<String>,
                textValue: String?,
                now: Instant,
            ) = Unit

            override suspend fun markCompleted(userId: String, completedAt: Instant, version: Int) = Unit

            override suspend fun hasCompleted(userId: String): Boolean = false

            override suspend fun listActiveQuestions(): List<OnboardingQuestionRecord> = emptyList()

            override suspend fun listActiveQuestionOptions(): List<OnboardingQuestionOptionRecord> = emptyList()

            override suspend fun questionExists(questionId: String): Boolean = false

            override suspend fun optionBelongsToQuestion(optionId: String, questionId: String): Boolean = false
        }

        val coordinator = UserLifecycleCoordinator(
            repository = repository,
            onboardingRepository = onboardingRepository,
            timestampProvider = object : TimestampProvider {
                override fun now(): Instant = Instant.parse("2026-01-01T00:00:00Z")
            }
        )

        kotlinx.coroutines.runBlocking {
            coordinator.ensureUserCreated(
                uid = "uid-1",
                email = "u@test.com",
                displayName = "User",
            )
        }

        assertEquals(listOf("upsertUser", "upsertProfile", "initializeState"), callOrder)
    }
}
