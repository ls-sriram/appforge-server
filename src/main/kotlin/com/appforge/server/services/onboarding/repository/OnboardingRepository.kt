package com.appforge.server.services.onboarding.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.onboarding.models.OnboardingQuestionType
import java.sql.Timestamp
import com.appforge.server.infrastructure.time.*

data class OnboardingQuestionRecord(
    val id: String,
    val stepType: String,
    val fieldKey: String,
    val prompt: String,
    val questionType: OnboardingQuestionType,
    val displayOrder: Int,
)

data class OnboardingQuestionOptionRecord(
    val id: String,
    val questionId: String,
    val label: String,
    val displayOrder: Int,
)

interface OnboardingRepositoryApi {
    suspend fun initializeState(userId: String, now: AppTimestamp, version: Int = 1)
    suspend fun replaceAnswers(
        userId: String,
        questionId: String,
        optionIds: List<String>,
        textValue: String?,
        now: AppTimestamp,
    )
    suspend fun markCompleted(userId: String, completedAt: AppTimestamp, version: Int = 1)
    suspend fun hasCompleted(userId: String): Boolean
    suspend fun listActiveQuestions(): List<OnboardingQuestionRecord>
    suspend fun listActiveQuestionOptions(): List<OnboardingQuestionOptionRecord>
    suspend fun questionExists(questionId: String): Boolean
    suspend fun optionBelongsToQuestion(optionId: String, questionId: String): Boolean
}

class SqlOnboardingRepository(
    database: ExposedDatabase,
) : OnboardingRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/onboarding/onboarding.sql",
        classLoader = SqlOnboardingRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(database)

    override suspend fun initializeState(userId: String, now: AppTimestamp, version: Int) {
        transactionProvider.write { conn ->
            conn.prepareStatement(
                sql.query("onboarding.initialize_state")
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setInt(2, version)
                stmt.setTimestamp(3, Timestamp.from(now))
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun replaceAnswers(
        userId: String,
        questionId: String,
        optionIds: List<String>,
        textValue: String?,
        now: AppTimestamp,
    ) {
        transactionProvider.write { conn ->
            conn.prepareStatement(sql.query("onboarding.delete_answers_for_question")).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, questionId)
                stmt.executeUpdate()
            }
            if (optionIds.isNotEmpty()) {
                optionIds.forEach { optionId ->
                    conn.prepareStatement(sql.query("onboarding.insert_answer")).use { stmt ->
                        stmt.setString(1, userId)
                        stmt.setString(2, questionId)
                        stmt.setString(3, optionId)
                        stmt.setNull(4, java.sql.Types.VARCHAR)
                        stmt.setTimestamp(5, Timestamp.from(now))
                        stmt.executeUpdate()
                    }
                }
            } else if (!textValue.isNullOrBlank()) {
                conn.prepareStatement(sql.query("onboarding.insert_answer")).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, questionId)
                    stmt.setNull(3, java.sql.Types.VARCHAR)
                    stmt.setString(4, textValue)
                    stmt.setTimestamp(5, Timestamp.from(now))
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun markCompleted(userId: String, completedAt: AppTimestamp, version: Int) {
        transactionProvider.write { conn ->
            conn.prepareStatement(
                sql.query("onboarding.mark_completed")
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setInt(2, version)
                stmt.setTimestamp(3, Timestamp.from(completedAt))
                stmt.setTimestamp(4, Timestamp.from(completedAt))
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun hasCompleted(userId: String): Boolean {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("onboarding.select_completed_state")
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getBoolean(1) else false
                }
            }
        }
    }

    override suspend fun listActiveQuestions(): List<OnboardingQuestionRecord> {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("onboarding.select_active_questions")
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                OnboardingQuestionRecord(
                                    id = rs.getString("id"),
                                    stepType = rs.getString("step_type"),
                                    fieldKey = rs.getString("field_key"),
                                    prompt = rs.getString("prompt"),
                                    questionType = OnboardingQuestionType.fromWire(rs.getString("question_type")),
                                    displayOrder = rs.getInt("display_order"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun listActiveQuestionOptions(): List<OnboardingQuestionOptionRecord> {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("onboarding.select_active_options")
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                OnboardingQuestionOptionRecord(
                                    id = rs.getString("id"),
                                    questionId = rs.getString("question_id"),
                                    label = rs.getString("label"),
                                    displayOrder = rs.getInt("display_order"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun questionExists(questionId: String): Boolean {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("onboarding.question_exists")
            ).use { stmt ->
                stmt.setString(1, questionId)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    override suspend fun optionBelongsToQuestion(optionId: String, questionId: String): Boolean {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("onboarding.option_matches_question")
            ).use { stmt ->
                stmt.setString(1, optionId)
                stmt.setString(2, questionId)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
    }
}
