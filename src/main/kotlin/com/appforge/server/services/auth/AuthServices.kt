package com.appforge.server.services.auth

import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.onboarding.OnboardingFlowUseCases
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.userprofile.UserProfileService
import com.appforge.server.services.usage.UsageMetricsService

interface AuthServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val loginService: LoginService
    val registrationService: RegistrationService
    val onboardingQaService: OnboardingQaService
    val userProfileService: UserProfileService
    val userAccountService: UserAccountService
    val usageMetricsService: UsageMetricsService
    val earlyAccessAppService: EarlyAccessAppService
    val onboardingFlowUseCases: OnboardingFlowUseCases
    val runtimeOptions: RuntimeOptions
}
