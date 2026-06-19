package com.appforge.server.services.email.templates

import com.appforge.server.config.ProductDetails

data class EmailContent(
    val subject: String,
    val html: String,
    val text: String? = null
)

object EmailTemplates {
    private const val brandColor = "#20B6A1" // hsl(172 70% 42%)
    private const val brandOnColor = "#1F2937" // hsl(206 30% 18%)
    private const val surfaceBg = "#E4F1F2" // hsl(193 36% 92%)
    private const val surfaceCard = "#F1F6F7" // hsl(189 28% 96%)
    private const val mutedText = "#607580" // hsl(205 12% 38%)
    private fun layoutHtml(
        title: String,
        bodyHtml: String
    ): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: $brandOnColor; margin: 0; padding: 0; background-color: $surfaceBg; }
                    .container { max-width: 600px; margin: 40px auto; background: $surfaceCard; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }
                    .header { background: $brandColor; padding: 40px; text-align: center; }
                    .header h1 { color: $brandOnColor; margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }
                    .content { padding: 40px; }
                    .greeting { font-size: 18px; font-weight: 600; margin-bottom: 24px; }
                    .order-card { background: #F3F4F6; border-radius: 8px; padding: 24px; margin: 32px 0; border-left: 4px solid $brandColor; }
                    .order-row { display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 14px; }
                    .order-row:last-child { margin-bottom: 0; }
                    .label { color: $mutedText; }
                    .value { font-weight: 600; text-align: right; }
                    .cta-container { text-align: center; margin-top: 40px; }
                    .btn { background-color: $brandColor; color: $brandOnColor !important; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block; transition: background-color 0.2s; }
                    .signature { margin-top: 32px; }
                    .footer { text-align: center; padding: 32px 40px; color: $mutedText; font-size: 13px; border-top: 1px solid #E5E7EB; }
                </style>
                <title>$title</title>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>${ProductDetails.productName}</h1>
                    </div>
                    <div class="content">
                        $bodyHtml
                        <div class="signature">
                            <p>Thanks,<br>${ProductDetails.teamName}</p>
                        </div>
                    </div>
                    <div class="footer">
                        &copy; ${java.time.Year.now().value} ${ProductDetails.productName}. All rights reserved.<br>
                        ${ProductDetails.supportTagline}
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun paymentConfirmation(
        amount: String,
        currency: String,
        planName: String,
        transactionId: String
    ): EmailContent {
        val subject = "Order Confirmed - $planName"
        val bodyHtml = """
            <div class="greeting">Thank you for your purchase!</div>
            <p>Your subscription has been successfully upgraded. You now have full access to all features included in your plan.</p>
            
            <div class="order-card" style="background: #F3F4F6; border-radius: 8px; padding: 24px; margin: 32px 0; border-left: 4px solid $brandColor;">
                <div class="order-row">
                    <span class="label">Plan</span>
                    <span class="value" style="color: $brandColor;">$planName</span>
                </div>
                <div class="order-row">
                    <span class="label">Amount Paid</span>
                    <span class="value">$amount $currency</span>
                </div>
                <div class="order-row">
                    <span class="label">Transaction ID</span>
                    <span class="value" style="font-family: monospace; font-size: 12px;">$transactionId</span>
                </div>
            </div>

            <div class="cta-container" style="text-align: center; margin-top: 40px;">
                <a href="${ProductDetails.dashboardUrl}" class="btn" style="background-color: $brandColor; color: $brandOnColor !important; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;">Explore My Dashboard</a>
            </div>
        """.trimIndent()
        val htmlContent = layoutHtml(subject, bodyHtml)

        val textContent = "Order confirmed. Plan: $planName. Amount: $amount $currency. Transaction: $transactionId."

        return EmailContent(
            subject = subject,
            html = htmlContent,
            text = textContent
        )
    }

    fun earlyAccessInvite(inviteUrl: String): EmailContent {
        val subject = "Welcome to ${ProductDetails.productName}!"
        val bodyHtml = """
            <div class="greeting">Thanks for your interest in ${ProductDetails.productName}!</div>
            <p>You are all set for early access. We are excited to have you join us.</p>
            <p>
                When you are ready, create your account here:
                <a href="$inviteUrl">$inviteUrl</a>
            </p>
            <div class="cta-container" style="text-align: center; margin-top: 40px;">
                <a href="$inviteUrl" class="btn" style="background-color: $brandColor; color: $brandOnColor !important; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;">Create your account</a>
            </div>
            <p style="margin-top: 40px;">Good luck with your application cycle — we hope ${ProductDetails.productName} helps simplify planning, paperwork, and deadlines so you can focus on what matters.</p>
            <p>If you didn't request this, you can ignore this email.</p>
        """.trimIndent()
        val htmlContent = layoutHtml(subject, bodyHtml)
        val textContent = "You are all set for early access. Create your account: $inviteUrl. Good luck with your application cycle — we hope ${ProductDetails.productName} helps simplify planning, paperwork, and deadlines."

        return EmailContent(
            subject = subject,
            html = htmlContent,
            text = textContent
        )
    }

    fun reviewShareInvite(
        shareUrl: String,
        entityCategory: String
    ): EmailContent {
        val subject = "You've been invited to review a ${entityCategory.replace('_', ' ')}"
        val bodyHtml = """
            <div class="greeting">You have a new review request</div>
            <p>You have been invited to review a ${entityCategory.replace('_', ' ')}.</p>
            <p>
                Open the review link below (expires in 21 days):
                <a href="$shareUrl">$shareUrl</a>
            </p>
            <div class="cta-container" style="text-align: center; margin-top: 40px;">
                <a href="$shareUrl" class="btn" style="background-color: $brandColor; color: $brandOnColor !important; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;">Open review</a>
            </div>
        """.trimIndent()
        val htmlContent = layoutHtml(subject, bodyHtml)
        val textContent = "You have been invited to review a ${entityCategory.replace('_', ' ')}. Open: $shareUrl"

        return EmailContent(
            subject = subject,
            html = htmlContent,
            text = textContent
        )
    }

    fun planChanged(
        previousPlanName: String,
        newPlanName: String
    ): EmailContent {
        val subject = "Your ${ProductDetails.productName} Plan Has Changed"
        val bodyHtml = """
            <div class="greeting">Your plan has been updated</div>
            <p>We've updated your ${ProductDetails.productName} subscription plan.</p>
            <div class="order-card" style="background: #F3F4F6; border-radius: 8px; padding: 24px; margin: 32px 0; border-left: 4px solid $brandColor;">
                <div class="order-row">
                    <span class="label">Previous Plan</span>
                    <span class="value">$previousPlanName</span>
                </div>
                <div class="order-row">
                    <span class="label">New Plan</span>
                    <span class="value" style="color: $brandColor;">$newPlanName</span>
                </div>
            </div>
            <div class="cta-container" style="text-align: center; margin-top: 40px;">
                <a href="${ProductDetails.billingSettingsUrl}" class="btn" style="background-color: $brandColor; color: $brandOnColor !important; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;">View Billing Settings</a>
            </div>
        """.trimIndent()
        val htmlContent = layoutHtml(subject, bodyHtml)
        val textContent = "Your ${ProductDetails.productName} plan changed from $previousPlanName to $newPlanName."

        return EmailContent(
            subject = subject,
            html = htmlContent,
            text = textContent
        )
    }
}
