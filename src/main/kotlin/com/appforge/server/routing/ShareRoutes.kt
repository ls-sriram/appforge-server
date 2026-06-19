package com.appforge.server.routing

import com.appforge.server.api.sharing.CreateShareRequest
import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.sharing.SendShareEmailRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.services.sharing.ShareServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.entityShareRoutes(services: ShareServices) {
    val shareUseCases = services.shareUseCases

    post("/shares") {
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
        val req = call.receive<CreateShareRequest>()
        withRouteSqlUserContext(ctx) {
            call.respond(HttpStatusCode.Created, shareUseCases.createShare(ctx.userId, type, entityId, req))
        }
    }

    get("/shares") {
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
            call.respond(shareUseCases.listShares(ctx.userId, type, entityId))
        }
    }

    post("/shares/{token}/revoke") {
        val ctx = call.attributes[RequestContextKey]
        val token = call.parameters["token"]
        if (token.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
            return@post
        }
        withRouteSqlUserContext(ctx) {
            shareUseCases.revokeShare(ctx.userId, token)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/shares/{token}/email") {
        val ctx = call.attributes[RequestContextKey]
        val token = call.parameters["token"]
        if (token.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
            return@post
        }
        val request = call.receive<SendShareEmailRequest>()
        withRouteSqlUserContext(ctx) {
            shareUseCases.sendShareEmail(ctx.userId, token, request)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
