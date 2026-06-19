package com.appforge.server.infrastructure

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Manages database migrations using Flyway.
 *
 * Migrations are located in: src/main/resources/db/migration/
 * Naming convention: V<version>__<description>.sql
 */
class MigrationManager {
    private val logger = LoggerFactory.getLogger(MigrationManager::class.java)

    /**
     * Runs all pending migrations.
     *
     * @param dataSource the DataSource to migrate
     * @return the list of executed migrations
     */
    fun migrate(dataSource: DataSource): List<String> {
        logger.info("Starting Flyway database migrations...")

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()

        val result = flyway.migrate()
        val appliedMigrations = flyway.info().applied().map { it.version.toString() + ": " + it.description }

        logger.info("Database migrations completed. Applied $result migrations.")
        appliedMigrations.forEach { logger.info("  Applied migration: $it") }

        return appliedMigrations
    }
}
