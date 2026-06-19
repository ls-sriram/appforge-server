package com.appforge.server.extensions

import com.appforge.server.infrastructure.Database
import com.appforge.server.services.auth.AuthService
import io.ktor.server.routing.Routing

/**
 * A client-specific extension module.
 *
 * Extensions are pluggable Kotlin modules that add app-specific
 * routes, database tables, and hook subscriptions to the platform.
 *
 * Each extension:
 * 1. Identifies itself with an `appId` (used in `X-App-Id` routing)
 * 2. Registers HTTP routes under its own prefix
 * 3. Optionally defines SQL tables (created by platform at startup)
 * 4. Optionally subscribes to platform hooks (before/after events)
 *
 * ## Example
 *
 * ```kotlin
 * object MyAppExtension : PlatformExtension {
 *     override val appId = "my-app"
 *
 *     override fun registerRoutes(routing: Routing, services: PlatformServices) {
 *         routing.route("/api/v1/my-app") {
 *             // ... app-specific routes
 *         }
 *     }
 *
 *     override fun defineTables(): List<String> = listOf(
 *         "CREATE TABLE IF NOT EXISTS extension_entities (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))"
 *     )
 *
 *     override fun defineHooks(): List<HookRegistration> = listOf(
 *         HookRegistration("after-entity-created", ::onEntityCreated)
 *     )
 * }
 * ```
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
