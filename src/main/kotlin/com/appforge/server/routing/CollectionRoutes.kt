package com.appforge.server.routing

import com.appforge.server.api.CollectionCreateRequest
import com.appforge.server.api.CollectionUpdateRequest
import com.appforge.server.api.ErrorResponse
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.collections.CollectionServices
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

/**
 * Schemaless JSON document store — one endpoint for any app-defined collection.
 *
 * ## What this is
 *
 * A convenience API backed by a single JSONB table (`custom_collections`).
 * Any authenticated client can store and retrieve arbitrary JSON objects under
 * any collection name without any server-side code changes. Records are isolated
 * by `X-App-Id` and the authenticated user.
 *
 * ## What this is NOT
 *
 * This is not the primary storage layer. All platform features — auth, billing,
 * sharing, tasks, recordings, documents — use typed, normalized SQL tables with
 * explicit schemas, foreign keys, and constraints. Those tables are the right
 * choice whenever the data has a known structure.
 *
 * Use collections when:
 * - The data shape is determined by the client app, not the server
 * - You need a quick store without writing a new typed repository
 * - The data is simple enough that no joins or constraints are required
 *
 * Use a typed [AppPlugin] repository instead when:
 * - The data has a fixed schema that benefits from constraints or foreign keys
 * - You need JOIN queries across tables
 * - You need server-side validation beyond "is this valid JSON?"
 *
 * ## Endpoints
 *
 * | Method | Path                                    | Description         |
 * |--------|-----------------------------------------|---------------------|
 * | POST   | /api/v1/collections/{collection}        | Create a record     |
 * | GET    | /api/v1/collections/{collection}        | List records        |
 * | GET    | /api/v1/collections/{collection}/{id}   | Get one record      |
 * | PATCH  | /api/v1/collections/{collection}/{id}   | Replace record data |
 * | DELETE | /api/v1/collections/{collection}/{id}   | Delete a record     |
 *
 * ## Collection naming
 *
 * Names must be 1–64 characters: letters, digits, hyphens, underscores.
 * Different collection names within the same `X-App-Id` are independent stores.
 *
 * ## Example
 *
 * ```
 * POST /api/v1/collections/expenses
 * X-App-Id: budget-app
 * Authorization: Bearer <token>
 * Content-Type: application/json
 *
 * { "data": { "amount": 42.50, "category": "travel", "note": "Uber to airport" } }
 * ```
 *
 * Response (`201 Created`):
 * ```json
 * {
 *   "id": "550e8400-...",
 *   "collection": "expenses",
 *   "data": { "amount": 42.50, "category": "travel", "note": "Uber to airport" },
 *   "createdAt": { "seconds": 1750000000, "nanos": 0 },
 *   "updatedAt": { "seconds": 1750000000, "nanos": 0 }
 * }
 * ```
 */
fun Route.collectionRoutes(services: CollectionServices) {
    route("/api/v1/collections/{collection}") {
        install(UserAuthPlugin) {
            this.authService = services.authService
            this.requestIdentityProvider = services.requestIdentityProvider
        }

        post {
            val ctx = call.attributes[RequestContextKey]
            val collection = call.parameters["collection"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("collection name is required."))
            val request = call.receive<CollectionCreateRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.collectionService.create(ctx.userId, ctx.appId, collection, request)
            }
            call.respondCollection(result, createdOnOk = true)
        }

        get {
            val ctx = call.attributes[RequestContextKey]
            val collection = call.parameters["collection"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("collection name is required."))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val result = withRouteSqlUserContext(ctx) {
                services.collectionService.list(ctx.userId, ctx.appId, collection, limit)
            }
            call.respondCollection(result, createdOnOk = false)
        }

        get("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val collection = call.parameters["collection"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("collection name is required."))
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required."))
            val result = withRouteSqlUserContext(ctx) {
                services.collectionService.get(ctx.userId, ctx.appId, collection, id)
            }
            call.respondCollection(result, createdOnOk = false)
        }

        patch("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val collection = call.parameters["collection"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("collection name is required."))
            val id = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required."))
            val request = call.receive<CollectionUpdateRequest>()
            val result = withRouteSqlUserContext(ctx) {
                services.collectionService.update(ctx.userId, ctx.appId, collection, id, request)
            }
            call.respondCollection(result, createdOnOk = false)
        }

        delete("/{id}") {
            val ctx = call.attributes[RequestContextKey]
            val collection = call.parameters["collection"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("collection name is required."))
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required."))
            val result = withRouteSqlUserContext(ctx) {
                services.collectionService.delete(ctx.userId, ctx.appId, collection, id)
            }
            call.respondCollection(result, createdOnOk = false)
        }
    }
}

private suspend fun <T> io.ktor.server.application.ApplicationCall.respondCollection(
    result: AuthResponse<T>,
    createdOnOk: Boolean,
) {
    when (result) {
        is AuthResponse.Ok -> respond(if (createdOnOk) HttpStatusCode.Created else HttpStatusCode.OK, result.data as Any)
        is AuthResponse.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
        is AuthResponse.Forbidden -> respond(HttpStatusCode.NotFound, ErrorResponse(result.message))
        is AuthResponse.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
    }
}
