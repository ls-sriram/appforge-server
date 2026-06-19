package com.appforge.server.services.reviews.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    for ((key, value) in this@toJsonObject) {
        put(key, value.toJsonElement())
    }
}

internal fun JsonObject.toReviewContentMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toDomainValue() }

internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Byte, is Short, is Int, is Long -> JsonPrimitive(this as Number)
    is Float, is Double -> JsonPrimitive(this as Number)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        for ((key, value) in this@toJsonElement) {
            put(key.toString(), value.toJsonElement())
        }
    }
    is Iterable<*> -> buildJsonArray {
        for (item in this@toJsonElement) {
            add(item.toJsonElement())
        }
    }
    is Array<*> -> buildJsonArray {
        for (item in this@toJsonElement) {
            add(item.toJsonElement())
        }
    }
    else -> JsonPrimitive(toString())
}

internal fun JsonElement.toDomainValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> toReviewContentMap()
    is JsonArray -> map { it.toDomainValue() }
    is JsonPrimitive -> {
        if (isString) {
            content
        } else {
            content.toBooleanStrictOrNull()
                ?: content.toLongOrNull()
                ?: content.toDoubleOrNull()
                ?: content
        }
    }
}
