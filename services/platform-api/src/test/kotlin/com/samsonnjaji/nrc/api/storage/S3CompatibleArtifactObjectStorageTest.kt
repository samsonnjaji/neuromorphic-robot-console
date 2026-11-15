package com.samsonnjaji.nrc.api.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class S3CompatibleArtifactObjectStorageTest {

    @Test
    fun singlePutPresignedUploadCanBeVerified() {
        val storage = configuredOrSkip()
        try {
            storage.ensureBucket()
            val bytes = "NRC object storage verification".toByteArray()
            val key = "ci-single/${UUID.randomUUID()}.bin"
            val signed = runBlocking {
                storage.createSingleUpload(key, "application/octet-stream", bytes.size.toLong())
            }
            val request = HttpRequest.newBuilder(URI.create(signed.url))
                .apply { signed.requiredHeaders.forEach { (name, value) -> header(name, value) } }
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

            assertEquals(200, response.statusCode())
            val verification = runBlocking { storage.verifyObject(key) }
            assertNotNull(verification)
            assertEquals(bytes.size.toLong(), verification.sizeBytes)
            assertEquals(sha256(bytes), verification.sha256Hex)
        } finally {
            storage.close()
        }
    }

    @Test
    fun multipartPresignedPartsCanBeCompletedAndVerified() {
        val storage = configuredOrSkip()
        try {
            storage.ensureBucket()
            val firstPart = ByteArray(5 * 1024 * 1024) { (it % 251).toByte() }
            val finalPart = ByteArray(1024 * 1024 + 37) { ((it + 11) % 251).toByte() }
            val expectedBytes = firstPart + finalPart
            val key = "ci-multipart/${UUID.randomUUID()}.mcap"
            val uploadId = runBlocking {
                storage.initiateMultipartUpload(key, "application/octet-stream")
            }
            val httpClient = HttpClient.newHttpClient()

            val firstUrl = runBlocking { storage.createPartUpload(key, uploadId, 1) }
            val firstResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(firstUrl.url))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(firstPart))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, firstResponse.statusCode())
            val firstEtag = firstResponse.headers().firstValue("ETag").orElseThrow()

            val finalUrl = runBlocking { storage.createPartUpload(key, uploadId, 2) }
            val finalResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(finalUrl.url))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(finalPart))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, finalResponse.statusCode())
            val finalEtag = finalResponse.headers().firstValue("ETag").orElseThrow()

            runBlocking {
                storage.completeMultipartUpload(
                    key,
                    uploadId,
                    listOf(
                        CompletedObjectPart(1, firstEtag),
                        CompletedObjectPart(2, finalEtag)
                    )
                )
            }

            val verification = runBlocking { storage.verifyObject(key) }
            assertNotNull(verification)
            assertEquals(expectedBytes.size.toLong(), verification.sizeBytes)
            assertEquals(sha256(expectedBytes), verification.sha256Hex)
        } finally {
            storage.close()
        }
    }

    private fun configuredOrSkip(): S3CompatibleArtifactObjectStorage {
        val endpoint = System.getenv("NRC_TEST_OBJECT_STORE_ENDPOINT")
        val accessKey = System.getenv("NRC_TEST_OBJECT_STORE_ACCESS_KEY")
        val secretKey = System.getenv("NRC_TEST_OBJECT_STORE_SECRET_KEY")
        assumeTrue(
            "RustFS integration environment is required.",
            !endpoint.isNullOrBlank() && !accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()
        )

        return S3CompatibleArtifactObjectStorage(
            S3ObjectStorageConfig(
                endpoint = URI.create(requireNotNull(endpoint)),
                bucket = System.getenv("NRC_TEST_OBJECT_STORE_BUCKET") ?: "nrc-ci-artifacts",
                accessKey = requireNotNull(accessKey),
                secretKey = requireNotNull(secretKey),
                region = "us-east-1",
                pathStyleAccess = true
            )
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
}
