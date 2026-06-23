package com.appforge.server.extensions

import com.appforge.server.infrastructure.Database
import com.appforge.server.services.auth.AuthService
import io.ktor.server.routing.Routing

/**
 * Contract for a pluggable app-specific module.
 *
 * Extensions add routes, database tables, and hook subscriptions to the platform
 * without modifying core server code. Each extension is identified by an [appId]
 * that maps to the `X-App-Id` request header, so multiple apps can share one
 * server binary while remaining fully isolated from each other.
 *
 * ## Recommended: use [AppPlugin] instead of implementing this directly
 *
 * [AppPlugin] is a convenience base class that eliminates boilerplate and provides
 * a typed route DSL ([PluginRouter]) with automatic auth, SQL user context, and
 * `db.rawQuery()` access for relational queries:
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
 *             get("/expenses") { ctx ->
 *                 val rows = ctx.db.rawQuery(
 *                     "SELECT * FROM expenses WHERE owner_uid = ? ORDER BY created_at DESC",
 *                     listOf(ctx.userId)
 *                 ).getOrThrow()
 *                 call.respond(rows)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## Implementing PlatformExtension directly
 *
 * Implement this interface when you need full control over Ktor routing or lifecycle:
 *
 * ```kotlin
 * object MyAppExtension : PlatformExtension {
 *     override val appId = "my-app"
 *
 *     override fun registerRoutes(routing: Routing, services: PlatformServices) {
 *         routing.route("/api/v1/my-app") {
 *             // ... standard Ktor routing
 *         }
 *     }
 *
 *     override fun defineTables(): List<String> = listOf(
 *         "CREATE TABLE IF NOT EXISTS my_entities (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))"
 *     )
 *
 *     override fun defineHooks(): List<HookRegistration> = listOf(
 *         HookRegistration(HookEvents.AFTER_ENTITY_CREATED, ::onEntityCreated)
 *     )
 * }
 * ```
 *
 * ## Registration
 *
 * **Code-share model** (each app gets its own binary):
 * ```kotlin
 * ClientRegistry.registerExtension(ExpensesPlugin())
 * ```
 *
 * **Runtime-share model** (one binary, multiple `X-App-Id` values):
 * Add the fully qualified class name to:
 * `META-INF/services/com.appforge.server.extensions.PlatformExtension`
 */
interface PlatformExtension {
    /**
     * Unique identifier for this extension.
     * Used for `X-App-Id` header matching and data scoping.
     */
    val appId: String

    /**
     * Register HTTP routes with the platform's Ktor application.
     * Extension routes are typically mounted under `/api/v1/{app-id}/...`
     * or a custom prefix defined by the extension.
     */
    fun registerRoutes(routing: Routing, services: PlatformServices)

    /**
     * SQL DDL statements to create extension tables.
     * Platform executes these at startup (CREATE TABLE IF NOT EXISTS).
     * Return empty list if extension uses only the generic platform tables.
     */
    fun defineTables(): List<String> = emptyList()

    /**
     * Hook subscriptions.
     * Platform fires these hooks at appropriate lifecycle points.
     * Return empty list if extension doesn't need hooks.
     */
    fun defineHooks(): List<HookRegistration> = emptyList()

    /**
     * Called when the platform initializes. Useful for extension-level setup.
     */
    fun onInitialize(services: PlatformServices) {}

    /**
     * Called when the platform shuts down. Useful for cleanup.
     */
    fun onShutdown() {}
}

/**
 * Services available to all extensions.
 * Provides access to core platform functionality.
 */
data class PlatformServices(
    /** Primary database — SQL for platform data. */
    val database: Database,

    /** Authentication service (Firebase). */
    val authService: AuthService,

    /** Hook engine — fire events to registered subscribers. */
    val hookEngine: HookEngine,

    /** All registered extensions (for cross-extension communication). */
    val extensions: List<PlatformExtension>,
)

/**
 * Registry that holds all extensions.
 * Extensions register themselves here at startup.
 */
class ExtensionRegistry {
    private val _extensions = mutableListOf<PlatformExtension>()
    val extensions: List<PlatformExtension> get() = _extensions.toList()

    fun register(extension: PlatformExtension) {
        if (_extensions.any { it.appId == extension.appId }) {
            throw IllegalArgumentException("Extension '${extension.appId}' is already registered")
        }
        _extensions.add(extension)
    }

    fun getById(appId: String): PlatformExtension? =
        _extensions.find { it.appId == appId }

    fun clear() {
        _extensions.clear()
    }
}
