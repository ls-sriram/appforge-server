package com.appforge.server.services.analytics

import com.appforge.server.config.AppEnv
import com.appforge.server.middleware.AnalyticsMiddleware
import com.appforge.server.middleware.AnalyticsMiddleware.installAnalyticsCapture
import com.appforge.server.middleware.RequestContextKey
import com.appforge.server.services.CoreServices
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import org.slf4j.LoggerFactory

class AnalyticsProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) {
    private val logger = LoggerFactory.getLogger(AnalyticsProvider::class.java)

    val repository: AnalyticsRepository = AnalyticsRepository(core.database)
    val useCases: AnalyticsUseCases = AnalyticsUseCases(repository)
    val userUseCases: UserAnalyticsUseCases = UserAnalyticsUseCases(repository)

    suspend fun initialize() {
        try {
            repository.initializeCollection()
            logger.info("Analytics collection initialized")
        } catch (e: Exception) {
            logger.warn("Analytics initialization skipped: ${e.message}")
        }
    }

    fun install(app: Application) {
        app.installAnalyticsCapture(
            repository = repository,
            getUserId = { call: ApplicationCall ->
                try { call.attributes[RequestContextKey].userId } catch (e: Exception) { null }
            },
            getAppId = { call: ApplicationCall ->
                try { call.attributes[RequestContextKey].appId } catch (e: Exception) { null }
            },
            getTeamId = { call: ApplicationCall ->
                try { call.attributes[RequestContextKey].teamId } catch (e: Exception) { null }
            },
            includePaths = { path: String ->
                !path.startsWith("/api/v1/uploads/") && !path.startsWith("/uploads/")
            },
        )
    }
}
