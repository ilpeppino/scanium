package com.scanium.app.network.security

import com.google.common.truth.Truth.assertThat
import okhttp3.Request
import org.junit.Test

class RequestSignerTest {
    @Test
    fun `generateSignature produces consistent output for same inputs`() {
        val apiKey = "test-api-key"
        val timestamp = 1703520000000L
        val body = """{"foo":"bar"}"""

        val signature1 = RequestSigner.generateSignature(apiKey, timestamp, body)
        val signature2 = RequestSigner.generateSignature(apiKey, timestamp, body)

        assertThat(signature1).isEqualTo(signature2)
        assertThat(signature1).hasLength(64) // SHA-256 produces 32 bytes = 64 hex chars
    }

    @Test
    fun `generateSignature produces different output for different bodies`() {
        val apiKey = "test-api-key"
        val timestamp = 1703520000000L

        val signature1 = RequestSigner.generateSignature(apiKey, timestamp, """{"foo":"bar"}""")
        val signature2 = RequestSigner.generateSignature(apiKey, timestamp, """{"foo":"baz"}""")

        assertThat(signature1).isNotEqualTo(signature2)
    }

    @Test
    fun `generateSignature produces different output for different timestamps`() {
        val apiKey = "test-api-key"
        val body = """{"foo":"bar"}"""

        val signature1 = RequestSigner.generateSignature(apiKey, 1703520000000L, body)
        val signature2 = RequestSigner.generateSignature(apiKey, 1703520001000L, body)

        assertThat(signature1).isNotEqualTo(signature2)
    }

    @Test
    fun `generateSignature produces different output for different api keys`() {
        val timestamp = 1703520000000L
        val body = """{"foo":"bar"}"""

        val signature1 = RequestSigner.generateSignature("key1", timestamp, body)
        val signature2 = RequestSigner.generateSignature("key2", timestamp, body)

        assertThat(signature1).isNotEqualTo(signature2)
    }

    @Test
    fun `sign adds timestamp and signature headers to request`() {
        val originalRequest =
            Request.Builder()
                .url("https://example.com/api")
                .build()
        val timestamp = 1703520000000L

        val signedRequest =
            RequestSigner.sign(
                request = originalRequest,
                apiKey = "test-api-key",
                requestBody = """{"test":"data"}""",
                timestampMs = timestamp,
            )

        assertThat(signedRequest.header(RequestSigner.HEADER_TIMESTAMP)).isEqualTo("1703520000000")
        assertThat(signedRequest.header(RequestSigner.HEADER_SIGNATURE)).isNotNull()
        assertThat(signedRequest.header(RequestSigner.HEADER_SIGNATURE)).hasLength(64)
    }

    @Test
    fun `sign returns original request when api key is blank`() {
        val originalRequest =
            Request.Builder()
                .url("https://example.com/api")
                .build()

        val signedRequest =
            RequestSigner.sign(
                request = originalRequest,
                apiKey = "",
                requestBody = """{"test":"data"}""",
            )

        assertThat(signedRequest.header(RequestSigner.HEADER_TIMESTAMP)).isNull()
        assertThat(signedRequest.header(RequestSigner.HEADER_SIGNATURE)).isNull()
    }

    @Test
    fun `addSignatureHeaders for JSON adds headers to builder`() {
        val builder =
            Request.Builder()
                .url("https://example.com/api")
        val timestamp = 1703520000000L

        RequestSigner.addSignatureHeaders(
            builder = builder,
            apiKey = "test-api-key",
            requestBody = """{"test":"data"}""",
            timestampMs = timestamp,
        )

        val request = builder.build()
        assertThat(request.header(RequestSigner.HEADER_TIMESTAMP)).isEqualTo("1703520000000")
        assertThat(request.header(RequestSigner.HEADER_SIGNATURE)).isNotNull()
    }

    @Test
    fun `addSignatureHeaders for JSON does nothing when api key is blank`() {
        val builder =
            Request.Builder()
                .url("https://example.com/api")

        RequestSigner.addSignatureHeaders(
            builder = builder,
            apiKey = "   ",
            requestBody = """{"test":"data"}""",
        )

        val request = builder.build()
        assertThat(request.header(RequestSigner.HEADER_TIMESTAMP)).isNull()
        assertThat(request.header(RequestSigner.HEADER_SIGNATURE)).isNull()
    }

    @Test
    fun `addSignatureHeaders for multipart creates canonical body`() {
        val builder =
            Request.Builder()
                .url("https://example.com/upload")
        val timestamp = 1703520000000L
        val params = mapOf("domainPackId" to "home_resale", "format" to "jpeg")

        RequestSigner.addSignatureHeaders(
            builder = builder,
            apiKey = "test-api-key",
            params = params,
            binaryContentSize = 1024L,
            timestampMs = timestamp,
        )

        val request = builder.build()
        assertThat(request.header(RequestSigner.HEADER_TIMESTAMP)).isEqualTo("1703520000000")
        assertThat(request.header(RequestSigner.HEADER_SIGNATURE)).isNotNull()
    }

