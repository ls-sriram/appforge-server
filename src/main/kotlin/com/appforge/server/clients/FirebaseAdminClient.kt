package com.appforge.server.clients

import com.appforge.server.config.AppEnv
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

class FirebaseAdminClient(env: AppEnv) {
    companion object {
        @Volatile
        private var instances: MutableMap<String, FirebaseAdminClient> = mutableMapOf()

        fun getInstance(env: AppEnv): FirebaseAdminClient =
            synchronized(this) {
                val cacheKey = env.firebase.firebaseProjectId.trim()
                instances[cacheKey] ?: FirebaseAdminClient(env).also { instances[cacheKey] = it }
            }
    }

    val app: FirebaseApp
    val auth: FirebaseAuth
    val credentials = env.firebase.getCredentials()
    private val appName = "appforge-firebase-${env.firebase.firebaseProjectId.trim()}"

    init {
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(env.firebase.firebaseProjectId)
            .build()

        app = try {
            FirebaseApp.getInstance(appName)
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(options, appName)
        }

        auth = FirebaseAuth.getInstance(app)
    }
}
