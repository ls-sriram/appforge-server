package com.appforge.server.middleware

import com.appforge.server.api.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond

class SecretAuthConfig {
    var headerName: String = "X-Internal-Secret"
    lateinit var internalSecret: String
}

val SecretAuthPlugin = createRouteScopedPlugin("SecretAuthPlugin", ::SecretAuthConfig) {
    val headerName = pluginConfig.headerName
    val internalSecret = pluginConfig.internalSecret

    onCall { call ->
        val secret = call.request.header(headerName)
        if (secret != internalSecret) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            throw UnauthorizedException()
        }
    }
}
