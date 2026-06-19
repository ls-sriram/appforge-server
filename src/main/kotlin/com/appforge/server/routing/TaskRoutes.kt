package com.appforge.server.routing

import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.TaskCompleteRequest
import com.appforge.server.api.TaskCreateRequest
import com.appforge.server.api.TaskUpdateRequest
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.tasks.TaskServices
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.taskRoutes(services: TaskServices) {
    route("/api/v1/tasks") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }

        post {
            val ctx = call.attributes[RequestContextKey]
            val request = call.receive<TaskCreateRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.create(ctx.userId, request)
            }
            call.respondTask(result, createdOnOk = true)
        }

        get {
            val ctx = call.attributes[RequestContextKey]
            val status = call.request.queryParameters["status"]
            val type = call.request.queryParameters["type"]
            val tag = call.request.queryParameters["tag"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.list(ctx.userId, status, type, tag, limit)
            }
            call.respondTask(result, createdOnOk = false)
        }

        get("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Task id is required."))
                return@get
            }
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.get(ctx.userId, id)
            }
            call.respondTask(result, createdOnOk = false)
        }

        patch("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Task id is required."))
                return@patch
            }
            val request = call.receive<TaskUpdateRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.update(ctx.userId, id, request)
            }
            call.respondTask(result, createdOnOk = false)
        }

        post("/{id}/complete") {
            val ctx = call.attributes[RequestContextKey]
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Task id is required."))
                return@post
            }
            val request = runCatching { call.receive<TaskCompleteRequest>() }.getOrDefault(TaskCompleteRequest())
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.complete(ctx.userId, id, request)
            }
            call.respondTask(result, createdOnOk = false)
        }

        post("/{id}/reopen") {
            val ctx = call.attributes[RequestContextKey]
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Task id is required."))
                return@post
            }
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.reopen(ctx.userId, id)
            }
            call.respondTask(result, createdOnOk = false)
        }

        delete("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Task id is required."))
                return@delete
            }
            val result = withRouteSqlUserContext(ctx) {
                services.taskService.delete(ctx.userId, id)
            }
            call.respondTask(result, createdOnOk = false)
        }
    }
}

private suspend fun <T> io.ktor.server.application.ApplicationCall.respondTask(
    result: AuthResponse<T>,
    createdOnOk: Boolean,
) {
    when (result) {
        is AuthResponse.Ok -> {
            val status = if (createdOnOk) HttpStatusCode.Created else HttpStatusCode.OK
            respond(status, result.data as Any)
        }
        is AuthResponse.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
        is AuthResponse.Forbidden -> respond(HttpStatusCode.NotFound, ErrorResponse(result.message))
        is AuthResponse.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
    }
}
