package com.appforge.server.extensions

import com.appforge.server.infrastructure.Database
import com.appforge.server.infrastructure.sql.SqlRequestContext
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext

/**
 * Convenience base class for [PlatformExtension] implementations.
 *
 * Eliminates boilerplate by handling service injection, route mounting, and
 * table DDL delegation. Subclasses only declare tables and routes.
 *
 * All routes mount automatically under `/api/v1/{appId}/`.
 *
 * ## Minimal example
 *
 * ```kotlin
 * class ExpensesPlugin : AppPlugin(appId = "expenses-app") {
 *
 *     override val tables = listOf("""
 *         CREATE TABLE IF NOT EXISTS expenses (
 *             id           VARCHAR(255) PRIMARY KEY,
 *             owner_uid    VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
 *             amount_cents INTEGER      NOT NULL,
 *             category     VARCHAR(64)  NOT NULL,
 *             created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
 *         )
 *     """)
 *
 *     override fun routes(api: PluginRouter) {
 *         api.authenticated {
 *             post("/expenses") { ctx ->
 *                 val body = call.receive<CreateExpenseRequest>()
 *                 val rows = ctx.db.rawQuery(
 *                     "INSERT INTO expenses (id, owner_uid, amount_cents, category) VALUES (?, ?, ?, ?) RETURNING *",
 *                     listOf(UUID.randomUUID().toString(), ctx.userId, body.amountCents, body.category)
 *                 ).getOrThrow()
 *                 call.respond(HttpStatusCode.Created, rows.first())
 *             }
 *
 *             get("/expenses") { ctx ->
 *                 val rows = ctx.db.rawQuery(
 *                     "SELECT e.*, u.storage_key AS receipt_url " +
 *                     "FROM expenses e LEFT JOIN uploads u ON u.id = e.receipt_id " +
 *                     "WHERE e.owner_uid = ? ORDER BY e.created_at DESC",
 *                     listOf(ctx.userId)
 *                 ).getOrThrow()
 *                 call.respond(rows)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## Registration
 *
 * **Code-share model** — each app deploys its own binary, the plugin is
 * registered directly at startup:
 * ```kotlin
 * ClientRegistry.registerExtension(ExpensesPlugin())
 * ```
 *
 * **Runtime-share model** — one binary serves multiple `X-App-Id` values,
 * plugins are discovered automatically via Java ServiceLoader. Add the fully
 * qualified class name to:
 * ```
 * META-INF/services/com.appforge.server.extensions.PlatformExtension
 * ```
 */
abstract class AppPlugin(final override val appId: String) : PlatformExtension {

    private lateinit var _services: PlatformServices

    /** Platform database — available after [onPluginInitialize] and in all route handlers. */
    protected val db: Database get() = _services.database

    /** Auth service — available after [onPluginInitialize]. */
    protected val auth: AuthService get() = _services.authService

    /** Hook engine — available after [onPluginInitialize]. Fire custom events or subscribe to platform hooks. */
    protected val hooks: HookEngine get() = _services.hookEngine

    /**
     * SQL DDL statements to create this plugin's tables.
     *
     * - Use `CREATE TABLE IF NOT EXISTS` — executed once at server startup.
     * - Foreign keys to platform tables are supported:
     *   ```sql
     *   owner_uid VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE
     *   ```
     * - Tables are created after Flyway migrations, so all platform tables exist.
     */
    abstract val tables: List<String>

    /**
     * Declare HTTP routes using the [PluginRouter] DSL.
     *
     * Called once during server startup after [onPluginInitialize].
     */
    abstract fun routes(api: PluginRouter)

    /**
     * Called after platform services are injected, before routes are registered.
     *
     * Override for one-time setup: validating config, warming caches, seeding data.
     * [db], [auth], and [hooks] are available here.
     */
    open fun onPluginInitialize() {}

    // ─── PlatformExtension wiring (final — subclasses use the abstract API above) ─

    final override fun defineTables(): List<String> = tables

    final override fun onInitialize(services: PlatformServices) {
        _services = services
        onPluginInitialize()
    }

    final override fun registerRoutes(routing: Routing, services: PlatformServices) {
        PluginRouter(routing, appId, services).also { routes(it) }
    }
}

