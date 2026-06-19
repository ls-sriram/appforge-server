package com.appforge.server.infrastructure

import com.appforge.server.utils.Mapper

class InMemoryRepository<DOMAIN>(
    private val mapper: Mapper<DOMAIN, Map<String, Any?>>,
    private val store: MutableMap<String, Map<String, Any?>>
) : Repository<DOMAIN> {

    override suspend fun create(id: String, data: DOMAIN): Resource<String> {
        store[id] = mapper.toDoc(data)
        return Resource.Success(id)
    }

    override suspend fun get(id: String): Resource<DOMAIN> {
        val doc = store[id] ?: return Resource.Error(Exception("Document not found: $id"))
        return Resource.Success(mapper.fromDoc(id, doc))
    }

    override suspend fun update(id: String, data: DOMAIN): Resource<Unit> {
        store[id] = mapper.toDoc(data)
        return Resource.Success(Unit)
    }

    override suspend fun delete(id: String): Resource<Unit> {
        store.remove(id)
        return Resource.Success(Unit)
    }

    override suspend fun merge(id: String, update: Map<String, Any?>): Resource<Unit> {
        val current = store[id] ?: emptyMap()
        store[id] = current + update
        return Resource.Success(Unit)
    }

    override suspend fun setIfAbsent(id: String, initial: Map<String, Any?>): Resource<Boolean> {
        if (store.containsKey(id)) {
            return Resource.Success(false)
        }
        store[id] = initial
        return Resource.Success(true)
    }

    override suspend fun findFirstByField(fieldName: String, value: Any): Resource<DOMAIN?> {
        val entry = store.entries.firstOrNull { (_, doc) -> doc[fieldName] == value } ?: return Resource.Success(null)
        return Resource.Success(mapper.fromDoc(entry.key, entry.value))
    }

    override suspend fun query(
        filters: Map<String, Any?>,
        orderBy: String?,
        direction: SortDirection,
        limit: Long?
    ): Resource<List<DOMAIN>> {
        var entries = store.entries.asSequence()
        for ((field, value) in filters) {
            entries = entries.filter { (_, doc) -> doc[field] == value }
        }
        val comparator = if (orderBy != null) {
            Comparator<Map.Entry<String, Map<String, Any?>>> { left, right ->
                val leftValue = left.value[orderBy]
                val rightValue = right.value[orderBy]
                if (leftValue == null && rightValue == null) return@Comparator 0
                if (leftValue == null) return@Comparator -1
                if (rightValue == null) return@Comparator 1
                when {
                    leftValue is Number && rightValue is Number ->
                        leftValue.toDouble().compareTo(rightValue.toDouble())
                    leftValue is String && rightValue is String ->
                        leftValue.compareTo(rightValue)
                    leftValue is java.time.Instant && rightValue is java.time.Instant ->
                        leftValue.compareTo(rightValue)
                    leftValue is Boolean && rightValue is Boolean ->
                        leftValue.compareTo(rightValue)
                    else -> 0
                }
            }
        } else null
        val list = entries.toList().let { docs ->
            if (comparator == null) docs
            else {
                val sorted = docs.sortedWith(comparator)
                if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
            }
        }
        val limited = if (limit != null) list.take(limit.toInt()) else list
        return Resource.Success(limited.map { (id, doc) -> mapper.fromDoc(id, doc) })
    }
}

class InMemoryRepositoryFactory : RepositoryFactory {
    private val stores = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()

    override fun <DOMAIN> create(
        collectionName: String,
        mapper: Mapper<DOMAIN, Map<String, Any?>>
    ): Repository<DOMAIN> {
        val store = stores.getOrPut(collectionName) { linkedMapOf() }
        return InMemoryRepository(mapper, store)
    }
}
