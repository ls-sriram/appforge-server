package com.appforge.server.middleware

import com.appforge.server.api.ErrorResponse
import com.appforge.server.services.uploads.ForbiddenUploadException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.application.call
import io.ktor.server.application.log

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<GoneException> { call, cause ->
            call.respond(HttpStatusCode.Gone, ErrorResponse(cause.message ?: "Gone"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Forbidden"))
        }
        exception<ForbiddenUploadException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Forbidden"))
        }
        exception<UnauthorizedException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Request failed: ${cause.message}", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.application.log.error("Server error: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Server error")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Server error")
            )
        }
    }
}
