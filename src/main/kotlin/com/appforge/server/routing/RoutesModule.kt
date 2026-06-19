package com.appforge.server.routing

import com.appforge.server.extensions.PlatformServices
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.services.ServicesModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import com.appforge.server.routing.healthRoutes
import com.appforge.server.routing.authRoutes
import com.appforge.server.routing.billingRoutes
import com.appforge.server.routing.uploadRoutes
import com.appforge.server.routing.uploadEventRoutes
import com.appforge.server.routing.reviewRoutes
import com.appforge.server.routing.recordingRoutes
import com.appforge.server.routing.documentRoutes
import com.appforge.server.routing.publicShareRoutes
import com.appforge.server.routing.systemRoutes
import com.appforge.server.routing.taskRoutes
import com.appforge.server.routing.entityReviewRoutes
import com.appforge.server.routing.entityShareRoutes
import com.appforge.server.routing.entityShareCollectionRoutes

class RoutesModule(
    private val servicesModule: ServicesModule,
) {
    fun register(app: Application) {
        app.routing {
            // ─── Core platform routes ────────────────────────────────────
            healthRoutes(servicesModule.systemServices())
            authRoutes(servicesModule.authServices())
            billingRoutes(servicesModule.billingServices())
            uploadEventRoutes(servicesModule.uploadServices())
            uploadRoutes(servicesModule.uploadServices())
            recordingRoutes(servicesModule.recordingServices())
            documentRoutes(servicesModule.documentServices())
            taskRoutes(servicesModule.taskServices())
            reviewRoutes(servicesModule.reviewServices())
            publicShareRoutes(servicesModule.publicShareServices())

            // Internal system routes (secured via secret, not user auth)
            systemRoutes(servicesModule.systemServices())

            // Entity-scoped routes (reviews, shares)
            route("/api/v1/entities/{type}/{id}") {
                install(UserAuthPlugin) {
                    this.authService = servicesModule.shareServices().authService
                    this.requestIdentityProvider = servicesModule.shareServices().requestIdentityProvider
                }
                entityReviewRoutes(servicesModule.reviewServices())
                entityShareRoutes(servicesModule.shareServices())
            }

            route("/api/v1/entities") {
                install(UserAuthPlugin) {
                    this.authService = servicesModule.shareServices().authService
                    this.requestIdentityProvider = servicesModule.shareServices().requestIdentityProvider
                }
                entityShareCollectionRoutes(servicesModule.shareServices())
            }

            // ─── Extension routes ────────────────────────────────────────
            val core = servicesModule.core
            val authService = servicesModule.authServices().authService
            val platformServices = PlatformServices(
                database = core.database,
                authService = authService,
                hookEngine = core.hookEngine,
                extensions = core.extensionRegistry.extensions,
            )

            for (extension in core.extensionRegistry.extensions) {
                extension.registerRoutes(this, platformServices)
            }
        }
    }
}
