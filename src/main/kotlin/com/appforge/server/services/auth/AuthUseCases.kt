package com.appforge.server.services.auth

data class SessionCookieSpec(
    val name: String,
    val value: String,
    val maxAge: Int,
    val secure: Boolean,
    val sameSite: String,
    val path: String = "/",
)

sealed class AuthResponse<out T> {
    data class Ok<T>(val data: T, val cookie: SessionCookieSpec? = null) : AuthResponse<T>()
    data class Unauthorized(val message: String = "Unauthorized") : AuthResponse<Nothing>()
    data class Forbidden(val message: String = "Forbidden") : AuthResponse<Nothing>()
    data class BadRequest(val message: String) : AuthResponse<Nothing>()
}
