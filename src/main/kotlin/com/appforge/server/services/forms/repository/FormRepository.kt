package com.appforge.server.services.forms.repository

import com.appforge.server.api.reviews.ReviewTemplate
import com.appforge.server.api.reviews.ReviewTemplateField
import com.appforge.server.api.reviews.ReviewTemplateOption
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider

interface FormRepositoryApi {
    suspend fun getActiveReviewFormByEntityType(entityType: String): ReviewTemplate?
}

class FormRepository(
    sqlDatabase: ExposedDatabase,
) : FormRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/forms/forms.sql",
        classLoader = FormRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun getActiveReviewFormByEntityType(entityType: String): ReviewTemplate? {
        val normalized = entityType.trim().lowercase()
        if (normalized.isBlank()) return null
        return transactionProvider.read { conn ->
            conn.prepareStatement(sql.query("forms.select_active_form_by_kind_entity")).use { stmt ->
                stmt.setString(1, "review")
                stmt.setString(2, normalized)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val formId = rs.getString("id")
                        val fields = loadFields(conn, formId)
                        ReviewTemplate(
                            id = formId,
                            version = rs.getInt("version"),
                            entityType = rs.getString("entity_type"),
                            name = rs.getString("name"),
                            fields = fields,
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun loadFields(conn: java.sql.Connection, formId: String): List<ReviewTemplateField> {
        return conn.prepareStatement(sql.query("forms.select_fields_by_form_id")).use { stmt ->
            stmt.setString(1, formId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val fieldId = rs.getString("id")
                        add(
                            ReviewTemplateField(
                                id = fieldId,
                                label = rs.getString("label"),
                                type = rs.getString("field_type"),
                                required = rs.getBoolean("required"),
                                options = loadOptions(conn, formId, fieldId),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadOptions(conn: java.sql.Connection, formId: String, fieldId: String): List<ReviewTemplateOption> {
        return conn.prepareStatement(sql.query("forms.select_options_by_field_id")).use { stmt ->
            stmt.setString(1, formId)
            stmt.setString(2, fieldId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ReviewTemplateOption(
                                id = rs.getString("id"),
                                label = rs.getString("label"),
                            )
                        )
                    }
                }
            }
        }
    }
}
