package com.appforge.server.infrastructure

import com.appforge.server.utils.Mapper

/**
 * Typed repository built on top of the abstract [Database] interface.
 *
 * This is the primary data access point for domain services.
 * It wraps raw database operations with type safety via a [Mapper].
 *
 * ```
 * DomainService
 *   → DatabaseRepository<User> (typed, with UserMapper)
 *     → Database (raw key-value)
 *       ├── ExposedDatabase     (production — PostgreSQL JSONB)
 *       └── InMemoryDatabase    (testing)
 * ```
 */
class DatabaseRepository<DOMAIN>(
    private val database: Database,
    private val collection: String,
    private val mapper: Mapper<DOMAIN, Map<String, Any?>>,
) : Repository<DOMAIN> {

    override suspend fun create(id: String, data: DOMAIN): Resource<String> =
        database.create(collection, id, mapper.toDoc(data))

    override suspend fun get(id: String): Resource<DOMAIN> {
        return when (val result = database.get(collection, id)) {
            is Resource.Success -> Resource.Success(mapper.fromDoc(id, result.data))
            is Resource.Error -> Resource.Error(result.exception)
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun update(id: String, data: DOMAIN): Resource<Unit> =
        database.update(collection, id, mapper.toDoc(data))

    override suspend fun delete(id: String): Resource<Unit> =
        database.delete(collection, id)

    override suspend fun merge(id: String, update: Map<String, Any?>): Resource<Unit> =
        database.merge(collection, id, update)

    override suspend fun setIfAbsent(id: String, initial: Map<String, Any?>): Resource<Boolean> =
        database.setIfAbsent(collection, id, initial)

    override suspend fun findFirstByField(fieldName: String, value: Any): Resource<DOMAIN?> {
        return when (val result = database.findFirstByField(collection, fieldName, value)) {
            is Resource.Success -> Resource.Success(result.data?.let { mapper.fromDoc(id = "", it) })
            is Resource.Error -> Resource.Error(result.exception)
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun query(
        filters: Map<String, Any?>,
        orderBy: String?,
        direction: SortDirection,
        limit: Long?,
    ): Resource<List<DOMAIN>> {
        val dbQuery = DatabaseQuery(filters, orderBy, direction, limit)
        return when (val result = database.query(collection, dbQuery)) {
            is Resource.Success -> Resource.Success(
                result.data.mapIndexed { index, doc ->
                    mapper.fromDoc("generated-$index", doc)
                }
            )
            is Resource.Error -> Resource.Error(result.exception)
            Resource.Loading -> Resource.Loading
        }
    }
}

/**
 * Factory that creates [DatabaseRepository] instances.
 * The database instance is injected at construction time.
 */
class DatabaseRepositoryFactory(
    private val database: Database,
) : RepositoryFactory {
    override fun <DOMAIN> create(
        collectionName: String,
        mapper: Mapper<DOMAIN, Map<String, Any?>>,
    ): Repository<DOMAIN> =
        DatabaseRepository(database, collectionName, mapper)
}
