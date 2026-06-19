// ========== DataSource.kt ==========
package com.appforge.server.infrastructure

interface DataSource<T> {
    suspend fun create(id: String, data: T): Resource<String>
    suspend fun get(id: String): Resource<T>
    suspend fun update(id: String, data: T): Resource<Unit>
    suspend fun delete(id: String): Resource<Unit>
}