/**
 * Route registration DSL passed to [AppPlugin.routes].
 *
 * - [authenticated] — routes that require a valid Firebase session. Handlers receive
 *   a [PluginContext] with the resolved user ID and a reference to the database.
 *   The SQL user context is set automatically, so Row Level Security policies fire.
 * - [public] — unauthenticated routes, raw Ktor [Route] DSL.
 *
 * All routes mount under `/api/v1/{appId}/`.
 */
class PluginRouter(
    private val routing: Routing,
    val appId: String,
    val services: PlatformServices,
) {
    /**
     * Register authenticated routes.
     *
     * Every handler inside this block:
     * - Requires `Authorization: Bearer <firebase-token>` or a session cookie.
     * - Receives a [PluginContext] as the first argument.
     * - Has `this` bound to [ApplicationCall] — use `call.receive<T>()`, `call.respond(...)`.
     *
     * ```kotlin
     * api.authenticated {
     *     get("/items") { ctx ->
     *         val rows = ctx.db.rawQuery("SELECT * FROM items WHERE owner_uid = ?", listOf(ctx.userId))
     *         call.respond(rows.getOrThrow())
     *     }
     *     post("/items") { ctx ->
     *         val body = call.receive<CreateItemRequest>()
     *         // insert and respond ...
     *     }
     * }
     * ```
     */
    fun authenticated(block: AuthenticatedPluginScope.() -> Unit) {
        routing.route("/api/v1/$appId") {
            install(UserAuthPlugin) {
                authService = services.authService
                requestIdentityProvider = ExternalIdentityProvider(services.authService)
            }
            AuthenticatedPluginScope(this, services).block()
        }
    }

    /**
     * Register unauthenticated public routes.
     *
     * The underlying [Route] is exposed directly — use the standard Ktor DSL.
     *
     * ```kotlin
     * api.public {
     *     get("/ping") { call.respond("pong") }
     * }
     * ```
     */
    fun public(block: Route.() -> Unit) {
        routing.route("/api/v1/$appId", block)
    }
}

/**
 * Authenticated route scope — returned by [PluginRouter.authenticated].
 *
 * Wraps standard Ktor HTTP verbs and injects a [PluginContext] into each handler.
 * The SQL user context is set before the handler runs, activating Row Level Security.
 */
class AuthenticatedPluginScope(
    private val route: Route,
    private val services: PlatformServices,
) {
    fun get(path: String = "", handler: suspend ApplicationCall.(PluginContext) -> Unit) {
        route.get(path) { handle(handler) }
    }

    fun post(path: String = "", handler: suspend ApplicationCall.(PluginContext) -> Unit) {
        route.post(path) { handle(handler) }
    }

    fun put(path: String = "", handler: suspend ApplicationCall.(PluginContext) -> Unit) {
        route.put(path) { handle(handler) }
    }

    fun patch(path: String = "", handler: suspend ApplicationCall.(PluginContext) -> Unit) {
        route.patch(path) { handle(handler) }
    }

    fun delete(path: String = "", handler: suspend ApplicationCall.(PluginContext) -> Unit) {
        route.delete(path) { handle(handler) }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handle(
        handler: suspend ApplicationCall.(PluginContext) -> Unit,
    ) {
        val requestCtx = call.attributes[RequestContextKey]
        val pluginCtx = PluginContext(
            userId = requestCtx.userId,
            appId = requestCtx.appId,
            teamId = requestCtx.teamId,
            db = services.database,
        )
        SqlRequestContext.withUserId(requestCtx.userId) {
            call.handler(pluginCtx)
        }
    }
}

/**
 * Request-scoped context injected into every [AppPlugin] route handler.
 *
 * @property userId  Firebase UID of the authenticated user.
 * @property appId   Value of the `X-App-Id` request header — identifies the calling app.
 * @property teamId  Value of the `X-Team-Id` header, if present.
 * @property db      Platform database. Use [Database.rawQuery] for arbitrary SQL SELECTs
 *                   and JOINs across your plugin's tables and platform tables.
 *                   The RLS user context is already set — queries are automatically
 *                   scoped to the current user by any RLS policies on your tables.
 */
data class PluginContext(
    val userId: String,
    val appId: String,
    val teamId: String?,
    val db: Database,
)
