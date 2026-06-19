package com.appforge.server.services.reviews

import com.appforge.server.config.AppEnv
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.clients.OpenAIClient
import com.appforge.server.services.openai.OpenAIService
import com.appforge.server.services.reviews.ai.AIReviewWorker
import com.appforge.server.services.reviews.ai.EntityTextContentResolverRegistry
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.ai.ReviewPipelineFactory
import com.appforge.server.services.reviews.ai.RecordingTextContentResolver
import com.appforge.server.services.reviews.ai.DocumentTextContentResolver
import com.appforge.server.services.reviews.repository.ProfileRepository
import com.appforge.server.services.reviews.repository.ReviewRepository
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.documents.repository.SqlDocumentRepository
import com.appforge.server.infrastructure.ExposedDatabase
import java.time.Clock

class ReviewProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : ReviewServices {
    private val clock: Clock = Clock.systemUTC()

    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val openAiService: OpenAIService by lazy {
        OpenAIService(
            openAIClient = OpenAIClient.getInstance(env),
            storageClient = core.storage,
            env = env,
        )
    }

    private val textContentResolverRegistry: EntityTextContentResolverRegistry by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Text content resolver registry requires SQL database")
        val documentRepository = SqlDocumentRepository(relationalDb)
        EntityTextContentResolverRegistry(
            resolversByType = mapOf(
                "recording" to RecordingTextContentResolver(),
                "audio" to RecordingTextContentResolver(),
                "document" to DocumentTextContentResolver(documentRepository),
            )
        )
    }

    private val reviewContentResolver: ReviewContentResolver by lazy {
        ReviewContentResolver(
            storage = core.storage,
            uploadMetadataRepository = core.uploadMetadataRepository,
            textContentResolverRegistry = textContentResolverRegistry,
        )
    }

    private val reviewPipelineFactory: ReviewPipelineFactory by lazy {
        ReviewPipelineFactory(
            openAIService = openAiService,
            contentResolver = reviewContentResolver
        )
    }

    private val reviewRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Review repository requires SQL database")
        ReviewRepository(relationalDb)
    }

    private val profileRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Profile repository requires SQL database")
        ProfileRepository(sqlDatabase = relationalDb, clock = clock)
    }

    private val aiReviewWorker: AIReviewWorker by lazy {
        AIReviewWorker(
            reviewRepository = reviewRepository,
            pipelineFactory = reviewPipelineFactory,
            clock = clock,
        )
    }

    private val reviewService: ReviewService by lazy {
        ReviewService(
            reviewRepository = reviewRepository,
            profileRepository = profileRepository,
            aiReviewWorker = aiReviewWorker,
            clock = clock,
        )
    }

    override val reviewUseCases: ReviewUseCases by lazy {
        ReviewUseCasesImpl(
            reviewService = reviewService
        )
    }
}
