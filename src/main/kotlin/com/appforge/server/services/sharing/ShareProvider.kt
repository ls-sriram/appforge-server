package com.appforge.server.services.sharing

import com.appforge.server.config.AppEnv
import com.appforge.server.services.CoreServices
import com.appforge.server.clients.OpenAIClient
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.repository.SqlUserRepository
import com.appforge.server.services.documents.repository.SqlDocumentRepository
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.ZeptoMailEmailService
import com.appforge.server.services.forms.repository.FormRepository
import com.appforge.server.services.openai.OpenAIService
import com.appforge.server.services.recordings.repository.RecordingEntityDescriptorResolver
import com.appforge.server.services.recordings.repository.SqlRecordingRepository
import com.appforge.server.services.reviews.ai.AIReviewWorker
import com.appforge.server.services.reviews.ai.DocumentTextContentResolver
import com.appforge.server.services.reviews.ai.EntityTextContentResolverRegistry
import com.appforge.server.services.reviews.ai.RecordingTextContentResolver
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.ai.ReviewPipelineFactory
import com.appforge.server.services.reviews.repository.ProfileRepository
import com.appforge.server.services.reviews.repository.ReviewRepository
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.sharing.repository.DocumentEntityDescriptorResolver
import com.appforge.server.services.sharing.repository.EntityDescriptorResolverRegistry
import com.appforge.server.services.sharing.repository.ReviewerShareRepository
import com.appforge.server.services.sharing.repository.ShareEntityRepository
import com.appforge.server.services.sharing.repository.ShareRepository
import com.appforge.server.services.sharing.services.ShareService
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.time.UtcTimestampProvider
import java.time.Clock

class ShareProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : ShareServices {
    private val clock: Clock = Clock.systemUTC()

    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val shareRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Share repository requires SQL database")
        ShareRepository(relationalDb)
    }

    private val reviewerShareRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Reviewer share repository requires SQL database")
        ReviewerShareRepository(relationalDb)
    }

    private val shareService: ShareService by lazy {
        ShareService(
            shareRepository = shareRepository,
            timestampProvider = UtcTimestampProvider,
        )
    }

    private val emailService: EmailService by lazy {
        ZeptoMailEmailService(env.email)
    }

    private val publicBaseUrl: String
        get() = env.runtime.publicBaseUrl

    private val userRepository by lazy {
        SqlUserRepository(core.database)
    }

    private val reviewRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Review repository requires SQL database")
        ReviewRepository(relationalDb)
    }

    private val profileRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Profile repository requires SQL database")
        ProfileRepository(relationalDb, clock)
    }

    private val reviewContentResolver: ReviewContentResolver by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Review content resolver requires SQL database")
        val documentRepository = SqlDocumentRepository(relationalDb)
        ReviewContentResolver(
            storage = core.storage,
            uploadMetadataRepository = core.uploadMetadataRepository,
            textContentResolverRegistry = EntityTextContentResolverRegistry(
                resolversByType = mapOf(
                    "recording" to RecordingTextContentResolver(),
                    "audio" to RecordingTextContentResolver(),
                    "document" to DocumentTextContentResolver(documentRepository),
                )
            ),
        )
    }

    private val openAiService: OpenAIService by lazy {
        OpenAIService(
            openAIClient = OpenAIClient.getInstance(env),
            storageClient = core.storage,
            env = env,
        )
    }

    private val reviewService: ReviewService by lazy {
        ReviewService(
            reviewRepository = reviewRepository,
            profileRepository = profileRepository,
            aiReviewWorker = AIReviewWorker(
                reviewRepository = reviewRepository,
                pipelineFactory = ReviewPipelineFactory(
                    openAIService = openAiService,
                    contentResolver = reviewContentResolver,
                ),
                clock = clock,
            ),
            clock = clock,
        )
    }

    private val recordingRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Recording repository requires SQL database")
        SqlRecordingRepository(relationalDb)
    }

    private val shareEntityRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Share entity repository requires SQL database")
        val documentRepository = SqlDocumentRepository(relationalDb)
        ShareEntityRepository(
            resolverRegistry = EntityDescriptorResolverRegistry(
                resolversByType = mapOf(
                    "recording" to RecordingEntityDescriptorResolver(recordingRepository),
                    "audio" to RecordingEntityDescriptorResolver(recordingRepository),
                    "document" to DocumentEntityDescriptorResolver(documentRepository),
                )
            )
        )
    }

    private val formRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Form repository requires SQL database")
        FormRepository(relationalDb)
    }

    override val shareUseCases: ShareUseCases by lazy {
        ShareUseCasesImpl(
            shareService = shareService,
            emailService = emailService,
            publicBaseUrl = publicBaseUrl
        )
    }

    override val reviewerShareUseCases: ReviewerShareUseCases by lazy {
        ReviewerShareUseCasesImpl(
            reviewerShareRepository = reviewerShareRepository,
            userRepository = userRepository,
            authService = authService,
            emailService = emailService,
            publicBaseUrl = publicBaseUrl,
            reviewService = reviewService,
            contentResolver = reviewContentResolver,
            shareEntityRepository = shareEntityRepository,
            formRepository = formRepository,
            uploadMetadataRepository = core.uploadMetadataRepository,
            signedGetUrlIssuer = core.uploadAccessUrlIssuer,
            uploadExpirySeconds = env.uploads.uploadUrlExpirySeconds.toLong(),
        )
    }
}
