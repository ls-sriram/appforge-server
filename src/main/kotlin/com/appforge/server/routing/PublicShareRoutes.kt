package com.appforge.server.routing

import com.appforge.server.api.reviews.SubmitReviewRequest
import com.appforge.server.api.ErrorResponse
import io.ktor.http.ContentType
import com.appforge.server.services.sharing.PublicShareServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.publicShareRoutes(services: PublicShareServices) {
    val publicShareUseCases = services.publicShareUseCases

    route("/shares/{token}") {
        get {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@get
            }
            val response = publicShareUseCases.getPublicShare(token)
            call.respond(response)
        }

        post("/reviews") {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@post
            }
            val request = call.receive<SubmitReviewRequest>()
            val response = publicShareUseCases.submitReview(token, request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/recording/content") {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@get
            }
            val content = publicShareUseCases.getSharedRecordingContent(token)
            call.respondBytes(
                bytes = content.audioBytes,
                contentType = ContentType.parse(content.metadata.contentType),
                status = HttpStatusCode.OK,
            )
        }

        // Compatibility alias for generic clients that don't hardcode entity-specific URLs.
        get("/content") {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@get
            }
            val content = publicShareUseCases.getSharedRecordingContent(token)
            call.respondBytes(
                bytes = content.audioBytes,
                contentType = ContentType.parse(content.metadata.contentType),
                status = HttpStatusCode.OK,
            )
        }
    }

    route("/reviews/{token}") {
        get {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@get
            }
            val response = publicShareUseCases.getReviewTemplate(token)
            call.respond(response)
        }

        post {
            val token = call.parameters["token"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Share token is required."))
                return@post
            }
            val request = call.receive<SubmitReviewRequest>()
            val response = publicShareUseCases.submitReview(token, request)
            call.respond(HttpStatusCode.Created, response)
        }
    }
}
