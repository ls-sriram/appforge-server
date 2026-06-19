package com.appforge.server.services.billing.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.billing.models.BillingAuditRecord

interface BillingAuditRepositoryApi {
    suspend fun record(record: BillingAuditRecord)
    suspend fun tryRecordOnce(record: BillingAuditRecord): Boolean
}

class BillingAuditRepository(
    sqlDatabase: ExposedDatabase,
) : BillingAuditRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/billing/billing.sql",
        classLoader = BillingAuditRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun record(record: BillingAuditRecord) {
        val docId = record.webhookId ?: record.timestamp.toEpochMilli().toString()
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("billing.upsert_audit_record")
                ).use { stmt ->
                    stmt.setString(1, docId)
                    stmt.setString(2, record.payload)
                    stmt.setInstant(3, record.timestamp)
                    stmt.setString(4, record.webhookId)
                    stmt.setString(5, record.source)
                    stmt.executeUpdate()
                }
        }
    }

    override suspend fun tryRecordOnce(record: BillingAuditRecord): Boolean {
        val webhookId = record.webhookId
        if (webhookId.isNullOrBlank()) {
            record(record)
            return true
        }
        return transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("billing.insert_audit_record_once")
                ).use { stmt ->
                    stmt.setString(1, webhookId)
                    stmt.setString(2, record.payload)
                    stmt.setInstant(3, record.timestamp)
                    stmt.setString(4, webhookId)
                    stmt.setString(5, record.source)
                    stmt.executeUpdate() > 0
                }
            }
    }
}
