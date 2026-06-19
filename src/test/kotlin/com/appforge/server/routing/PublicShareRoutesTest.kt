package com.appforge.server.routing

import com.appforge.server.api.reviews.SubmitReviewRequest
import com.appforge.server.api.reviews.ReviewAnswerRequest
import com.appforge.server.api.reviews.ReviewTemplate
import com.appforge.server.api.reviews.ReviewTemplateField
import com.appforge.server.api.reviews.ReviewTemplateOption
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.api.sharing.PublicEntityResponse
import com.appforge.server.api.sharing.PublicShareMetadata
import com.appforge.server.infrastructure.time.timestampFromEpochMilli
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.RecordingMetadata
import com.appforge.server.services.sharing.PublicShareServices
import com.appforge.server.services.sharing.PublicShareUseCases
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicShareRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `get share returns gone when invalid`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.getPublicShare("token") } throws com.appforge.server.middleware.GoneException("gone")

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        assertFailsWith<com.appforge.server.middleware.GoneException> {
            client.get("/shares/token")
        }
    }

    @Test
    fun `get share returns canonical share envelope`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.getPublicShare("token") } returns PublicEntityResponse(
            share = PublicShareMetadata(
                token = "token",
                entityType = "recording",
                entityId = "r1",
                accessMode = "public_link",
                expiresAt = com.appforge.server.api.ProtoTimestamp(seconds = 1_700_000_000L, nanos = 0),
                revokedAt = null,
            ),
            entity = PublicEntity(
                id = "r1",
                category = "recording",
                title = "Shared recording",
                content = null,
            )
        )

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        val response = client.get("/shares/token")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<PublicEntityResponse>(response.body<String>())
        assertEquals("token", body.share.token)
        assertEquals("recording", body.share.entityType)
        assertEquals("r1", body.share.entityId)
    }

    @Test
    fun `submit review returns gone when share invalid`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.submitReview(any(), any()) } throws com.appforge.server.middleware.GoneException("Expired or Revoked")

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        assertFailsWith<com.appforge.server.middleware.GoneException> {
            client.post("/shares/token/reviews") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(json.encodeToString(SubmitReviewRequest(
                    displayName = "Reviewer",
                    reviewFormId = "review_form_recording_dummy_v1",
                    reviewFormVersion = 1,
                    answers = listOf(
                        ReviewAnswerRequest(fieldId = "overall_rating", optionIds = listOf("4")),
                        ReviewAnswerRequest(fieldId = "comments", textValue = "hello"),
                    ),
                )))
            }
        }
    }

    @Test
    fun `reviews route returns template`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.getReviewTemplate("token") } returns ReviewTemplateResponse(
            reviewForm = ReviewTemplate(
                id = "review_form_recording_dummy_v1",
                version = 1,
                entityType = "recording",
                name = "Recording Review Form",
                fields = listOf(
                    ReviewTemplateField(
                        id = "overall_rating",
                        label = "Overall rating",
                        type = "single_select",
                        required = true,
                        options = listOf(
                            ReviewTemplateOption(id = "1", label = "1"),
                            ReviewTemplateOption(id = "2", label = "2"),
                        ),
                    )
                )
            )
        )

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        val response = client.get("/reviews/token")
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { publicShareUseCases.getReviewTemplate("token") }
    }

    @Test
    fun `explicit reviews route submits review`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.submitReview(any(), any()) } returns com.appforge.server.api.reviews.ReviewResponse(
            id = "rev-1",
            authorRole = "external",
            authorId = null,
            authorName = "Reviewer",
            authorEmail = null,
            content = mapOf("text" to "hello"),
            createdAtTimestamp = 1_700_000_000_000L,
        )

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        val response = client.post("/reviews/token") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(SubmitReviewRequest(
                displayName = "Reviewer",
                reviewFormId = "review_form_recording_dummy_v1",
                reviewFormVersion = 1,
                answers = listOf(
                    ReviewAnswerRequest(fieldId = "overall_rating", optionIds = listOf("4")),
                    ReviewAnswerRequest(fieldId = "comments", textValue = "hello"),
                ),
            )))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        coVerify(exactly = 1) { publicShareUseCases.submitReview("token", any()) }
    }

    @Test
    fun `recording content route returns bytes`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        val bytes = "audio".toByteArray()
        coEvery { publicShareUseCases.getSharedRecordingContent("token") } returns RecordingContent(
            metadata = RecordingMetadata(
                id = "r1",
                uid = "user-1",
                contentType = "audio/webm",
                sizeBytes = bytes.size.toLong(),
                durationSeconds = 2,
                createdAt = timestampFromEpochMilli(1_700_000_000_000L),
            ),
            audioBytes = bytes,
        )

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        val response = client.get("/shares/token/recording/content")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio/webm", response.headers[HttpHeaders.ContentType])
        coVerify(exactly = 1) { publicShareUseCases.getSharedRecordingContent("token") }
    }

    @Test
    fun `generic content alias returns bytes`() = testApplication {
        val publicShareUseCases = mockk<PublicShareUseCases>()
        val bytes = "audio".toByteArray()
        coEvery { publicShareUseCases.getSharedRecordingContent("token") } returns RecordingContent(
            metadata = RecordingMetadata(
                id = "r1",
                uid = "user-1",
                contentType = "audio/webm",
                sizeBytes = bytes.size.toLong(),
                durationSeconds = 2,
                createdAt = timestampFromEpochMilli(1_700_000_000_000L),
            ),
            audioBytes = bytes,
        )

        val services = buildPublicShareServices(publicShareUseCases = publicShareUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { publicShareRoutes(services) }
        }

        val response = client.get("/shares/token/content")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio/webm", response.headers[HttpHeaders.ContentType])
        coVerify(exactly = 1) { publicShareUseCases.getSharedRecordingContent("token") }
    }

    private fun buildPublicShareServices(
        publicShareUseCases: PublicShareUseCases = mockk(relaxed = true),
    ): PublicShareServices {
        return object : PublicShareServices {
            override val publicShareUseCases = publicShareUseCases
        }
    }
}
