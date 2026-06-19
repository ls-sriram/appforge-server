package com.appforge.server.services.openai.models

import kotlinx.serialization.Serializable

@Serializable
data class DocumentReviewContent(
    val score: Int,
    val summary: String,
    val positiveFeedback: List<String>,
    val improvementFeedback: List<String>
)

@Serializable
data class AudioReviewContent(
    val overallScore: Int,
    val summary: String,
    val contentScore: Int,
    val deliveryScore: Int,
    val actionableTips: List<String>
)

@Serializable
data class BenchPrepReviewContent(
    val score: Int,
    val visualAnalysis: String,
    val techniqueFeedback: List<String>,
    val nextSteps: List<String>
)
