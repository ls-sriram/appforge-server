package com.appforge.server.services.tasks

import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.auth.AuthService

interface TaskServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val taskService: TaskService
}
