package com.appforge.server.integration

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.reviews.ReviewAnswerRequest
import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.sharing.CreateShareRequest
import com.appforge.server.api.sharing.CreateReviewerShareRequest
import com.appforge.server.api.sharing.ReviewerShareEntityResponse
import com.appforge.server.api.sharing.ReviewerShareResponse
import com.appforge.server.api.sharing.ShareResponse
import com.appforge.server.api.sharing.ShareSummaryResponse
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.routing.entityShareCollectionRoutes
import com.appforge.server.routing.entityReviewerShareRoutes
import com.appforge.server.routing.entityShareRoutes
import com.appforge.server.routing.publicShareRoutes
import com.appforge.server.routing.reviewerInboxRoutes
import com.appforge.server.routing.reviewerShareManagementRoutes
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.repository.SqlUserRepository
import com.appforge.server.services.documents.repository.SqlDocumentRepository
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.forms.repository.FormRepository
import com.appforge.server.services.recordings.repository.RecordingRepository
import com.appforge.server.services.reviews.ai.AIReviewWorker
import com.appforge.server.services.reviews.ai.DocumentTextContentResolver
import com.appforge.server.services.reviews.ai.EntityTextContentResolverRegistry
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.repository.ReviewRepository
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.sharing.PublicShareServices
import com.appforge.server.services.sharing.PublicShareUseCases
import com.appforge.server.services.sharing.PublicShareUseCasesImpl
import com.appforge.server.services.sharing.ReviewerShareUseCases
import com.appforge.server.services.sharing.ReviewerShareUseCasesImpl
import com.appforge.server.services.sharing.ShareServices
import com.appforge.server.services.sharing.ShareUseCases
import com.appforge.server.services.sharing.ShareUseCasesImpl
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.repository.DocumentEntityDescriptorResolver
import com.appforge.server.services.sharing.repository.EntityDescriptorResolverRegistry
import com.appforge.server.services.sharing.repository.ReviewerShareRepository
import com.appforge.server.services.sharing.repository.ShareEntityRepository
import com.appforge.server.services.sharing.repository.ShareRepository
import com.appforge.server.services.sharing.services.ShareService
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import com.google.cloud.storage.Storage
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShareReviewBackendIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val shareSql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/sharing/sharing.sql",
        classLoader = ShareRepository::class.java.classLoader,
    )

    @Test
    fun `share endpoints mutate backend state in postgres`() = testApplication {
        val db = IntegrationDbHarness.createDatabase()
        resetBackendTables(db)
        seedUser(db, uid = "user-1", email = "user-1@example.com")
        seedDocument(
            db = db,
            id = "doc-1",
            ownerUid = "user-1",
            title = "Doc One",
            content = "Integration content",
        )

        val services = buildShareServices(db)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    entityShareRoutes(services)
                }
                route("/api/v1/entities") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    entityShareCollectionRoutes(services)
                }
            }
        }

        val createResponse = client.post("/api/v1/entities/document/doc-1/shares") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    CreateShareRequest(
                        entityType = "document",
                        entityPath = "/api/v1/entities/document/doc-1",
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdShare = json.decodeFromString<ShareResponse>(createResponse.body<String>())
        assertEquals("document", createdShare.entityType)
        assertEquals("doc-1", createdShare.entityId)
        assertEquals(1, countShares(db))
        assertEquals(null, lookupShareRevokedAt(db, createdShare.id))

        val listResponse = client.get("/api/v1/entities/document/doc-1/shares") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listed = json.decodeFromString<List<ShareSummaryResponse>>(listResponse.body<String>())
        assertEquals(1, listed.size)
        assertEquals(createdShare.id, listed.single().id)

        val revokeResponse = client.post("/api/v1/entities/shares/${createdShare.id}/revoke") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)
        assertNotNull(lookupShareRevokedAt(db, createdShare.id))
    }

    @Test
    fun `public review submit stores structured json in postgres`() = testApplication {
        val db = IntegrationDbHarness.createDatabase()
        resetBackendTables(db)
        seedUser(db, uid = "user-1", email = "user-1@example.com")
        seedDocument(
            db = db,
            id = "doc-1",
            ownerUid = "user-1",
            title = "Doc One",
            content = "Public review content",
        )
        seedDocumentReviewForm(db)

        val shareServices = buildShareServices(db)
        val publicShareServices = buildPublicShareServices(db)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = shareServices.authService
                        this.requestIdentityProvider = shareServices.requestIdentityProvider
                    }
                    entityShareRoutes(shareServices)
                }
                publicShareRoutes(publicShareServices)
            }
        }

        val createResponse = client.post("/api/v1/entities/document/doc-1/shares") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    CreateShareRequest(
                        entityType = "document",
                        entityPath = "/api/v1/entities/document/doc-1",
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdShare = json.decodeFromString<ShareResponse>(createResponse.body<String>())

        val reviewResponse = client.post("/shares/${createdShare.id}/reviews") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    SubmitReviewPayload(
                        displayName = "Reviewer",
                        reviewFormId = "document_review_form_v1",
                        reviewFormVersion = 1,
                        answers = listOf(
                            ReviewAnswerRequest(fieldId = "topics", optionIds = listOf("clarity", "structure")),
                            ReviewAnswerRequest(fieldId = "summary", textValue = "Clear and concise."),
                        ),
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, reviewResponse.status)
        val createdReview = json.decodeFromString<ReviewResponse>(reviewResponse.body<String>())
        assertEquals("Reviewer", createdReview.authorName)
        assertTrue(createdReview.content["topics"] is JsonArray)

        val contentJson = loadSingleReviewContent(db)
        val topics = contentJson["topics"] as? JsonArray
        assertNotNull(topics)
        assertEquals(listOf(JsonPrimitive("clarity"), JsonPrimitive("structure")), topics.toList())
        assertEquals(JsonPrimitive("Clear and concise."), contentJson["summary"])
    }

    @Test
    fun `reviewer share endpoints restrict access to authenticated reviewer and persist review author email`() = testApplication {
        val db = IntegrationDbHarness.createDatabase()
        resetBackendTables(db)
        seedUser(db, uid = "user-1", email = "student@example.com")
        seedUser(db, uid = "reviewer-1", email = "mentor@example.com")
        seedUser(db, uid = "reviewer-2", email = "other@example.com")
        seedDocument(
            db = db,
            id = "doc-1",
            ownerUid = "user-1",
            title = "Doc One",
            content = "Reviewer share content",
        )
        seedDocumentReviewForm(db)

        val services = buildShareServices(db)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    entityReviewerShareRoutes(services)
                }
                route("/api/v1/reviewer-shares") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    reviewerShareManagementRoutes(services)
                }
                route("/api/v1/reviewer") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    reviewerInboxRoutes(services)
                }
            }
        }

        val createResponse = client.post("/api/v1/entities/document/doc-1/reviewer-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CreateReviewerShareRequest(reviewerEmail = "mentor@example.com")))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdShare = json.decodeFromString<ReviewerShareResponse>(createResponse.body<String>())
        assertEquals("active", createdShare.status)

        val ownerListResponse = client.get("/api/v1/entities/document/doc-1/reviewer-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, ownerListResponse.status)
        val ownerShares = json.decodeFromString<List<ReviewerShareResponse>>(ownerListResponse.body<String>())
        assertEquals(listOf(createdShare.id), ownerShares.map { it.id })

        val reviewerInboxResponse = client.get("/api/v1/reviewer/shares") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, reviewerInboxResponse.status)
        val reviewerShares = json.decodeFromString<List<ReviewerShareResponse>>(reviewerInboxResponse.body<String>())
        assertEquals(listOf(createdShare.id), reviewerShares.map { it.id })

        val otherReviewerInboxResponse = client.get("/api/v1/reviewer/shares") {
            header(HttpHeaders.Authorization, "Bearer other-reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, otherReviewerInboxResponse.status)
        val otherReviewerShares = json.decodeFromString<List<ReviewerShareResponse>>(otherReviewerInboxResponse.body<String>())
        assertTrue(otherReviewerShares.isEmpty())

        val detailResponse = client.get("/api/v1/reviewer/shares/${createdShare.id}") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, detailResponse.status)
        val detail = json.decodeFromString<ReviewerShareEntityResponse>(detailResponse.body<String>())
        assertEquals("Reviewer share content", detail.entity.content)

        val forbiddenDetailResponse = client.get("/api/v1/reviewer/shares/${createdShare.id}") {
            header(HttpHeaders.Authorization, "Bearer other-reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.Gone, forbiddenDetailResponse.status)

        val reviewTemplateResponse = client.get("/api/v1/reviewer/shares/${createdShare.id}/review-template") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, reviewTemplateResponse.status)

        val submitResponse = client.post("/api/v1/reviewer/shares/${createdShare.id}/reviews") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    SubmitReviewerShareReviewPayload(
                        reviewFormId = "document_review_form_v1",
                        reviewFormVersion = 1,
                        answers = listOf(
                            ReviewAnswerRequest(fieldId = "topics", optionIds = listOf("clarity")),
                            ReviewAnswerRequest(fieldId = "summary", textValue = "Mentor feedback."),
                        ),
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, submitResponse.status)
        val createdReview = json.decodeFromString<ReviewResponse>(submitResponse.body<String>())
        assertEquals("mentor@example.com", createdReview.authorEmail)

        val storedReview = loadSingleReviewRow(db)
        assertEquals("mentor@example.com", storedReview.authorEmail)
        assertEquals(JsonPrimitive("Mentor feedback."), storedReview.content["summary"])

        val revokeResponse = client.post("/api/v1/reviewer-shares/${createdShare.id}/revoke") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)

        val revokedDetailResponse = client.get("/api/v1/reviewer/shares/${createdShare.id}") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.Gone, revokedDetailResponse.status)
    }

    private fun buildShareServices(db: ExposedDatabase): ShareServices {
        val authService = mockAuthService()
        val shareRepository = ShareRepository(db)
        val reviewerShareRepository = ReviewerShareRepository(db)
        val shareService = ShareService(shareRepository)
        val shareUseCases: ShareUseCases = ShareUseCasesImpl(
            shareService = shareService,
            emailService = mockk<EmailService>(relaxed = true),
            publicBaseUrl = "https://example.test",
        )
        val documentRepository = SqlDocumentRepository(db)
        val reviewRepository = ReviewRepository(db)
        val reviewService = ReviewService(
            reviewRepository = reviewRepository,
            profileRepository = mockk(relaxed = true),
            aiReviewWorker = mockk<AIReviewWorker>(relaxed = true),
        )
        val contentResolver = ReviewContentResolver(
            storage = mockk<Storage>(relaxed = true),
            uploadMetadataRepository = mockk<UploadMetadataRepository>(relaxed = true),
            textContentResolverRegistry = EntityTextContentResolverRegistry(
                mapOf("document" to DocumentTextContentResolver(documentRepository))
            ),
        )
        val reviewerShareUseCases: ReviewerShareUseCases = ReviewerShareUseCasesImpl(
            reviewerShareRepository = reviewerShareRepository,
            userRepository = SqlUserRepository(db),
            authService = authService,
            emailService = mockk<EmailService>(relaxed = true),
            publicBaseUrl = "https://example.test",
            reviewService = reviewService,
            contentResolver = contentResolver,
            shareEntityRepository = ShareEntityRepository(
                EntityDescriptorResolverRegistry(
                    mapOf("document" to DocumentEntityDescriptorResolver(documentRepository))
                )
            ),
            formRepository = FormRepository(db),
            uploadMetadataRepository = mockk(relaxed = true),
            signedGetUrlIssuer = mockk<SignedGetUrlIssuer>(relaxed = true),
            uploadExpirySeconds = 300,
        )
        return object : ShareServices {
            override val authService: AuthService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val shareUseCases: ShareUseCases = shareUseCases
            override val reviewerShareUseCases: ReviewerShareUseCases = reviewerShareUseCases
        }
    }

    private fun buildPublicShareServices(db: ExposedDatabase): PublicShareServices {
        val shareRepository = ShareRepository(db)
        val shareService = ShareService(shareRepository)
        val documentRepository = SqlDocumentRepository(db)
        val reviewRepository = ReviewRepository(db)
        val reviewService = ReviewService(
            reviewRepository = reviewRepository,
            profileRepository = mockk(relaxed = true),
            aiReviewWorker = mockk<AIReviewWorker>(relaxed = true),
        )
        val publicShareUseCases: PublicShareUseCases = PublicShareUseCasesImpl(
            shareService = shareService,
            reviewService = reviewService,
            contentResolver = ReviewContentResolver(
                storage = mockk<Storage>(relaxed = true),
                uploadMetadataRepository = mockk<UploadMetadataRepository>(relaxed = true),
                textContentResolverRegistry = EntityTextContentResolverRegistry(
                    mapOf("document" to DocumentTextContentResolver(documentRepository))
                ),
            ),
            shareEntityRepository = ShareEntityRepository(
                EntityDescriptorResolverRegistry(
                    mapOf("document" to DocumentEntityDescriptorResolver(documentRepository))
                )
            ),
            formRepository = FormRepository(db),
            uploadMetadataRepository = mockk(relaxed = true),
            signedGetUrlIssuer = mockk<SignedGetUrlIssuer>(relaxed = true),
            uploadExpirySeconds = 300,
            recordingRepository = mockk<RecordingRepository>(relaxed = true),
        )
        return object : PublicShareServices {
            override val publicShareUseCases: PublicShareUseCases = publicShareUseCases
        }
    }

    private fun mockAuthService(): AuthService {
        val authService = mockk<AuthService>()
        val ownerToken = mockk<FirebaseToken>()
        val reviewerToken = mockk<FirebaseToken>()
        val otherReviewerToken = mockk<FirebaseToken>()
        every { ownerToken.uid } returns "user-1"
        every { reviewerToken.uid } returns "reviewer-1"
        every { otherReviewerToken.uid } returns "reviewer-2"
        every { authService.verifyIdToken("good-token") } returns ownerToken
        every { authService.verifyIdToken("owner-token") } returns ownerToken
        every { authService.verifyIdToken("reviewer-token") } returns reviewerToken
        every { authService.verifyIdToken("other-reviewer-token") } returns otherReviewerToken
        every { authService.verifySessionCookie(any()) } returns null
        every { authService.sessionCookieName } returns "session"
        every { authService.internalSecret } returns "internal-secret"
        every { authService.getUserEmail("reviewer-1") } returns "mentor@example.com"
        every { authService.getUserEmail("reviewer-2") } returns "other@example.com"
        return authService
    }

    private fun seedUser(db: ExposedDatabase, uid: String, email: String) = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO app_users (uid, email, email_normalized, display_name, created_at, last_login_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uid)
                stmt.setString(2, email)
                stmt.setString(3, email.lowercase())
                stmt.setString(4, uid)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedDocument(
        db: ExposedDatabase,
        id: String,
        ownerUid: String,
        title: String,
        content: String,
    ) = runBlocking {
        SqlDocumentRepository(db).upsert(
            id = id,
            ownerUid = ownerUid,
            title = title,
            tag = "document",
            version = "v1",
            content = content,
        )
    }

    private fun seedDocumentReviewForm(db: ExposedDatabase) = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO forms (id, version, form_kind, entity_type, name, status, created_at, updated_at)
                VALUES ('document_review_form_v1', 1, 'review', 'document', 'Document Review', 'active', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """.trimIndent()
            ).use { it.executeUpdate() }
            conn.prepareStatement(
                """
                INSERT INTO form_fields (id, form_id, label, field_type, required, display_order)
                VALUES
                    ('topics', 'document_review_form_v1', 'Topics', 'multi_select', TRUE, 10),
                    ('summary', 'document_review_form_v1', 'Summary', 'text', TRUE, 20)
                ON CONFLICT (form_id, id) DO NOTHING
                """.trimIndent()
            ).use { it.executeUpdate() }
            conn.prepareStatement(
                """
                INSERT INTO form_field_options (id, form_id, form_field_id, label, display_order)
                VALUES
                    ('clarity', 'document_review_form_v1', 'topics', 'Clarity', 10),
                    ('structure', 'document_review_form_v1', 'topics', 'Structure', 20)
                ON CONFLICT (form_id, form_field_id, id) DO NOTHING
                """.trimIndent()
            ).use { it.executeUpdate() }
        }
    }

    private fun resetBackendTables(db: ExposedDatabase) = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                TRUNCATE TABLE reviews, reviewer_entity_shares, entity_shares, documents, form_field_options, form_fields, forms, profiles, app_users
                RESTART IDENTITY CASCADE
                """.trimIndent()
            ).use { it.executeUpdate() }
        }
    }

    private fun countShares(db: ExposedDatabase): Int = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM entity_shares").use { stmt ->
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    private fun lookupShareRevokedAt(db: ExposedDatabase, token: String): String? = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(shareSql.query("sharing.select_entity_share_by_token_hash")).use { stmt ->
                stmt.setString(1, sha256(token))
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    rs.getTimestamp("revoked_at")?.toInstant()?.toString()
                }
            }
        }
    }

    private fun loadSingleReviewContent(db: ExposedDatabase): JsonObject = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement("SELECT content::text FROM reviews LIMIT 1").use { stmt ->
                stmt.executeQuery().use { rs ->
                    rs.next()
                    json.parseToJsonElement(rs.getString(1)).let { it as JsonObject }
                }
            }
        }
    }

    private fun loadSingleReviewRow(db: ExposedDatabase): StoredReviewRow = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement("SELECT author_email, content::text FROM reviews LIMIT 1").use { stmt ->
                stmt.executeQuery().use { rs ->
                    rs.next()
                    StoredReviewRow(
                        authorEmail = rs.getString("author_email"),
                        content = json.parseToJsonElement(rs.getString("content")).let { it as JsonObject }
                    )
                }
            }
        }
    }

    @Serializable
    private data class SubmitReviewPayload(
        val displayName: String,
        val reviewFormId: String,
        val reviewFormVersion: Int,
        val answers: List<ReviewAnswerRequest>,
    )

    @Serializable
    private data class SubmitReviewerShareReviewPayload(
        val reviewFormId: String,
        val reviewFormVersion: Int,
        val answers: List<ReviewAnswerRequest>,
    )

    private data class StoredReviewRow(
        val authorEmail: String?,
        val content: JsonObject,
    )

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
