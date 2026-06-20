package com.appforge.server.integration

import com.appforge.server.infrastructure.ExposedDatabase
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object IntegrationDbHarness {
    private val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
        withDatabaseName("appforge_it")
        withUsername("appforge")
        withPassword("appforge")
    }

    @Synchronized
    fun startIfNeeded() {
        if (!postgres.isRunning) {
            postgres.start()
        }
    }

    @Synchronized
    fun stop() {
        if (postgres.isRunning) {
            postgres.stop()
        }
    }

    fun createDatabase(poolSize: Int = 2): ExposedDatabase {
        val externalUrl = System.getenv("INTEGRATION_DB_URL")?.takeIf { it.isNotBlank() }
        val externalUser = System.getenv("INTEGRATION_DB_USER")?.takeIf { it.isNotBlank() }
        val externalPassword = System.getenv("INTEGRATION_DB_PASSWORD")?.takeIf { it.isNotBlank() }
        if (externalUrl != null && externalUser != null && externalPassword != null) {
            return ExposedDatabase(
                connectionUrl = externalUrl,
                username = externalUser,
                password = externalPassword,
                poolSize = poolSize,
            ).also { it.runMigrations() }
        }

        startIfNeeded()
        return ExposedDatabase(
            connectionUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            poolSize = poolSize,
        ).also { it.runMigrations() }
    }

}
