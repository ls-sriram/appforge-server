package com.appforge.server.services.recordings

import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.auth.AuthService

interface RecordingServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val recordingService: RecordingService
}
