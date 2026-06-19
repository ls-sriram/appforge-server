package com.appforge.server

import com.appforge.server.api.HealthResponse
import com.appforge.server.config.AppEnv
import com.appforge.server.extensions.PlatformExtension
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.routing.RoutesModule
import com.appforge.server.routing.analyticsRoutes
import com.appforge.server.routing.configureCors
import com.appforge.server.services.ClientRegistry
import com.appforge.server.services.CoreServices
import com.appforge.server.services.ServicesModule
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.callloging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import java.util.ServiceLoader
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.MDC

private val CallMdc = createApplicationPlugin("CallMdc") {
    onCall { call ->
        val requestId = call.callId ?: UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
    }
    onCallRespond { _, _ ->
        MDC.remove("requestId")
        MDC.remove("userId")
        MDC.remove("appId")
        MDC.remove("teamId")
    }
}

/**
 * Install the AppForge platform into a Ktor application.
 *
 * Sets up middleware, initializes infrastructure, discovers and registers
 * any [PlatformExtension] implementations on the classpath via ServiceLoader,
 * then mounts all platform and extension routes.
 *
 * Call this from your app's own `main()`:
 * ```kotlin
 * fun main() {
 *     val env = AppEnv.load()
 *     embeddedServer(Netty, port = env.runtime.port, host = env.runtime.host) {
 *         installAppforgePlatform(env)
 *     }.start(wait = true)
 * }
 * ```
 *
 * Your extension is discovered automatically — register it in
 * `META-INF/services/com.appforge.server.extensions.PlatformExtension`.
 */
fun Application.installAppforgePlatform(env: AppEnv = AppEnv.load()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(CallId) {
        retrieveFromHeader("X-Request-Id")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
    }
    install(CallMdc)
    install(CallLogging) {
        filter { call -> !call.request.path().startsWith("/api/v1/uploads/") }
        format { call ->
            val status = call.response.status()?.value?.toString() ?: "Unhandled"
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val duration = call.processingTimeMillis()
            val requestId = call.callId ?: "-"
            "$status $method - $uri in ${duration}ms requestId=$requestId"
        }
    }

    configureCors(env)
    configureErrorHandling()

    ClientRegistry.initialize(env)

    // Discover and register extensions from the classpath
    ServiceLoader.load(PlatformExtension::class.java).forEach { extension ->
        ClientRegistry.registerExtension(extension)
    }

    // Firebase disabled = health-only mode. Full infrastructure (auth, GCS, uploads)
    // requires Firebase to be configured.
    if (!env.firebase.enabled) {
        routing {
            get("/health") {
                call.respond(HealthResponse(status = "ok"))
            }
        }
        return
    }

    val coreServices = CoreServices(
        configProvider = ClientRegistry.configProvider,
        firebaseAuth = ClientRegistry.firebaseClient.auth,
        storage = ClientRegistry.storageClient,
        repositoryFactory = ClientRegistry.repositoryFactory,
        database = ClientRegistry.databaseInstance,
        transactionProvider = ClientRegistry.transactionProvider,
        featureFlagProvider = ClientRegistry.featureFlagProvider,
        hookEngine = ClientRegistry.hookEngine,
        extensionRegistry = ClientRegistry.extensionRegistry,
        uploadOwnershipAuthorizer = ClientRegistry.uploadOwnershipAuthorizer,
        uploadMetadataRepository = ClientRegistry.uploadMetadataRepository,
        uploadSignedUrlIssuer = ClientRegistry.uploadSignedUrlIssuer,
        uploadAccessUrlIssuer = ClientRegistry.uploadAccessUrlIssuer,
        dodoPaymentsClient = ClientRegistry.dodoPaymentsClient,
    )

    val servicesModule = ServicesModule(core = coreServices, env = env)

    val analyticsProvider = com.appforge.server.services.analytics.AnalyticsProvider(coreServices, env)
    runBlocking { analyticsProvider.initialize() }
    analyticsProvider.install(this)

    RoutesModule(servicesModule).register(this)

    routing {
        analyticsRoutes(
            analyticsProvider.useCases,
            analyticsProvider.userUseCases,
            env.runtime.internalSecret,
        )
    }
}
