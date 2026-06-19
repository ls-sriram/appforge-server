package com.appforge.server.routing

import com.appforge.server.api.HealthResponse
import com.appforge.server.services.system.SystemServices
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.Route

fun Route.healthRoutes(services: SystemServices) {
    route("/health") {
        get {
            call.respond(HealthResponse(status = services.healthUseCases.status()))
        }
    }
}
