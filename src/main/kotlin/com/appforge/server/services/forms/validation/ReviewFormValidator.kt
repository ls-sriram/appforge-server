package com.appforge.server.services.forms.validation

import com.appforge.server.api.reviews.ReviewAnswerRequest
import com.appforge.server.api.reviews.ReviewTemplate

object ReviewFormValidator {
    fun validateAnswers(template: ReviewTemplate, answers: List<ReviewAnswerRequest>): String? {
        val normalizedIds = answers.map { it.fieldId.trim() }
        if (normalizedIds.size != normalizedIds.toSet().size) {
            return "Duplicate fieldId entries are not allowed."
        }
        val byFieldId = answers.associateBy { it.fieldId.trim() }
        for (field in template.fields) {
            val answer = byFieldId[field.id]
            if (answer == null) {
                if (field.required) return "Missing required field: ${field.id}"
                continue
            }
            when (field.type) {
                "single_select" -> {
                    if (answer.optionIds.size != 1) return "Field ${field.id} expects exactly one option."
                    val optionId = answer.optionIds.first()
                    if (field.options.none { it.id == optionId }) return "Field ${field.id} has invalid option."
                }
                "multi_select" -> {
                    val validIds = field.options.map { it.id }.toSet()
                    if (answer.optionIds.any { it !in validIds }) return "Field ${field.id} has invalid option."
                }
                "text" -> {
                    val value = answer.textValue?.trim().orEmpty()
                    if (field.required && value.isBlank()) return "Field ${field.id} requires text."
                }
            }
        }
        return null
    }
}
