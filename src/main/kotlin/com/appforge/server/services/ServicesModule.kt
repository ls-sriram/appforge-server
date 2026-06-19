package com.appforge.server.services

import com.appforge.server.config.AppEnv
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.billing.BillingServices
import com.appforge.server.services.documents.DocumentServices
import com.appforge.server.services.recordings.RecordingServices
import com.appforge.server.services.sharing.PublicShareServices
import com.appforge.server.services.reviews.ReviewServices
import com.appforge.server.services.sharing.ShareServices
import com.appforge.server.services.system.SystemServices
import com.appforge.server.services.tasks.TaskServices
import com.appforge.server.services.uploads.UploadServices
import com.appforge.server.services.auth.AuthProvider
import com.appforge.server.services.billing.BillingProvider
import com.appforge.server.services.documents.DocumentProvider
import com.appforge.server.services.recordings.RecordingProvider
import com.appforge.server.services.sharing.PublicShareProvider
import com.appforge.server.services.reviews.ReviewProvider
import com.appforge.server.services.sharing.ShareProvider
import com.appforge.server.services.system.SystemProvider
import com.appforge.server.services.tasks.TaskProvider
import com.appforge.server.services.uploads.UploadProvider

class ServicesModule(
    val core: CoreServices,
    val env: AppEnv,
) {
    fun uploadServices(): UploadServices = UploadProvider(core, env)
    fun authServices(): AuthServices = AuthProvider(core, env)
    fun systemServices(): SystemServices = SystemProvider(core, env)
    fun reviewServices(): ReviewServices = ReviewProvider(core, env)
    fun shareServices(): ShareServices = ShareProvider(core, env)
    fun billingServices(): BillingServices = BillingProvider(core, env)
    fun publicShareServices(): PublicShareServices = PublicShareProvider(core, env)
    fun recordingServices(): RecordingServices = RecordingProvider(core, env)
    fun documentServices(): DocumentServices = DocumentProvider(core, env)
    fun taskServices(): TaskServices = TaskProvider(core, env)
}
