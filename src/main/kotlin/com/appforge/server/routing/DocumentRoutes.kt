package com.appforge.server.routing

import com.appforge.server.api.DocumentSaveRequest
import com.appforge.server.api.ErrorResponse
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.documents.DocumentServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.documentRoutes(services: DocumentServices) {
    route("/api/v1/documents") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }

        post {
            val ctx = call.attributes[RequestContextKey]
            val request = call.receive<DocumentSaveRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.documentService.save(ctx.userId, request)
            }
            call.respondDocument(result, createdOnOk = true)
        }

        get {
            val ctx = call.attributes[RequestContextKey]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val result = withRouteSqlUserContext(ctx) {
                services.documentService.list(ctx.userId, limit)
            }
            call.respondDocument(result, createdOnOk = false)
        }
    }
}

private suspend fun <T> io.ktor.server.application.ApplicationCall.respondDocument(
    result: AuthResponse<T>,
    createdOnOk: Boolean,
) {
    when (result) {
        is AuthResponse.Ok -> {
            val status = if (createdOnOk) HttpStatusCode.Created else HttpStatusCode.OK
            respond(status, result.data as Any)
        }
        is AuthResponse.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
        is AuthResponse.Forbidden -> respond(HttpStatusCode.Forbidden, ErrorResponse(result.message))
        is AuthResponse.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
    }
}
