package com.appforge.server.clients

import com.appforge.server.config.AppEnv
import com.dodopayments.api.client.DodoPaymentsClient as DodoPaymentsSdkClient
import com.dodopayments.api.client.okhttp.DodoPaymentsOkHttpClient
import com.dodopayments.api.models.checkoutsessions.CheckoutSessionRequest
import com.dodopayments.api.models.checkoutsessions.CheckoutSessionResponse
import com.dodopayments.api.models.products.Product
import com.dodopayments.api.models.products.ProductListPage
import com.dodopayments.api.models.subscriptions.Subscription
import com.dodopayments.api.models.subscriptions.SubscriptionUpdateParams
import org.slf4j.LoggerFactory

interface DodoPaymentsDataClient {
    fun listProducts(): ProductListPage
    fun retrieveProduct(productId: String): Product
    fun createCheckoutSession(request: CheckoutSessionRequest): CheckoutSessionResponse
    fun cancelSubscriptionAtPeriodEnd(subscriptionId: String): Subscription
}

class DodoPaymentsClient(env: AppEnv) : DodoPaymentsDataClient {
    private val logger = LoggerFactory.getLogger(DodoPaymentsClient::class.java)

    companion object {
        @Volatile
        private var instance: DodoPaymentsClient? = null

        fun getInstance(env: AppEnv): DodoPaymentsClient =
            instance ?: synchronized(this) {
                instance ?: DodoPaymentsClient(env).also { instance = it }
            }
    }

    private val client: DodoPaymentsSdkClient

    init {
        logger.info("Initializing DodoPaymentsClient with baseUrl: {} and apiKey prefix: {}", env.dodoPayments.dodoPaymentsBaseUrl, env.dodoPayments.dodoPaymentsApiKey.take(8))
        client = DodoPaymentsOkHttpClient.builder()
            .bearerToken(env.dodoPayments.dodoPaymentsApiKey)
            .baseUrl(env.dodoPayments.dodoPaymentsBaseUrl)
            .build()
    }

    override fun listProducts(): ProductListPage =
        client.products().list()

    override fun retrieveProduct(productId: String): Product =
        client.products().retrieve(productId)

    override fun createCheckoutSession(request: CheckoutSessionRequest): CheckoutSessionResponse {
        return client.checkoutSessions().create(request)
    }

    override fun cancelSubscriptionAtPeriodEnd(subscriptionId: String): Subscription {
        val request = SubscriptionUpdateParams.builder()
            .subscriptionId(subscriptionId)
            .cancelAtNextBillingDate(true)
            .build()
        return client.subscriptions().update(request)
    }
}
