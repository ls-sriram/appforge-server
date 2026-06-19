package com.appforge.server.infrastructure

/**
 * In-memory database implementation for testing.
 * Thread-safe, non-persistent.
 */
class InMemoryDatabase : Database {
    override val name: String = "memory"
    private val store = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()

    override suspend fun create(collection: String, id: String, data: Map<String, Any?>): Resource<String> {
        val map = store.getOrPut(collection) { mutableMapOf() }
        if (map.containsKey(id)) return Resource.Error(Exception("Document already exists: $collection/$id"))
        map[id] = data
        return Resource.Success(id)
    }

    override suspend fun get(collection: String, id: String): Resource<Map<String, Any?>> {
        val doc = store[collection]?.get(id) ?: return Resource.Error(Exception("Document not found: $collection/$id"))
        return Resource.Success(doc)
    }

    override suspend fun update(collection: String, id: String, data: Map<String, Any?>): Resource<Unit> {
        store.getOrPut(collection) { mutableMapOf() }[id] = data
        return Resource.Success(Unit)
    }

    override suspend fun delete(collection: String, id: String): Resource<Unit> {
        store[collection]?.remove(id)
        return Resource.Success(Unit)
    }

    override suspend fun merge(collection: String, id: String, data: Map<String, Any?>): Resource<Unit> {
        val map = store.getOrPut(collection) { mutableMapOf() }
        map[id] = (map[id] ?: emptyMap()) + data
        return Resource.Success(Unit)
    }

    override suspend fun setIfAbsent(collection: String, id: String, data: Map<String, Any?>): Resource<Boolean> {
        val map = store.getOrPut(collection) { mutableMapOf() }
        if (map.containsKey(id)) return Resource.Success(false)
        map[id] = data
        return Resource.Success(true)
    }

    override suspend fun findFirstByField(collection: String, field: String, value: Any): Resource<Map<String, Any?>?> {
        val found = store[collection]?.values?.find { it[field] == value }
        return Resource.Success(found)
    }

    override suspend fun query(collection: String, query: DatabaseQuery): Resource<List<Map<String, Any?>>> {
        var results = store[collection]?.values?.toList() ?: return Resource.Success(emptyList())
        for ((f, v) in query.filters) { if (v != null) results = results.filter { it[f] == v } }
        if (query.limit != null) results = results.take(query.limit.toInt())
        return Resource.Success(results)
    }

    override suspend fun <T> transaction(block: suspend TransactionContext.() -> T): Resource<T> {
        return try {
            val ctx = object : TransactionContext {
                override suspend fun create(c: String, id: String, data: Map<String, Any?>) {
                    store.getOrPut(c) { mutableMapOf() }[id] = data
                }
                override suspend fun get(c: String, id: String): Map<String, Any?>? = store[c]?.get(id)
                override suspend fun update(c: String, id: String, data: Map<String, Any?>) {
                    store.getOrPut(c) { mutableMapOf() }[id] = data
                }
                override suspend fun delete(c: String, id: String) { store[c]?.remove(id) }
            }
            Resource.Success(ctx.block())
        } catch (e: Exception) { Resource.Error(e) }
    }

    fun clear() { store.clear() }
}
