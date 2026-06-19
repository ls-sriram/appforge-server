package com.appforge.server.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.appforge.server.infrastructure.sql.SqlRequestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Types

/**
 * PostgreSQL implementation of the [Database] interface using HikariCP + Flyway migrations.
 *
 * This replaces the old SqlDatabase with Flyway-based migration management.
 * The core CRUD logic remains the same (raw JDBC), but schema is now managed by Flyway.
 */
class ExposedDatabase(
    connectionUrl: String,
    username: String,
    password: String,
    poolSize: Int = 10,
) : Database {

    override val name: String = "sql"

    private val logger = LoggerFactory.getLogger(ExposedDatabase::class.java)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val dataSource: HikariDataSource

    init {
        val config = HikariConfig().apply {
            jdbcUrl = connectionUrl
            this.username = username
            this.password = password
            this.maximumPoolSize = poolSize
            this.minimumIdle = 2
            this.idleTimeout = 30000
            this.maxLifetime = 1800000
            this.connectionTimeout = 30000
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("stringtype", "unspecified")
        }
        dataSource = HikariDataSource(config)
        logger.info("ExposedDatabase initialized with HikariCP (poolSize=$poolSize)")
    }

    /**
     * Runs Flyway migrations before any database operations.
     */
    fun runMigrations() {
        val migrationManager = MigrationManager()
        migrationManager.migrate(dataSource)
    }

    // ─── Core CRUD ───────────────────────────────────────────────────────

    override suspend fun create(
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ): Resource<String> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun get(
        collection: String,
        id: String,
    ): Resource<Map<String, Any?>> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun update(
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ): Resource<Unit> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun delete(
        collection: String,
        id: String,
    ): Resource<Unit> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    // ─── Extended operations ─────────────────────────────────────────────

    override suspend fun merge(
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ): Resource<Unit> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun setIfAbsent(
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ): Resource<Boolean> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun findFirstByField(
        collection: String,
        field: String,
        value: Any,
    ): Resource<Map<String, Any?>?> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun query(
        collection: String,
        query: DatabaseQuery,
    ): Resource<List<Map<String, Any?>>> = Resource.Error(UnsupportedOperationException("Document collections are removed. Use typed SQL repositories."))

    override suspend fun <T> transaction(
        block: suspend TransactionContext.() -> T,
    ): Resource<T> {
        var conn: Connection? = null
        return try {
            conn = dataSource.connection
            conn.autoCommit = false
            applyRequestSqlContext(conn)
            val ctx = ExposedTransactionContext(conn, this)
            val result = withContext(Dispatchers.IO) {
                kotlinx.coroutines.runBlocking { ctx.block() }
            }
            conn.commit()
            Resource.Success(result)
        } catch (e: Exception) {
            var rollbackError: Exception? = null
            try {
                conn?.rollback()
            } catch (re: Exception) {
                rollbackError = re
                logger.error("Transaction rollback failed: ${re.message}", re)
            }
            if (rollbackError != null) {
                e.addSuppressed(rollbackError)
            }
            Resource.Error(e)
        } finally {
            try { conn?.autoCommit = true } catch (_: Exception) {}
            try { conn?.close() } catch (_: Exception) {}
        }
    }

    suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                applyRequestSqlContext(conn)
                block(conn)
            }
        }

    private fun applyRequestSqlContext(conn: Connection) {
        val userId = SqlRequestContext.currentUserId()
        conn.prepareStatement("SELECT set_config('app.user_id', ?, true)").use { stmt ->
            if (userId == null) {
                stmt.setNull(1, Types.VARCHAR)
            } else {
                stmt.setString(1, userId)
            }
            stmt.execute()
        }
    }

    // ─── JSON helpers ────────────────────────────────────────────────────

    internal fun toJson(data: Map<String, Any?>): String {
        val obj = buildJsonObject {
            for ((k, v) in data) {
                put(k, toElement(v))
            }
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Map<*, *> -> buildJsonObject { for ((k, vv) in v) put(k.toString(), toElement(vv)) }
        is List<*> -> buildJsonArray { v.forEach { add(toElement(it)) } }
        else -> JsonPrimitive(v.toString())
    }

    private fun fromJson(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        val element = Json.parseToJsonElement(json)
        if (element !is JsonObject) return emptyMap()
        return element.mapValues { (_, v) -> fromElement(v) }
    }

    private fun fromElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        is JsonObject -> element.mapValues { (_, v) -> fromElement(v) }
        is JsonArray -> element.map { fromElement(it) }
    }

    // ─── Connection helper ───────────────────────────────────────────────

    private suspend fun <T> execute(operation: String, block: (Connection) -> Resource<T>): Resource<T> {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                try {
                    block(conn)
                } catch (e: Exception) {
                    logger.error("SQL operation '$operation' failed: ${e.message}", e)
                    Resource.Error(e)
                }
            }
        }
    }

    fun getDataSource(): HikariDataSource = dataSource

    fun close() {
        dataSource.close()
    }

    companion object {
        private var instance: ExposedDatabase? = null

        fun getInstance(
            url: String,
            user: String,
            password: String,
            poolSize: Int = 10,
        ): ExposedDatabase = instance ?: synchronized(this) {
            instance ?: ExposedDatabase(url, user, password, poolSize).also { instance = it }
        }
    }
}

/** Transaction context wrapping a JDBC Connection. */
private class ExposedTransactionContext(
    private val conn: Connection,
    private val db: ExposedDatabase,
) : TransactionContext {
    override suspend fun create(collection: String, id: String, data: Map<String, Any?>) {
        throw UnsupportedOperationException("Document collections are removed. Use typed SQL repositories.")
    }

    override suspend fun get(collection: String, id: String): Map<String, Any?>? {
        throw UnsupportedOperationException("Document collections are removed. Use typed SQL repositories.")
    }

    override suspend fun update(collection: String, id: String, data: Map<String, Any?>) {
        throw UnsupportedOperationException("Document collections are removed. Use typed SQL repositories.")
    }

    override suspend fun delete(collection: String, id: String) {
        throw UnsupportedOperationException("Document collections are removed. Use typed SQL repositories.")
    }

    private fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Map<*, *> -> buildJsonObject { for ((k, vv) in v) put(k.toString(), toElement(vv)) }
        is List<*> -> buildJsonArray { v.forEach { add(toElement(it)) } }
        else -> JsonPrimitive(v.toString())
    }

    private fun fromElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        is JsonObject -> element.mapValues { (_, v) -> fromElement(v) }
        is JsonArray -> element.map { fromElement(it) }
    }

    private val Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
}
