// ========== GcsDataSource.kt ==========
package com.appforge.server.infrastructure

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GcsDataSource<T>(
        private val bucketName: String,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
        private val storage: Storage = StorageOptions.getDefaultInstance().service
) : DataSource<T> {

    override suspend fun create(id: String, data: T): Resource<String> =
            withContext(Dispatchers.IO) {
                try {
                    val jsonData = serializer(data).toByteArray()
                    val blobId = BlobId.of(bucketName, id)
                    val blobInfo =
                            BlobInfo.newBuilder(blobId).setContentType("application/json").build()
                    storage.create(blobInfo, jsonData)
                    Resource.Success(id)
                } catch (e: Exception) {
                    Resource.Error(e)
                }
            }

    override suspend fun get(id: String): Resource<T> =
            withContext(Dispatchers.IO) {
                try {
                    val blobId = BlobId.of(bucketName, id)
                    val blob = storage.get(blobId)
                    if (blob != null && blob.exists()) {
                        val bytes = blob.getContent()
                        val jsonString = String(bytes)
                        val data = deserializer(jsonString)
                        Resource.Success(data)
                    } else {
                        Resource.Error(Exception("Object not found"))
                    }
                } catch (e: Exception) {
                    Resource.Error(e)
                }
            }

    override suspend fun update(id: String, data: T): Resource<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val jsonData = serializer(data).toByteArray()
                    val blobId = BlobId.of(bucketName, id)
                    val blobInfo =
                            BlobInfo.newBuilder(blobId).setContentType("application/json").build()
                    storage.create(blobInfo, jsonData)
                    Resource.Success(Unit)
                } catch (e: Exception) {
                    Resource.Error(e)
                }
            }

    override suspend fun delete(id: String): Resource<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val blobId = BlobId.of(bucketName, id)
                    val deleted = storage.delete(blobId)
                    if (deleted) {
                        Resource.Success(Unit)
                    } else {
                        Resource.Error(Exception("Object not found or already deleted"))
                    }
                } catch (e: Exception) {
                    Resource.Error(e)
                }
            }
}
