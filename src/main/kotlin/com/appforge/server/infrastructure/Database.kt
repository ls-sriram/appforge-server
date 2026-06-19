package com.appforge.server.infrastructure

/**
 * Database provider types.
 * Only SQL is used in production. MEMORY is for testing.
 */
enum class DatabaseProvider {
    /** Relational database via JDBC — production primary */
    SQL,

    /** In-memory — for testing only */
    MEMORY,
}

/**
 * Query specification for database operations.
 */
data class DatabaseQuery(
    val filters: Map<String, Any?> = emptyMap(),
    val orderBy: String? = null,
    val direction: SortDirection = SortDirection.ASCENDING,
    val limit: Long? = null,
)

/**
 * Transaction context provided inside database transactions.
 */
interface TransactionContext {
    suspend fun create(collection: String, id: String, data: Map<String, Any?>)
    suspend fun get(collection: String, id: String): Map<String, Any?>?
    suspend fun update(collection: String, id: String, data: Map<String, Any?>)
    suspend fun delete(collection: String, id: String)
}

/**
 * Core database interface — all data access flows through this.
 *
 * Production uses PostgreSQL via ExposedDatabase (JSONB document store).
 * InMemoryDatabase is available for testing.
 *
 * ## Architecture
 *
 * ```
 * Route Handler
 *   → UseCase
 *     → Repository<T> (typed, with Mapper<T>)
 *       → Database (raw key-value operations)
 *         ├── ExposedDatabase   (production — PostgreSQL JSONB)
 *         └── InMemoryDatabase (testing)
 * ```
 *
 * ## Configuration
 *
 * ```
 * DATABASE_PRIMARY=sql
 * DATABASE_SQL_URL=jdbc:postgresql://localhost:5432/appforge
 * DATABASE_SQL_USER=appforge
 * DATABASE_SQL_PASSWORD=...
 * DATABASE_SQL_POOL_SIZE=10
 * ```
 */
interface Database {

    /** Human-readable name for logging and diagnostics. */
    val name: String

    /**
     * Create a document in the given collection.
     * Fails if the document already exists.
     */
    suspend fun create(collection: String, id: String, data: Map<String, Any?>): Resource<String>

    /**
     * Get a document by ID.
     * Returns [Resource.Error] with a "not found" exception if missing.
     */
    suspend fun get(collection: String, id: String): Resource<Map<String, Any?>>

    /**
     * Update (overwrite) a document.
     */
    suspend fun update(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>

    /**
     * Delete a document.
     */
    suspend fun delete(collection: String, id: String): Resource<Unit>

    /**
     * Partial update — merge the given fields into the existing document.
     * Creates the document if it doesn't exist.
     */
    suspend fun merge(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>

    /**
     * Create a document only if it doesn't already exist.
     * Returns `true` if created, `false` if already present.
     */
    suspend fun setIfAbsent(collection: String, id: String, data: Map<String, Any?>): Resource<Boolean>

    /**
     * Find the first document matching the given field/value equality.
     */
    suspend fun findFirstByField(
        collection: String,
        field: String,
        value: Any,
    ): Resource<Map<String, Any?>?>

    /**
     * Query documents with optional filtering, ordering, and pagination.
     */
    suspend fun query(
        collection: String,
        query: DatabaseQuery = DatabaseQuery(),
    ): Resource<List<Map<String, Any?>>>

    /**
     * Execute a block of operations atomically.
     * Not all implementations support transactions.
     * Unsupported implementations should throw [UnsupportedOperationException].
     */
    suspend fun <T> transaction(block: suspend TransactionContext.() -> T): Resource<T>
}
