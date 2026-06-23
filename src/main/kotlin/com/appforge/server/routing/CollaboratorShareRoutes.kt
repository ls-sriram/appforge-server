package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.sharing.CreateCollaboratorShareRequest
import com.appforge.server.api.sharing.SubmitCollaboratorReviewRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.services.sharing.ShareServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.entityCollaboratorShareRoutes(services: ShareServices) {
    val collaboratorShareUseCases = services.collaboratorShareUseCases

    post("/collaborator-shares") {
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
        val request = call.receive<CreateCollaboratorShareRequest>()
        withRouteSqlUserContext(ctx) {
            call.respond(HttpStatusCode.Created, collaboratorShareUseCases.createCollaboratorShare(ctx.userId, type, entityId, request))
        }
    }

    get("/collaborator-shares") {
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
            call.respond(collaboratorShareUseCases.listCollaboratorSharesForEntity(ctx.userId, type, entityId))
        }
    }
}

fun Route.collaboratorShareManagementRoutes(services: ShareServices) {
    val collaboratorShareUseCases = services.collaboratorShareUseCases

    post("/{shareId}/revoke") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Collaborator share id is required."))
            return@post
        }
        withRouteSqlUserContext(ctx) {
            collaboratorShareUseCases.revokeCollaboratorShare(ctx.userId, shareId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.collaboratorInboxRoutes(services: ShareServices) {
    val collaboratorShareUseCases = services.collaboratorShareUseCases

    get("/shares") {
        val ctx = call.attributes[RequestContextKey]
        withRouteSqlUserContext(ctx) {
            call.respond(collaboratorShareUseCases.listCollaboratorInbox(ctx.userId))
        }
    }

    get("/shares/{shareId}") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Collaborator share id is required."))
            return@get
        }
        withRouteSqlUserContext(ctx) {
            call.respond(collaboratorShareUseCases.getCollaboratorShare(ctx.userId, shareId))
        }
    }

    get("/shares/{shareId}/review-template") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Collaborator share id is required."))
            return@get
        }
        withRouteSqlUserContext(ctx) {
            call.respond(collaboratorShareUseCases.getCollaboratorReviewTemplate(ctx.userId, shareId))
        }
    }

    post("/shares/{shareId}/reviews") {
        val ctx = call.attributes[RequestContextKey]
        val shareId = call.parameters["shareId"]
        if (shareId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Collaborator share id is required."))
            return@post
        }
        val request = call.receive<SubmitCollaboratorReviewRequest>()
        withRouteSqlUserContext(ctx) {
            call.respond(HttpStatusCode.Created, collaboratorShareUseCases.submitCollaboratorReview(ctx.userId, shareId, request))
        }
    }
}
