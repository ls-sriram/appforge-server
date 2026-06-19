package com.appforge.server.services.useraccount

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.AppTimestamp
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider

data class AccountDeletionAuditRecord(
    val id: String,
    val userId: String,
    val event: String,
    val status: String,
    val detail: String? = null,
    val createdAt: AppTimestamp,
)

interface AccountDeletionAuditRepositoryApi {
    suspend fun record(record: AccountDeletionAuditRecord)
}

class AccountDeletionAuditRepository(
    sqlDatabase: ExposedDatabase,
) : AccountDeletionAuditRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/useraccount/account_deletion.sql",
        classLoader = AccountDeletionAuditRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun record(record: AccountDeletionAuditRecord) {
        transactionProvider.write { conn ->
            conn.prepareStatement(
                sql.query("account_deletion.insert_audit_record")
            ).use { stmt ->
                stmt.setString(1, record.id)
                stmt.setString(2, record.userId)
                stmt.setString(3, record.event)
                stmt.setString(4, record.status)
                stmt.setString(5, record.detail)
                stmt.setInstant(6, record.createdAt)
                stmt.executeUpdate()
            }
        }
    }
}
