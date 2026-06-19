package com.appforge.server.providers.transaction

import com.appforge.server.infrastructure.ExposedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

interface TransactionProvider {
    suspend fun <T> read(block: (Connection) -> T): T
    suspend fun <T> write(block: (Connection) -> T): T
}

class SqlTransactionProvider(
    private val database: ExposedDatabase,
) : TransactionProvider {
    override suspend fun <T> read(block: (Connection) -> T): T {
        return database.withConnection { conn -> block(conn) }
    }

    override suspend fun <T> write(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        database.getDataSource().connection.use { conn ->
            val previousAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = previousAutoCommit
            }
        }
    }
}
