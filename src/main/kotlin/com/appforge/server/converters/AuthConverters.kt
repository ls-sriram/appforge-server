package com.appforge.server.converters

import com.appforge.server.api.SessionMeResponse
import com.google.firebase.auth.FirebaseToken

object AuthConverters {
    fun toSessionMeResponse(token: FirebaseToken): SessionMeResponse {
        return SessionMeResponse(
            uid = token.uid,
            email = token.email,
            name = token.name,
        )
    }
}
