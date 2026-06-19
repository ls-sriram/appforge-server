package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.UploadCompleteRequest
import com.appforge.server.api.UploadCompleteResponse
import com.appforge.server.services.uploads.UploadCompletionRequest
import com.appforge.server.services.uploads.UploadServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.uploadEventRoutes(services: UploadServices) {
    route("/api/v1/upload-events") {
        post("/complete") {
            val secret = call.request.header("X-Upload-Event-Secret")
            if (secret != services.uploadEventSharedSecret) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@post
            }

            val req = call.receive<UploadCompleteRequest>()
            val result = services.uploadUseCases.completeUpload(
                UploadCompletionRequest(
                    bucket = req.bucket,
                    objectName = req.objectName,
                    generation = req.generation,
                    sizeBytes = req.sizeBytes,
                    contentType = req.contentType,
                    eventTimeEpochSeconds = req.eventTimeEpochSeconds,
                )
            )

            call.respond(HttpStatusCode.OK, UploadCompleteResponse(success = result.processed))
        }
    }
}
