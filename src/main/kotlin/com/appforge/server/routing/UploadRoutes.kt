package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.UploadInitRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.uploads.UploadServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import org.slf4j.LoggerFactory

fun Route.uploadRoutes(services: UploadServices) {
    val uploadUseCases = services.uploadUseCases

    route("/api/v1/uploads") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }
        post("/init") {
            val ctx = call.attributes[RequestContextKey]

            val req = call.receive<UploadInitRequest>()
            withRouteSqlUserContext(ctx) {
                val result = uploadUseCases.initUpload(ctx.userId, req)
                call.respond(HttpStatusCode.OK, result)
            }
        }

        get("/access/{assetId}") {
            val logger = LoggerFactory.getLogger("UploadRoutes.access")
            val ctx = call.attributes[RequestContextKey]
            val assetId = call.parameters["assetId"]
            if (assetId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("assetId path parameter is required."))
                return@get
            }

            val redirectRaw = call.request.queryParameters["redirect"]
            val redirect = redirectRaw?.toBooleanStrictOrNull() ?: true
            if (redirectRaw != null && redirectRaw.toBooleanStrictOrNull() == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("redirect query parameter must be 'true' or 'false'."))
                return@get
            }

            logger.debug("Access request for asset $assetId from user ${ctx.userId}, redirect=$redirect")
            val accessUrl = withRouteSqlUserContext(ctx) {
                uploadUseCases.getAccessUrl(ctx.userId, assetId)
            }

            if (accessUrl != null) {
                if (redirect) {
                    logger.debug("Access GRANTED for asset $assetId. Redirecting to signed URL.")
                    call.respondRedirect(accessUrl)
                } else {
                    logger.debug("Access GRANTED for asset $assetId. Returning URL as JSON.")
                    call.respond(mapOf("url" to accessUrl))
                }
            } else {
                logger.warn("Access DENIED or NOT FOUND for asset $assetId (user: ${ctx.userId}). Either record missing or user mismatch.")
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Access URL not found or expired"))
            }
        }
    }
}
