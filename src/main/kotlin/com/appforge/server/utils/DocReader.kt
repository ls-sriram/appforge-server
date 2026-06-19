package com.appforge.server.utils

import com.appforge.server.infrastructure.time.*

class DocReader(
        private val doc: Map<String, Any?>,
        private val id: String,
) {
    fun string(field: String): String = doc[field] as? String ?: error("Missing $field for $id")

    fun optionalString(field: String): String? = (doc[field] as? String)?.takeIf { it.isNotBlank() }

    fun int(field: String): Int =
            (doc[field] as? Number)?.toInt() ?: error("Missing $field for $id")

    fun long(field: String): Long =
            (doc[field] as? Number)?.toLong() ?: error("Missing $field for $id")

    fun instant(field: String): AppTimestamp = parseTimestamp(string(field))

    fun optionalInstant(field: String): AppTimestamp? = optionalString(field)?.let(AppTimestamp::parse)

    fun optionalLong(field: String): Long? = (doc[field] as? Number)?.toLong()

    inline fun <reified E> enum(field: String): E where E : Enum<E>, E : WireEnum =
            wireEnumFrom(string(field))

    inline fun <reified E> enumOr(
            field: String,
            fallback: E,
    ): E where E : Enum<E>, E : WireEnum = wireEnumFrom(string(field), fallback)

    fun map(field: String): Map<String, Any?> =
        (doc[field] as? Map<*, *>)?.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value }
        }?.toMap() ?: emptyMap()
}
