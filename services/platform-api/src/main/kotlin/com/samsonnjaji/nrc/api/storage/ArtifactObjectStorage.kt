package com.samsonnjaji.nrc.api.storage

data class SignedObjectUpload(
    val url: String,
    val requiredHeaders: Map<String, String>,
    val expiresAtUnixMs: Long
)

data class CompletedObjectPart(
    val partNumber: Int,
    val etag: String
)

data class ObjectVerification(
    val sizeBytes: Long,
    val etag: String,
    val sha256Hex: String
)

interface ArtifactObjectStorage : AutoCloseable {
    val bucket: String

    fun ensureBucket()

    suspend fun createSingleUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long
    ): SignedObjectUpload

    suspend fun initiateMultipartUpload(
        objectKey: String,
        contentType: String
    ): String

    suspend fun createPartUpload(
        objectKey: String,
        uploadId: String,
        partNumber: Int
    ): SignedObjectUpload

    suspend fun completeMultipartUpload(
        objectKey: String,
        uploadId: String,
        parts: List<CompletedObjectPart>
    )

    suspend fun abortMultipartUpload(objectKey: String, uploadId: String)

    suspend fun deleteObject(objectKey: String)

    suspend fun verifyObject(objectKey: String): ObjectVerification?

    suspend fun createDownloadUrl(objectKey: String): SignedObjectUpload
}
