package com.appforge.server.services.billing

import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.billing.BillingCoordinator
import com.appforge.server.services.dodopayments.DodoPaymentsService

interface BillingServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val billingUseCases: BillingUseCases
}
