package com.appforge.server.services.tasks

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.tasks.repository.SqlTaskRepository

class TaskProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : TaskServices {
    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }

    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    override val taskService: TaskService by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Task repository requires SQL database")
        TaskServiceImpl(SqlTaskRepository(relationalDb))
    }
}
