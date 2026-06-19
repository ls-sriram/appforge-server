package com.appforge.server.infrastructure.sql

private val nameRegex = Regex("^\\s*--\\s*name:\\s*([a-zA-Z0-9_\\-\\.]+)\\s*$")

class NamedSql private constructor(
    private val queries: Map<String, String>,
) {
    fun query(name: String): String {
        return queries[name] ?: error("Missing SQL query: $name")
    }

    companion object {
        fun fromResource(resourcePath: String, classLoader: ClassLoader): NamedSql {
            val raw = classLoader.getResourceAsStream(resourcePath)?.bufferedReader()?.use { it.readText() }
                ?: error("SQL resource not found: $resourcePath")

            val result = linkedMapOf<String, String>()
            var currentName: String? = null
            val currentSql = StringBuilder()

            fun flush() {
                val name = currentName ?: return
                val sql = currentSql.toString().trim()
                if (sql.isNotEmpty()) {
                    result[name] = sql
                }
                currentName = null
                currentSql.clear()
            }

            raw.lineSequence().forEach { line ->
                val match = nameRegex.matchEntire(line)
                if (match != null) {
                    flush()
                    currentName = match.groupValues[1]
                } else if (currentName != null) {
                    currentSql.appendLine(line)
                }
            }
            flush()
            return NamedSql(result)
        }
    }
}

