package com.appforge.server.services.reviews.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.appforge.server.infrastructure.time.*

interface ReviewRepositoryApi {
    suspend fun create(userId: String, collection: String, entityId: String, review: Review): Resource<String>
    suspend fun getReviewsForEntity(userId: String, collection: String, entityId: String): Resource<List<Review>>
    suspend fun listAllReviews(userId: String): Resource<List<Review>>
}

class ReviewRepository(
    sqlDatabase: ExposedDatabase,
) : ReviewRepositoryApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/reviews/reviews.sql",
        classLoader = ReviewRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun create(
        userId: String,
        collection: String,
        entityId: String,
        review: Review,
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val contentJson = json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    review.content.forEach { (key, value) ->
                        put(key, value?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    }
                }
            )
            transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("reviews.insert_review")
                ).use { stmt ->
                    stmt.setString(1, review.id)
                    stmt.setString(2, userId)
                    stmt.setString(3, review.entityId)
                    stmt.setString(4, review.entityCategory.value)
                    stmt.setString(5, collection)
                    stmt.setString(6, review.authorRole.wire)
                    stmt.setString(7, review.authorId)
                    stmt.setString(8, review.authorName)
                    stmt.setString(9, review.authorEmail)
                    stmt.setString(10, contentJson)
                    stmt.setInstant(11, review.createdAt)
                    stmt.executeUpdate()
                }
            }
            Resource.Success(review.id)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun getReviewsForEntity(
        userId: String,
        collection: String,
        entityId: String,
    ): Resource<List<Review>> = withContext(Dispatchers.IO) {
        try {
            transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("reviews.select_reviews_for_entity")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, entityId)
                    stmt.setString(3, collection)
                    stmt.executeQuery().use { rs ->
                        val rows = mutableListOf<Review>()
                        while (rs.next()) {
                            rows.add(
                                Review(
                                    id = rs.getString("id"),
                                    entityId = rs.getString("entity_id"),
                                    entityCategory = EntityCategory.fromWire(rs.getString("entity_category")),
                                    authorRole = ReviewAuthorRole.fromWire(rs.getString("author_role")),
                                    authorId = rs.getString("author_id"),
                                    authorName = rs.getString("author_name"),
                                    authorEmail = rs.getString("author_email"),
                                    content = parseContent(rs.getString("content")),
                                    createdAt = rs.getAppTimestamp("created_at") ?: error("reviews.created_at is null"),
                                )
                            )
                        }
                        Resource.Success(rows)
                    }
                }
            }
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun listAllReviews(userId: String): Resource<List<Review>> = withContext(Dispatchers.IO) {
        try {
            transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("reviews.select_reviews_for_owner")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeQuery().use { rs ->
                        val rows = mutableListOf<Review>()
                        while (rs.next()) {
                            rows.add(
                                Review(
                                    id = rs.getString("id"),
                                    entityId = rs.getString("entity_id"),
                                    entityCategory = EntityCategory.fromWire(rs.getString("entity_category")),
                                    authorRole = ReviewAuthorRole.fromWire(rs.getString("author_role")),
                                    authorId = rs.getString("author_id"),
                                    authorName = rs.getString("author_name"),
                                    authorEmail = rs.getString("author_email"),
                                    content = parseContent(rs.getString("content")),
                                    createdAt = rs.getAppTimestamp("created_at") ?: error("reviews.created_at is null"),
                                )
                            )
                        }
                        Resource.Success(rows)
                    }
                }
            }
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    private fun parseContent(rawContent: String): Map<String, Any?> {
        if (rawContent.isBlank()) return emptyMap()
        return json.parseToJsonElement(rawContent).jsonObject.mapValues { (_, value) ->
            value.jsonPrimitive.contentOrNull ?: value.toString()
        }
    }
}
