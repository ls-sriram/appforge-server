package com.appforge.server.infrastructure

import com.appforge.server.utils.Mapper

interface Repository<DOMAIN> : DataSource<DOMAIN> {
    suspend fun merge(id: String, update: Map<String, Any?>): Resource<Unit>
    suspend fun setIfAbsent(id: String, initial: Map<String, Any?>): Resource<Boolean>
    suspend fun findFirstByField(fieldName: String, value: Any): Resource<DOMAIN?>
    suspend fun query(
        filters: Map<String, Any?> = emptyMap(),
        orderBy: String? = null,
        direction: SortDirection = SortDirection.ASCENDING,
        limit: Long? = null
    ): Resource<List<DOMAIN>>
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

interface RepositoryFactory {
    fun <DOMAIN> create(
        collectionName: String,
        mapper: Mapper<DOMAIN, Map<String, Any?>>
    ): Repository<DOMAIN>
}
