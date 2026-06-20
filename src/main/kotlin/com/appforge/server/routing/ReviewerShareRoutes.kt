package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.sharing.CreateReviewerShareRequest
import com.appforge.server.api.sharing.SubmitReviewerShareReviewRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.services.sharing.ShareServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.entityReviewerShareRoutes(services: ShareServices) {
    val reviewerShareUseCases = services.reviewerShareUseCases

    post("/reviewer-shares") {
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
        val request = call.receive<CreateReviewerShareRequest>()
        withRouteSqlUserContext(ctx) {
            call.respond(HttpStatusCode.Created, reviewerShareUseCases.createReviewerShare(ctx.userId, type, entityId, request))
        }
    }

    get("/reviewer-shares") {
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
            call.respond(reviewerShareUseCases.listReviewerSharesForEntity(ctx.userId, type, entityId))
        }
    }
}

fun Route.reviewerShareManagementRoutes(services: ShareServices) {
    val reviewerShareUseCases = services.reviewerShareUseCases

    post("/{shareId}/revoke") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Reviewer share id is required."))
            return@post
        }
        withRouteSqlUserContext(ctx) {
            reviewerShareUseCases.revokeReviewerShare(ctx.userId, shareId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.reviewerInboxRoutes(services: ShareServices) {
    val reviewerShareUseCases = services.reviewerShareUseCases

    get("/shares") {
        val ctx = call.attributes[RequestContextKey]
        withRouteSqlUserContext(ctx) {
            call.respond(reviewerShareUseCases.listReviewerInbox(ctx.userId))
        }
    }

    get("/shares/{shareId}") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Reviewer share id is required."))
            return@get
        }
        withRouteSqlUserContext(ctx) {
            call.respond(reviewerShareUseCases.getReviewerShare(ctx.userId, shareId))
        }
    }

    get("/shares/{shareId}/review-template") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Reviewer share id is required."))
            return@get
        }
        withRouteSqlUserContext(ctx) {
            call.respond(reviewerShareUseCases.getReviewerShareReviewTemplate(ctx.userId, shareId))
        }
    }

    post("/shares/{shareId}/reviews") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Reviewer share id is required."))
            return@post
        }
        val request = call.receive<SubmitReviewerShareReviewRequest>()
        withRouteSqlUserContext(ctx) {
            call.respond(HttpStatusCode.Created, reviewerShareUseCases.submitReviewerShareReview(ctx.userId, shareId, request))
        }
    }
}
