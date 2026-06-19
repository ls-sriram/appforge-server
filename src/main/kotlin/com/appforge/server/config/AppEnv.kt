package com.appforge.server.config

import com.appforge.server.config.options.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File

data class AppEnv(
        val runtime: RuntimeOptions,
        val session: SessionOptions,
        val dodoPayments: DodoPaymentsOptions,
        val firebase: FirebaseOptions,
        val uploads: UploadOptions,
        val openai: OpenAIOptions,
        val email: EmailOptions,
        val billing: BillingOptions,
        val database: DatabaseOptions,
) {

    companion object {
        fun load(): AppEnv {
            val env = System.getenv()
            val config = loadMergedConfig(env)
            val reader = ConfigReader(config, env)

            val runtime = RuntimeOptions.load(reader)
            return AppEnv(
                    runtime = runtime,
                    session = SessionOptions.load(reader, runtime.nodeEnv),
                    dodoPayments = DodoPaymentsOptions.load(reader),
                    firebase = FirebaseOptions.load(reader),
                    uploads = UploadOptions.load(reader),
                    openai = OpenAIOptions.load(reader),
                    email = EmailOptions.load(reader),
                    billing = BillingOptions.load(reader),
                    database = DatabaseOptions.load(reader),
            )
        }

        private fun loadMergedConfig(env: Map<String, String>): Config {
            val baseConfig = ConfigFactory.load()
            val appEnv =
                    env["APP_ENV"]?.ifBlank { null }
                            ?: (if (baseConfig.hasPath("app.env")) baseConfig.getString("app.env")
                            else "dev")
            val envConfig = loadOptionalConfig("application-$appEnv.conf").withFallback(baseConfig)
            return loadOptionalConfig(".secrets.conf").withFallback(envConfig).resolve()
        }

        private fun loadOptionalConfig(name: String): Config {
            val file = File(name)
            if (file.exists()) {
                return ConfigFactory.parseFile(file)
            }

            val resource = AppEnv::class.java.classLoader.getResource(name)
            return if (resource != null) {
                ConfigFactory.parseURL(resource)
            } else {
                ConfigFactory.empty()
            }
        }
    }
}
