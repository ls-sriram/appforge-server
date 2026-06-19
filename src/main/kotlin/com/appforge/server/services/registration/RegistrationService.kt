package com.appforge.server.services.registration

import com.appforge.server.api.SignupInitRequest
import com.appforge.server.api.SignupInitResponse
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.IdentityProviderUserResolver
import com.appforge.server.services.auth.UserLifecycleCoordinator
import com.appforge.server.services.billing.SignupEntitlementCoordinator
import com.appforge.server.services.earlyaccess.EarlyAccessDecision
import com.appforge.server.services.earlyaccess.EarlyAccessService

interface RegistrationService {
    suspend fun signupInit(request: SignupInitRequest): AuthResponse<SignupInitResponse>
}

class RegistrationServiceImpl(
    private val authService: AuthService,
    private val earlyAccessService: EarlyAccessService,
    private val signupEntitlementCoordinator: SignupEntitlementCoordinator,
    private val userLifecycleCoordinator: UserLifecycleCoordinator,
    private val identityResolver: IdentityProviderUserResolver = IdentityProviderUserResolver(authService),
) : RegistrationService {
    override suspend fun signupInit(request: SignupInitRequest): AuthResponse<SignupInitResponse> {
        val identity = when (val resolved = identityResolver.fromIdToken(request.idToken)) {
            is AuthResponse.Ok -> resolved.data
            is AuthResponse.Unauthorized -> return resolved
            is AuthResponse.Forbidden -> return AuthResponse.Unauthorized()
            is AuthResponse.BadRequest -> return AuthResponse.Unauthorized()
        }
        val alreadyHasAccount = userLifecycleCoordinator.hasExistingAccount(identity.uid)
        if (!alreadyHasAccount) {
            when (val decision = earlyAccessService.enforceLoginAccess(identity.email)) {
                is EarlyAccessDecision.Allowed -> Unit
                is EarlyAccessDecision.Blocked -> return AuthResponse.Forbidden(decision.message)
            }
        }
        earlyAccessService.autoApproveWhenOpen(identity.email)
        userLifecycleCoordinator.ensureUserCreated(
            uid = identity.uid,
            email = identity.email,
            displayName = identity.name,
        )
        signupEntitlementCoordinator.initializeDefaultEntitlement(identity.uid)
        return AuthResponse.Ok(SignupInitResponse(success = true, uid = identity.uid))
    }
}
