package com.appforge.server.services.useraccount

import com.appforge.server.api.DeleteUserAccountResponse
import com.appforge.server.api.UpdateUserProfileRequest
import com.appforge.server.api.UpdateUserProfileResponse
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.repository.UserRepositoryApi

interface UserAccountService {
    suspend fun updateUserProfile(userId: String, request: UpdateUserProfileRequest): AuthResponse<UpdateUserProfileResponse>
    suspend fun deleteUserAccount(userId: String): AuthResponse<DeleteUserAccountResponse>
}

class UserAccountServiceImpl(
    private val userRepository: UserRepositoryApi,
    private val accountDeletionService: AccountDeletionService,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) : UserAccountService {
    override suspend fun updateUserProfile(
        userId: String,
        request: UpdateUserProfileRequest
    ): AuthResponse<UpdateUserProfileResponse> {
        val trimmed = request.name.trim()
        if (trimmed.isBlank()) return AuthResponse.BadRequest("Name cannot be empty.")
        val updated = userRepository.updateDisplayName(
            uid = userId,
            displayName = trimmed,
            updatedAt = timestampProvider.now(),
        )
        if (!updated) return AuthResponse.BadRequest("User profile not found.")
        return AuthResponse.Ok(UpdateUserProfileResponse(success = true))
    }

    override suspend fun deleteUserAccount(userId: String): AuthResponse<DeleteUserAccountResponse> {
        val result = accountDeletionService.deleteAccount(userId)
        return if (result.success) {
            AuthResponse.Ok(DeleteUserAccountResponse(success = true))
        } else {
            AuthResponse.BadRequest("Delete account failed.")
        }
    }
}
