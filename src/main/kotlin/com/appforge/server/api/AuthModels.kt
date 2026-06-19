package com.appforge.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionLoginRequest(
    val idToken: String,
)

@Serializable
data class SessionLoginResponse(
    val success: Boolean,
    val uid: String? = null,
)

@Serializable
data class SessionLogoutResponse(
    val success: Boolean,
    val uid: String? = null,
)

@Serializable
data class SessionMeResponse(
    val uid: String,
    val email: String? = null,
    val name: String? = null,
    val onboardingCompleted: Boolean = false,
)

@Serializable
data class SignupInitRequest(
    val idToken: String,
)

@Serializable
data class SignupInitResponse(
    val success: Boolean,
    val uid: String? = null,
)

@Serializable
data class ProtoTimestamp(
    val seconds: Long,
    val nanos: Int = 0,
)

@Serializable
data class OnboardingSubmitAnswerRequest(
    val questionId: String,
    val optionIds: List<String> = emptyList(),
    val textValue: String? = null,
)

@Serializable
data class OnboardingSubmitRequest(
    val answers: List<OnboardingSubmitAnswerRequest> = emptyList(),
    val completedAt: ProtoTimestamp? = null,
)

@Serializable
data class OnboardingSubmitResponse(
    val success: Boolean,
    val uid: String? = null,
)

@Serializable
data class EarlyAccessCheckRequest(
    val email: String,
)

@Serializable
data class EarlyAccessCheckResponse(
    val hasAccess: Boolean,
)

@Serializable
data class EarlyAccessJoinRequest(
    val email: String,
)

@Serializable
data class EarlyAccessJoinResponse(
    val success: Boolean,
)

@Serializable
data class EarlyAccessStatusResponse(
    val enabled: Boolean,
)

@Serializable
data class OnboardingFlowResponse(
    val id: String,
    val version: Int,
    val name: String,
    val steps: List<OnboardingFlowStepResponse>,
)

@Serializable
data class OnboardingFlowStepResponse(
    val type: OnboardingStepTypeDto,
    val title: String,
    val description: String,
    val ctaLabel: String,
    val fields: List<OnboardingFlowFieldResponse> = emptyList(),
)

@Serializable
data class OnboardingFlowFieldResponse(
    val id: String,
    val label: String,
    val type: OnboardingQuestionTypeDto,
    val options: List<OnboardingFlowOptionResponse> = emptyList(),
)

@Serializable
data class OnboardingFlowOptionResponse(
    val id: String,
    val label: String,
)

@Serializable
data class EarlyAccessApproveRequest(
    val email: String,
    val forceSend: Boolean = false,
)

@Serializable
data class EarlyAccessApproveResponse(
    val success: Boolean,
    val email: String? = null,
    val previousStatus: String? = null,
    val created: Boolean = false,
    val emailSent: Boolean = false,
)

@Serializable
data class UsageFeatureResponse(
    val used: Long,
    val limit: Long,
    val unlocked: Boolean,
)

@Serializable
data class UserPlanResponse(
    val name: UserPlanDto,
    val status: UserPlanStatusDto,
    val expiresAt: ProtoTimestamp? = null,
    val startedAt: ProtoTimestamp? = null,
    val source: UserPlanSourceDto? = null,
    val cancelAtPeriodEnd: Boolean,
    val checkoutUrl: String? = null,
)

@Serializable
data class UserUsageResponse(
    val reviewSubmissions: UsageFeatureResponse,
    val entityCreations: UsageFeatureResponse,
    val apiRequests: UsageFeatureResponse,
    val sharedLinks: UsageFeatureResponse,
    val storageBytes: UsageFeatureResponse,
)

@Serializable
data class UserProfileResponse(
    val uid: String,
    val email: String? = null,
    val name: String? = null,
    val createdAt: ProtoTimestamp? = null,
    val lastLoginAt: ProtoTimestamp? = null,
    val plan: UserPlanResponse? = null,
    val usage: UserUsageResponse? = null,
)

@Serializable
data class UpdateUserProfileRequest(
    val name: String,
)

@Serializable
data class UpdateUserProfileResponse(
    val success: Boolean,
)

@Serializable
data class PasswordResetLinkRequest(
    val email: String,
)

@Serializable
data class PasswordResetLinkResponse(
    val success: Boolean,
)

@Serializable
data class DeleteUserAccountResponse(
    val success: Boolean,
)

@Serializable
data class UserUsageBucketResponse(
    val windowStart: ProtoTimestamp,
    val count: Long,
)

@Serializable
data class UserUsageSeriesResponse(
    val metric: String,
    val total: Long,
    val buckets: List<UserUsageBucketResponse>,
)

@Serializable
data class UserUsageSummaryResponse(
    val granularity: String,
    val from: ProtoTimestamp? = null,
    val to: ProtoTimestamp? = null,
    val series: List<UserUsageSeriesResponse>,
)
@Serializable
enum class OnboardingStepTypeDto {
    @SerialName("onboarding")
    ONBOARDING,
    @SerialName("profile")
    PROFILE,
    @SerialName("personalization")
    PERSONALIZATION,
}

@Serializable
enum class OnboardingQuestionTypeDto {
    @SerialName("single_select")
    SINGLE_SELECT,
    @SerialName("multi_select")
    MULTI_SELECT,
    @SerialName("text")
    TEXT,
}

@Serializable
enum class UserPlanDto {
    @SerialName("free")
    FREE,
    @SerialName("trial")
    TRIAL,
    @SerialName("pro")
    PRO,
}

@Serializable
enum class UserPlanStatusDto {
    @SerialName("active")
    ACTIVE,
    @SerialName("trialing")
    TRIALING,
    @SerialName("cancel_pending")
    CANCEL_PENDING,
    @SerialName("past_due")
    PAST_DUE,
    @SerialName("canceled")
    CANCELED,
}

@Serializable
enum class UserPlanSourceDto {
    @SerialName("manual")
    MANUAL,
    @SerialName("trial")
    TRIAL,
    @SerialName("dodo_payments")
    DODO_PAYMENTS,
}
