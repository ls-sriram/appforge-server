package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.RecordingCreateRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.RecordingServices
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.recordingRoutes(services: RecordingServices) {
    route("/api/v1/recordings") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }

        post {
            val ctx = call.attributes[RequestContextKey]
            val request = call.receive<RecordingCreateRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.recordingService.create(ctx.userId, request)
            }
            call.respondRecording(result)
        }

        get {
            val ctx = call.attributes[RequestContextKey]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val result = withRouteSqlUserContext(ctx) {
                services.recordingService.list(ctx.userId, limit)
            }
            call.respondRecording(result)
        }

        get("/{id}/content") {
            val ctx = call.attributes[RequestContextKey]
            val recordingId = call.parameters["id"]
            if (recordingId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Recording id is required."))
                return@get
            }
            val result = withRouteSqlUserContext(ctx) {
                services.recordingService.content(ctx.userId, recordingId)
            }
            when (result) {
                is AuthResponse.Ok -> call.respondRecordingContent(result.data)
                is AuthResponse.Unauthorized -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
                is AuthResponse.Forbidden -> call.respond(HttpStatusCode.NotFound, ErrorResponse(result.message))
                is AuthResponse.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            }
        }
    }
}

private suspend fun <T> io.ktor.server.application.ApplicationCall.respondRecording(
    result: AuthResponse<T>,
) {
    when (result) {
        is AuthResponse.Ok -> respond(HttpStatusCode.OK, result.data as Any)
        is AuthResponse.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
        is AuthResponse.Forbidden -> respond(HttpStatusCode.Forbidden, ErrorResponse(result.message))
        is AuthResponse.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondRecordingContent(content: RecordingContent) {
    respondBytes(
        bytes = content.audioBytes,
        contentType = ContentType.parse(content.metadata.contentType),
        status = HttpStatusCode.OK,
    )
}
