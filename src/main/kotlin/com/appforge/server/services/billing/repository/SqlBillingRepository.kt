package com.appforge.server.services.billing.repository

import com.appforge.server.infrastructure.Database
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingEntitlementMapper
import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.models.PaymentRecord
import com.appforge.server.utils.wireEnumFrom
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory

/**
 * SQL-based billing repository.
 */
class SqlBillingRepository(
    database: Database,
) : BillingRepositoryApi {

    private val logger = LoggerFactory.getLogger(SqlBillingRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/billing/billing.sql",
        classLoader = SqlBillingRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider =
        SqlTransactionProvider(database as? ExposedDatabase ?: error("SqlBillingRepository requires ExposedDatabase"))

    // ─── Entitlement ─────────────────────────────────────────────────────

    override suspend fun upsert(userId: String, entitlement: BillingEntitlement) {
        run {
            val featuresJson = json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    entitlement.features.forEach { (key, feature) ->
                        put(
                            key,
                            buildJsonObject {
                                put("limit", feature.limit?.let(::JsonPrimitive) ?: JsonNull)
                                put("used", JsonPrimitive(feature.used))
                                put("unlocked", JsonPrimitive(feature.unlocked))
                            }
                        )
                    }
                }
            )
            transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("billing.upsert_entitlement")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, entitlement.plan.wire)
                    stmt.setString(3, entitlement.status.wire)
                    stmt.setInstant(4, entitlement.expiresAt)
                    stmt.setInstant(5, entitlement.startedAt)
                    stmt.setString(6, featuresJson)
                    stmt.setString(7, entitlement.source.wire)
                    stmt.setString(8, entitlement.externalCustomerId)
                    stmt.setString(9, entitlement.externalReferenceId)
                    stmt.setString(10, entitlement.billingType?.wire)
                    if (entitlement.lastPaymentAmountCents == null) stmt.setNull(11, java.sql.Types.BIGINT) else stmt.setLong(11, entitlement.lastPaymentAmountCents)
                    stmt.setString(12, entitlement.lastPaymentCurrency)
                    stmt.setInstant(13, entitlement.createdAt)
                    stmt.setInstant(14, entitlement.updatedAt)
                    stmt.executeUpdate()
                }
            }
            return
        }
    }

    override suspend fun get(userId: String): BillingEntitlement? {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("billing.select_entitlement_by_user")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@read null
                        val featuresRaw = rs.getString("features")
                        val featuresParsed = parseFeaturesJson(featuresRaw)
                        BillingEntitlement(
                            customerId = userId,
                            plan = BillingEntitlementMapper.planFromRaw(rs.getString("plan")),
                            status = wireEnumFrom(rs.getString("status")),
                            expiresAt = rs.getAppTimestamp("expires_at"),
                            startedAt = rs.getAppTimestamp("started_at") ?: error("billing_entitlements.started_at is null"),
                            externalCustomerId = rs.getString("external_customer_id"),
                            externalReferenceId = rs.getString("external_reference_id"),
                            billingType = rs.getString("billing_type")?.let { wireEnumFrom<BillingPaymentType>(it) },
                            lastPaymentAmountCents = rs.getObject("last_payment_amount_cents") as? Long,
                            lastPaymentCurrency = rs.getString("last_payment_currency"),
                            source = wireEnumFrom(rs.getString("entitlement_source")),
                            features = featuresParsed,
                            createdAt = rs.getAppTimestamp("created_at") ?: error("billing_entitlements.created_at is null"),
                            updatedAt = rs.getAppTimestamp("updated_at") ?: error("billing_entitlements.updated_at is null"),
                        )
                    }
                }
        }
    }

    // ─── Payment Records ─────────────────────────────────────────────────

    override suspend fun getPayment(userId: String, recordId: String): PaymentRecord? {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("billing.select_payment_by_id")
                ).use { stmt ->
                    stmt.setString(1, recordId)
                    stmt.setString(2, userId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@read null
                        PaymentRecord(
                            date = rs.getAppTimestamp("date") ?: error("billing_payments.date is null"),
                            amountCents = rs.getLong("amount_cents"),
                            currency = rs.getString("currency"),
                            planId = rs.getString("plan_id"),
                            emailSentAt = rs.getAppTimestamp("email_sent_at"),
                        )
                    }
                }
        }
    }

    override suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String?) {
        val id = recordId ?: record.date.toEpochMilli().toString()
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("billing.upsert_payment")
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, userId)
                    stmt.setInstant(3, record.date)
                    stmt.setLong(4, record.amountCents)
                    stmt.setString(5, record.currency)
                    stmt.setString(6, record.planId)
                    stmt.setInstant(7, record.emailSentAt)
                    stmt.executeUpdate()
                }
        }
    }

    // ─── Helper: list payments for a user ────────────────────────────────

    suspend fun listPayments(userId: String): List<PaymentRecord> {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("billing.list_payments_by_user")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeQuery().use { rs ->
                        val results = mutableListOf<PaymentRecord>()
                        while (rs.next()) {
                            results.add(
                                PaymentRecord(
                                    date = rs.getAppTimestamp("date") ?: error("billing_payments.date is null"),
                                    amountCents = rs.getLong("amount_cents"),
                                    currency = rs.getString("currency"),
                                    planId = rs.getString("plan_id"),
                                    emailSentAt = rs.getAppTimestamp("email_sent_at"),
                                )
                            )
                        }
                        results
                    }
                }
        }
    }

    private fun parseFeaturesJson(raw: String): Map<String, BillingFeature> {
        if (raw.isBlank()) return emptyMap()
        val root = json.parseToJsonElement(raw).jsonObject
        return root.mapValues { (_, value) ->
            val obj = value.jsonObject
            val used = obj["used"]?.jsonPrimitive?.longOrNull
                ?: throw IllegalArgumentException("Billing feature 'used' is required and must be numeric")
            val unlocked = obj["unlocked"]?.jsonPrimitive?.booleanOrNull
                ?: throw IllegalArgumentException("Billing feature 'unlocked' is required and must be boolean")
            BillingFeature(
                limit = obj["limit"]?.jsonPrimitive?.longOrNull,
                used = used,
                unlocked = unlocked,
            )
        }
    }

}
