package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.services.sharing.ShareServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.entityShareCollectionRoutes(services: ShareServices) {
    val shareUseCases = services.shareUseCases

    get("/shares") {
        val ctx = call.attributes[RequestContextKey]
        withRouteSqlUserContext(ctx) {
            call.respond(shareUseCases.listOwnerShares(ctx.userId))
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
}
