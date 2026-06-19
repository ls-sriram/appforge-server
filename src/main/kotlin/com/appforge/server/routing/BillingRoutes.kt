package com.appforge.server.routing

import com.appforge.server.api.CheckoutRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.billing.BillingServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.net.URI

fun Route.billingRoutes(services: BillingServices) {
    val billingUseCases = services.billingUseCases

    route("/api/v1/billing") {
        get("/pricing-cards") {
            call.application.log.info("Received GET /billing/pricing-cards")
            call.respond(billingUseCases.listPricingCards())
        }

        route("/entitlement") {
            install(UserAuthPlugin) {
                this.authService = services.authService
                this.requestIdentityProvider = services.requestIdentityProvider
            }
            get {
                call.application.log.info("Received GET /billing/entitlement")
                val ctx = call.attributes[RequestContextKey]
                withRouteSqlUserContext(ctx) {
                    call.respond(billingUseCases.entitlement(ctx.userId))
                }
            }
        }

        route("/checkout") {
            install(UserAuthPlugin) {
                this.authService = services.authService
                this.requestIdentityProvider = services.requestIdentityProvider
            }
            post {
                call.application.log.info("Received POST /billing/checkout")
                val ctx = call.attributes[RequestContextKey]

                val request = call.receive<CheckoutRequest>()
                val origin = resolveCheckoutOrigin(
                    originHeader = call.request.header("Origin"),
                    refererHeader = call.request.header("Referer"),
                )
                withRouteSqlUserContext(ctx) {
                    call.respond(billingUseCases.checkout(ctx.userId, request, origin))
                }
            }
        }

        route("/subscription") {
            install(UserAuthPlugin) {
                this.authService = services.authService
                this.requestIdentityProvider = services.requestIdentityProvider
            }
            post("/cancel") {
                call.application.log.info("Received POST /billing/subscription/cancel")
                val ctx = call.attributes[RequestContextKey]
                withRouteSqlUserContext(ctx) {
                    billingUseCases.cancelSubscription(ctx.userId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        route("/webhook") {
            post("/dodo") {
                val webhookId = call.request.header("webhook-id")
                val timestamp = call.request.header("webhook-timestamp")
                val payload = call.receiveText()

                val signature = call.request.header("webhook-signature")
                call.respond(billingUseCases.handleWebhook(payload, signature, webhookId, timestamp))
            }
        }
    }
}

private fun resolveCheckoutOrigin(originHeader: String?, refererHeader: String?): String {
    originHeader?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

    val referer = refererHeader?.trim().orEmpty()
    if (referer.isNotBlank()) {
        runCatching {
            val uri = URI(referer)
            if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                val port = if (uri.port > 0) ":${uri.port}" else ""
                return "${uri.scheme}://${uri.host}$port"
            }
        }
    }

    return "http://localhost:3000"
}
