package com.appforge.server.routing

import com.appforge.server.api.EarlyAccessApproveRequest
import com.appforge.server.api.ErrorResponse
import com.appforge.server.middleware.SecretAuthPlugin
import com.appforge.server.services.system.SystemServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.systemRoutes(services: SystemServices) {
    val runtimeOptions = services.runtimeOptions
    val logger = LoggerFactory.getLogger("SystemRoutes")
    val systemUseCases = services.systemUseCases

    route("/api/v1/system") {
        install(SecretAuthPlugin) {
            internalSecret = runtimeOptions.internalSecret
        }
        /**
         * Unified trigger endpoint for system events.
         * Handles dashboard sync and automated AI reviews.
         */
        post("/trigger") {
            val userId = call.request.queryParameters["userId"]
            if (userId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId query parameter is required."))
                return@post
            }
            logger.info("Received system trigger for user: $userId")
            call.respond(HttpStatusCode.OK, systemUseCases.trigger(userId))
        }

        /**
         * Internal admin-only endpoint to approve early access and send invite.
         */
        post("/early-access/approve") {
            logger.info("Received early access approve request")
            val request = call.receive<EarlyAccessApproveRequest>()
            logger.info("Early access approve payload received for email: {}", request.email)
            val response = systemUseCases.approveEarlyAccess(request)
            call.respond(response)
        }
    }
}
