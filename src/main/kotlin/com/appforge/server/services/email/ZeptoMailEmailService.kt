package com.appforge.server.services.email

import com.appforge.server.config.options.EmailOptions
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import com.appforge.server.services.email.templates.EmailTemplates

@Serializable
private data class ZeptoEmailAddress(val address: String, val name: String? = null)

@Serializable
private data class ZeptoTo(val email_address: ZeptoEmailAddress)

@Serializable
private data class ZeptoPayload(
    val from: ZeptoEmailAddress,
    val to: List<ZeptoTo>,
    val subject: String,
    val htmlbody: String? = null,
    val textbody: String? = null
)

class ZeptoMailEmailService(private val options: EmailOptions) : EmailService {
    private val logger = LoggerFactory.getLogger(ZeptoMailEmailService::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    override suspend fun sendPaymentConfirmation(
        to: String,
        amount: String,
        currency: String,
        planName: String,
        transactionId: String
    ) {
        val content = EmailTemplates.paymentConfirmation(
            amount = amount,
            currency = currency,
            planName = planName,
            transactionId = transactionId
        )
        sendEmail(to, content.subject, content.html, isHtml = true)
    }

    override suspend fun sendEmail(
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean
    ) {
        if (!options.enabled) {
            logger.info("Email service disabled via EMAIL_ENABLED=false. Skipping email to: $to")
            return
        }
        if (options.zeptoMailSendToken.isBlank()) {
            logger.warn("ZeptoMail Send Token is missing. Skipping email to: $to. Subject: $subject")
            return
        }

        val obfuscatedToken = if (options.zeptoMailSendToken.length > 8) {
            options.zeptoMailSendToken.take(4) + "..." + options.zeptoMailSendToken.takeLast(4)
        } else {
            "***"
        }

        logger.info("[ZeptoMail] Preparing to send email. URL: ${options.zeptoMailApiUrl}, Mode: ${if (isHtml) "HTML" else "Text"}, Token: $obfuscatedToken")

        withContext(Dispatchers.IO) {
            try {
                val payload = ZeptoPayload(
                    from = ZeptoEmailAddress(options.fromEmail, options.fromName),
                    to = listOf(ZeptoTo(ZeptoEmailAddress(to))),
                    subject = subject,
                    htmlbody = if (isHtml) content else null,
                    textbody = if (!isHtml) content else null
                )

                val response: HttpResponse = client.post(options.zeptoMailApiUrl) {
                    header("Authorization", "Zoho-enczapikey ${options.zeptoMailSendToken}")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

                if (response.status.isSuccess()) {
                    logger.info("[ZeptoMail] Email sent successfully to: $to. Status: ${response.status}")
                } else {
                    val body = response.bodyAsText()
                    logger.error("[ZeptoMail] Failed to send email to: $to. Status: ${response.status}, Body: $body")
                }
            } catch (ex: Exception) {
                logger.error("[ZeptoMail] Unexpected error during email transmission: ${ex.message}", ex)
            }
        }
    }
}
