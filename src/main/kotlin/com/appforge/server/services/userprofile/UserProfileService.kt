package com.appforge.server.services.userprofile

import com.appforge.server.api.UserProfileResponse
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.services.billing.BillingAccountService
import com.appforge.server.services.billing.EntitlementService

interface UserProfileService {
    suspend fun userProfile(userId: String): UserProfileResponse
}

class UserProfileServiceImpl(
    private val userRepository: UserRepositoryApi,
    private val billingAccountService: BillingAccountService,
    private val entitlementService: EntitlementService,
) : UserProfileService {
    override suspend fun userProfile(userId: String): UserProfileResponse {
        val user = userRepository.getUser(userId)
        val entitlement = billingAccountService.getEntitlement(userId)
        return UserProfileResponse(
            uid = userId,
            email = user?.email,
            name = user?.displayName,
            createdAt = instantToProtoTimestamp(user?.createdAt),
            lastLoginAt = instantToProtoTimestamp(user?.lastLoginAt),
            plan = entitlementService.toPlan(entitlement),
            usage = entitlementService.toUsage(entitlement),
        )
    }
}

