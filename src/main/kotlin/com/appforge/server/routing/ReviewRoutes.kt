package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.reviews.ReviewServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.reviewRoutes(services: ReviewServices) {
    val reviewUseCases = services.reviewUseCases

    route("/api/v1/reviews") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }
        get {
            val ctx = call.attributes[RequestContextKey]
            withRouteSqlUserContext(ctx) {
                call.respond(reviewUseCases.listAllReviews(ctx.userId))
            }
        }
    }
}

fun Route.entityReviewRoutes(services: ReviewServices) {
    val reviewUseCases = services.reviewUseCases

    get("/reviews") {
        val ctx = call.attributes[RequestContextKey]
        val type = call.parameters["type"]
        if (type.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Entity type is required."))
            return@get
        }
        val entityId = call.parameters["id"]
        if (entityId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Entity id is required."))
            return@get
        }
        withRouteSqlUserContext(ctx) {
            call.respond(reviewUseCases.getReviews(ctx.userId, type, entityId))
        }
    }

    post("/ai-review") {
        val ctx = call.attributes[RequestContextKey]
        val type = call.parameters["type"]
        if (type.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Entity type is required."))
            return@post
        }
        val entityId = call.parameters["id"]
        if (entityId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Entity id is required."))
            return@post
        }
        val versionId = call.request.queryParameters["versionId"]
        withRouteSqlUserContext(ctx) {
            reviewUseCases.requestAiReview(ctx.userId, type, entityId, versionId)
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "AI Review Enqueued", "versionId" to versionId))
        }
    }
}
