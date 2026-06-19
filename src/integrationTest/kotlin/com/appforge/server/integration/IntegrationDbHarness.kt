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
        startIfNeeded()
        return ExposedDatabase(
            connectionUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            poolSize = poolSize,
        ).also { it.runMigrations() }
    }

}
