package com.scanium.app.network.security

import okhttp3.Request
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 request signer for cloud API requests.
 *
 * Prevents replay attacks by signing requests with:
 * - X-Request-Timestamp: Unix timestamp (milliseconds)
 * - X-Request-Signature: HMAC-SHA256(apiKey, "$timestamp:$bodyDigest")
 *
 * The server should:
 * 1. Verify the signature matches
 * 2. Reject requests with timestamps older than a threshold (e.g., 5 minutes)
 * 3. Track seen signatures to prevent replay within the time window
 *
 * ## Usage
 * ```kotlin
 * // For JSON requests:
 * val signedRequest = RequestSigner.sign(
 *     request = originalRequest,
 *     apiKey = BuildConfig.SCANIUM_API_KEY,
 *     requestBody = jsonBody
 * )
 *
 * // For multipart requests:
 * RequestSigner.addSignatureHeaders(
 *     builder = requestBuilder,
 *     apiKey = BuildConfig.SCANIUM_API_KEY,
 *     params = mapOf("domainPackId" to domainPackId),
 *     binaryContentSize = imageBytes.size.toLong()
 * )
 * ```
 */
object RequestSigner {
    private const val HMAC_SHA256 = "HmacSHA256"
    internal const val HEADER_TIMESTAMP = "X-Request-Timestamp"
    internal const val HEADER_SIGNATURE = "X-Request-Signature"

    /**
     * Sign a request with HMAC-SHA256.
     *
     * @param request The original OkHttp request
     * @param apiKey The API key to use as the HMAC secret
     * @param requestBody The request body content (for signature calculation)
     * @param timestampMs Optional timestamp override (for testing)
     * @return A new request with signature headers added
     */
    fun sign(
        request: Request,
        apiKey: String,
        requestBody: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): Request {
        if (apiKey.isBlank()) {
            return request
        }

        val signature = generateSignature(apiKey, timestampMs, requestBody)

        return request.newBuilder()
            .header(HEADER_TIMESTAMP, timestampMs.toString())
            .header(HEADER_SIGNATURE, signature)
            .build()
    }

    /**
     * Add signature headers to a Request.Builder for JSON requests.
     *
     * Use this when building a request before it's finalized.
     *
     * @param builder The request builder to add headers to
     * @param apiKey The API key to use as the HMAC secret
     * @param requestBody The request body content (for signature calculation)
     * @param timestampMs Optional timestamp override (for testing)
     */
    fun addSignatureHeaders(
        builder: Request.Builder,
        apiKey: String,
        requestBody: String,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (apiKey.isBlank()) {
            return
        }

        val signature = generateSignature(apiKey, timestampMs, requestBody)
        builder.header(HEADER_TIMESTAMP, timestampMs.toString())
        builder.header(HEADER_SIGNATURE, signature)
    }

    /**
     * Add signature headers to a Request.Builder for multipart requests.
     *
     * For multipart requests, we sign a canonical representation of the parameters
     * along with the binary content size for integrity.
     *
     * @param builder The request builder to add headers to
     * @param apiKey The API key to use as the HMAC secret
     * @param params Key-value pairs from the multipart form (text parts only)
     * @param binaryContentSize Size of binary content in bytes (for integrity check)
     * @param timestampMs Optional timestamp override (for testing)
     */
    fun addSignatureHeaders(
        builder: Request.Builder,
        apiKey: String,
        params: Map<String, String>,
        binaryContentSize: Long,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (apiKey.isBlank()) {
            return
        }

        val canonicalBody = buildCanonicalMultipartBody(params, binaryContentSize)
        val signature = generateSignature(apiKey, timestampMs, canonicalBody)
        builder.header(HEADER_TIMESTAMP, timestampMs.toString())
        builder.header(HEADER_SIGNATURE, signature)
    }

    /**
     * Build a canonical string representation for multipart requests.
     *
     * Format: "key1=value1&key2=value2&_size=<binarySize>"
     * Keys are sorted alphabetically for deterministic ordering.
     */
    private fun buildCanonicalMultipartBody(
        params: Map<String, String>,
        binarySize: Long,
    ): String {
        val sortedParams = params.entries.sortedBy { it.key }
        val paramString = sortedParams.joinToString("&") { "${it.key}=${it.value}" }
        return if (paramString.isEmpty()) {
            "_size=$binarySize"
        } else {
            "$paramString&_size=$binarySize"
        }
    }

    /**
     * Add signature headers to a Request.Builder for GET requests.
     *
     * For GET requests, we sign the URL path and query parameters.
     *
     * @param builder The request builder to add headers to
     * @param apiKey The API key to use as the HMAC secret
     * @param urlPath The URL path with query string (e.g., "/v1/config?foo=bar")
     * @param timestampMs Optional timestamp override (for testing)
     */
    fun addSignatureHeadersForGet(
        builder: Request.Builder,
        apiKey: String,
        urlPath: String,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        if (apiKey.isBlank()) {
            return
        }

        val signature = generateSignature(apiKey, timestampMs, urlPath)
        builder.header(HEADER_TIMESTAMP, timestampMs.toString())
        builder.header(HEADER_SIGNATURE, signature)
    }

    /**
     * Generate HMAC-SHA256 signature.
     *
     * The signature is computed as: HMAC-SHA256(apiKey, "$timestamp:$requestBody")
     *
     * @param apiKey The secret key
     * @param timestampMs Unix timestamp in milliseconds
     * @param requestBody The request body content
     * @return Hex-encoded signature
     */
    internal fun generateSignature(
        apiKey: String,
        timestampMs: Long,
        requestBody: String,
    ): String {
        val message = "$timestampMs:$requestBody"
        val mac = Mac.getInstance(HMAC_SHA256)
        val secretKey = SecretKeySpec(apiKey.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hmacBytes.toHexString()
    }

    /**
     * Convert byte array to lowercase hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
