package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.appforge.server.infrastructure.DatabaseProvider

data class DatabaseOptions(
    val sqlUrl: String,
    val sqlUser: String,
    val sqlPassword: String,
    val sqlPoolSize: Int,
) {
    val primary: DatabaseProvider = DatabaseProvider.SQL

    companion object {
        fun load(reader: ConfigReader): DatabaseOptions {
            val host = reader.string("POSTGRES_HOST") ?: "localhost"
            val port = reader.int("POSTGRES_PORT") ?: 5432
            val database = reader.string("POSTGRES_DB") ?: ""
            return DatabaseOptions(
                sqlUrl = if (database.isBlank()) "" else "jdbc:postgresql://$host:$port/$database",
                sqlUser = reader.string("POSTGRES_USER") ?: "",
                sqlPassword = reader.string("POSTGRES_PASSWORD") ?: "",
                sqlPoolSize = reader.int("POSTGRES_POOL_SIZE") ?: 10,
            )
        }
    }
}
