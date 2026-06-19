package com.appforge.server.utils

interface WireEnum {
    val wire: String
}

inline fun <reified E> wireEnumFrom(
    value: String,
    fallback: E? = null,
): E where E : Enum<E>, E : WireEnum {
    return enumValues<E>().firstOrNull { it.wire == value }
        ?: fallback
        ?: throw IllegalArgumentException(
            "Unknown ${E::class.simpleName} wire value: $value",
        )
}
