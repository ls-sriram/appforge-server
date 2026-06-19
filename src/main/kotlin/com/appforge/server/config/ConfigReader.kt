package com.appforge.server.config

import com.typesafe.config.Config

class ConfigReader(
    private val config: Config,
    private val env: Map<String, String>
) {
    fun string(key: String): String? =
        env[key]?.takeIf { it.isNotBlank() } ?: config.takeIf { it.hasPath(key) }?.getString(key)

    fun requiredString(key: String): String =
        string(key)?.takeIf { it.isNotBlank() }
            ?: error("Missing required config value: $key")

    fun int(key: String): Int? =
        env[key]?.toIntOrNull()
            ?: config.takeIf { it.hasPath(key) }?.getInt(key)

    fun long(key: String): Long? =
        env[key]?.toLongOrNull()
            ?: config.takeIf { it.hasPath(key) }?.getLong(key)

    fun requiredLong(key: String): Long =
        long(key) ?: error("Missing required config value: $key")

    fun bool(key: String): Boolean? =
        env[key]?.toBooleanStrictOrNull()
            ?: config.takeIf { it.hasPath(key) }?.getBoolean(key)

    fun stringList(key: String): List<String> =
        env[key]
            ?.let { raw ->
                val trimmed = raw.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    trimmed
                        .removePrefix("[")
                        .removeSuffix("]")
                        .split(",")
                        .map { it.trim().trim('"') }
                        .filter { it.isNotEmpty() }
                } else {
                    trimmed
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
            }
            ?: config.takeIf { it.hasPath(key) }?.getStringList(key)?.toList()
            ?: emptyList()
}
