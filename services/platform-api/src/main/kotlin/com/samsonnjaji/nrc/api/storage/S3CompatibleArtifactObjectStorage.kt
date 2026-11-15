package com.samsonnjaji.nrc.api.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import java.net.URI
import java.security.MessageDigest
import java.time.Duration

data class S3ObjectStorageConfig(
    val endpoint: URI,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "us-east-1",
    val pathStyleAccess: Boolean = true,
    val presignTtlSeconds: Long = 900
) {
    init {
        require(endpoint.scheme in setOf("http", "https") && endpoint.host != null) {
            "Object storage endpoint must be an absolute HTTP(S) URL."
        }
        require(bucket.matches(Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$"))) {
            "Object storage bucket must be a valid 3-63 character S3 bucket name."
        }
        require(accessKey.isNotBlank() && secretKey.isNotBlank()) {
            "Object storage access and secret keys are required."
        }
        require(presignTtlSeconds in 60L..604_800L) {
            "Presigned URL lifetime must be between 60 seconds and 7 days."
        }
    }
}

class S3CompatibleArtifactObjectStorage(
    private val config: S3ObjectStorageConfig
) : ArtifactObjectStorage {
    override val bucket: String = config.bucket

    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(config.accessKey, config.secretKey)
    )
    private val s3Configuration = S3Configuration.builder()
        .pathStyleAccessEnabled(config.pathStyleAccess)
        .checksumValidationEnabled(false)
        .build()

    private val client = S3Client.builder()
        .endpointOverride(config.endpoint)
        .region(Region.of(config.region))
        .credentialsProvider(credentials)
        .serviceConfiguration(s3Configuration)
        .build()

    private val presigner = S3Presigner.builder()
        .endpointOverride(config.endpoint)
        .region(Region.of(config.region))
        .credentialsProvider(credentials)
        .serviceConfiguration(s3Configuration)
        .build()

    override fun ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
        } catch (failure: S3Exception) {
            if (failure.statusCode() != 404) throw failure
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        }
    }

    override suspend fun createSingleUpload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long
    ): SignedObjectUpload = withContext(Dispatchers.IO) {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        val signed = presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.presignTtlSeconds))
                .putObjectRequest(request)
                .build()
        )
        SignedObjectUpload(
            url = signed.url().toString(),
            requiredHeaders = mapOf("Content-Type" to contentType),
            expiresAtUnixMs = System.currentTimeMillis() + config.presignTtlSeconds * 1_000
        )
    }

    override suspend fun initiateMultipartUpload(
        objectKey: String,
        contentType: String
    ): String = withContext(Dispatchers.IO) {
        client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build()
        ).uploadId()
    }

    override suspend fun createPartUpload(
        objectKey: String,
        uploadId: String,
        partNumber: Int
    ): SignedObjectUpload = withContext(Dispatchers.IO) {
        val request = UploadPartRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build()
        val signed = presigner.presignUploadPart(
            UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.presignTtlSeconds))
                .uploadPartRequest(request)
                .build()
        )
        SignedObjectUpload(
            url = signed.url().toString(),
            requiredHeaders = emptyMap(),
            expiresAtUnixMs = System.currentTimeMillis() + config.presignTtlSeconds * 1_000
        )
    }

    override suspend fun completeMultipartUpload(
        objectKey: String,
        uploadId: String,
        parts: List<CompletedObjectPart>
    ) = withContext(Dispatchers.IO) {
        val completedParts = parts.sortedBy { it.partNumber }.map {
            CompletedPart.builder()
                .partNumber(it.partNumber)
                .eTag(it.etag)
                .build()
        }
        client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(
                    CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build()
                )
                .build()
        )
        Unit
    }

    override suspend fun abortMultipartUpload(objectKey: String, uploadId: String) =
        withContext(Dispatchers.IO) {
            client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build()
            )
            Unit
        }

    override suspend fun deleteObject(objectKey: String) = withContext(Dispatchers.IO) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build()
        )
        Unit
    }

    override suspend fun verifyObject(objectKey: String): ObjectVerification? =
        withContext(Dispatchers.IO) {
            val head = try {
                client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build()
                )
            } catch (failure: S3Exception) {
                if (failure.statusCode() == 404) return@withContext null
                throw failure
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var actualSize = 0L
            client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()
            ).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    actualSize += count
                }
            }

            ObjectVerification(
                sizeBytes = actualSize,
                etag = head.eTag().orEmpty(),
                sha256Hex = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            )
        }

    override suspend fun createDownloadUrl(objectKey: String): SignedObjectUpload =
        withContext(Dispatchers.IO) {
            val signed = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(config.presignTtlSeconds))
                    .getObjectRequest(
                        GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
                    )
                    .build()
            )
            SignedObjectUpload(
                url = signed.url().toString(),
                requiredHeaders = emptyMap(),
                expiresAtUnixMs = System.currentTimeMillis() + config.presignTtlSeconds * 1_000
            )
        }

    override fun close() {
        presigner.close()
        client.close()
    }
}