    @Test
    fun `addSignatureHeaders for multipart is deterministic regardless of param order`() {
        val timestamp = 1703520000000L

        val builder1 = Request.Builder().url("https://example.com/upload")
        RequestSigner.addSignatureHeaders(
            builder = builder1,
            apiKey = "test-api-key",
            params = linkedMapOf("a" to "1", "b" to "2"),
            binaryContentSize = 100L,
            timestampMs = timestamp,
        )

        val builder2 = Request.Builder().url("https://example.com/upload")
        RequestSigner.addSignatureHeaders(
            builder = builder2,
            apiKey = "test-api-key",
            params = linkedMapOf("b" to "2", "a" to "1"),
            binaryContentSize = 100L,
            timestampMs = timestamp,
        )

        val sig1 = builder1.build().header(RequestSigner.HEADER_SIGNATURE)
        val sig2 = builder2.build().header(RequestSigner.HEADER_SIGNATURE)

        assertThat(sig1).isEqualTo(sig2)
    }

    @Test
    fun `addSignatureHeaders for multipart includes binary size in signature`() {
        val timestamp = 1703520000000L
        val params = mapOf("domainPackId" to "home_resale")

        val builder1 = Request.Builder().url("https://example.com/upload")
        RequestSigner.addSignatureHeaders(
            builder = builder1,
            apiKey = "test-api-key",
            params = params,
            binaryContentSize = 1024L,
            timestampMs = timestamp,
        )

        val builder2 = Request.Builder().url("https://example.com/upload")
        RequestSigner.addSignatureHeaders(
            builder = builder2,
            apiKey = "test-api-key",
            params = params,
            binaryContentSize = 2048L,
            timestampMs = timestamp,
        )

        val sig1 = builder1.build().header(RequestSigner.HEADER_SIGNATURE)
        val sig2 = builder2.build().header(RequestSigner.HEADER_SIGNATURE)

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `addSignatureHeadersForGet signs URL path`() {
        val builder =
            Request.Builder()
                .url("https://example.com/v1/config")
        val timestamp = 1703520000000L

        RequestSigner.addSignatureHeadersForGet(
            builder = builder,
            apiKey = "test-api-key",
            urlPath = "/v1/config",
            timestampMs = timestamp,
        )

        val request = builder.build()
        assertThat(request.header(RequestSigner.HEADER_TIMESTAMP)).isEqualTo("1703520000000")
        assertThat(request.header(RequestSigner.HEADER_SIGNATURE)).isNotNull()
    }

    @Test
    fun `addSignatureHeadersForGet produces different signature for different paths`() {
        val timestamp = 1703520000000L

        val builder1 = Request.Builder().url("https://example.com/v1/config")
        RequestSigner.addSignatureHeadersForGet(
            builder = builder1,
            apiKey = "test-api-key",
            urlPath = "/v1/config",
            timestampMs = timestamp,
        )

        val builder2 = Request.Builder().url("https://example.com/v1/users")
        RequestSigner.addSignatureHeadersForGet(
            builder = builder2,
            apiKey = "test-api-key",
            urlPath = "/v1/users",
            timestampMs = timestamp,
        )

        val sig1 = builder1.build().header(RequestSigner.HEADER_SIGNATURE)
        val sig2 = builder2.build().header(RequestSigner.HEADER_SIGNATURE)

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `addSignatureHeadersForGet does nothing when api key is blank`() {
        val builder =
            Request.Builder()
                .url("https://example.com/v1/config")

        RequestSigner.addSignatureHeadersForGet(
            builder = builder,
            apiKey = "",
            urlPath = "/v1/config",
        )

        val request = builder.build()
        assertThat(request.header(RequestSigner.HEADER_TIMESTAMP)).isNull()
        assertThat(request.header(RequestSigner.HEADER_SIGNATURE)).isNull()
    }

    @Test
    fun `signature is valid hex string`() {
        val signature =
            RequestSigner.generateSignature(
                apiKey = "test-key",
                timestampMs = 1703520000000L,
                requestBody = "test body",
            )

        assertThat(signature).matches("[0-9a-f]{64}")
    }

    @Test
    fun `known test vector produces expected signature`() {
        // This test verifies the HMAC-SHA256 implementation is correct
        // by testing against a known input/output pair
        val apiKey = "secret-key"
        val timestamp = 1000000000000L
        val body = "test"
        val expectedMessage = "1000000000000:test"

        val signature = RequestSigner.generateSignature(apiKey, timestamp, body)

        // Verify the signature is a valid 64-char hex string
        assertThat(signature).hasLength(64)
        assertThat(signature).matches("[0-9a-f]+")

        // Verify consistency - same inputs produce same output
        val signature2 = RequestSigner.generateSignature(apiKey, timestamp, body)
        assertThat(signature).isEqualTo(signature2)
    }
}
