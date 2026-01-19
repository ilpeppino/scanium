package com.scanium.app.selling.assistant

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Phase B: Contract tests for error response parsing and mapping.
 *
 * Ensures that Phase B error codes (AUTH_REQUIRED, AUTH_INVALID, RATE_LIMITED)
 * are correctly parsed from backend responses and mapped to appropriate UI states.
 */
class AssistantErrorMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Test
    fun `401 with AUTH_REQUIRED code maps to AUTH_REQUIRED error type`() {
        val errorJson =
            """
            {
                "error": {
                    "code": "AUTH_REQUIRED",
                    "message": "Sign in is required to use this feature.",
                    "correlationId": "test-123"
                }
            }
            """.trimIndent()

        // This test simulates the error parsing logic in AssistantRepository
        val errorData =
            runCatching {
                json.decodeFromString<TestErrorResponse>(errorJson)
            }.getOrNull()

        assertNotNull("Error response should parse successfully", errorData)
        assertEquals("AUTH_REQUIRED", errorData?.error?.code)
        assertEquals("Sign in is required to use this feature.", errorData?.error?.message)

        // Verify error type mapping
        val errorType = parseErrorType(errorData?.error?.code ?: "")
        assertEquals(AssistantBackendErrorType.AUTH_REQUIRED, errorType)
    }

    @Test
    fun `401 with AUTH_INVALID code maps to AUTH_INVALID error type`() {
        val errorJson =
            """
            {
                "error": {
                    "code": "AUTH_INVALID",
                    "message": "Your session is invalid or expired. Please sign in again.",
                    "correlationId": "test-456"
                }
            }
            """.trimIndent()

        val errorData =
            runCatching {
                json.decodeFromString<TestErrorResponse>(errorJson)
            }.getOrNull()

        assertNotNull("Error response should parse successfully", errorData)
        assertEquals("AUTH_INVALID", errorData?.error?.code)

        val errorType = parseErrorType(errorData?.error?.code ?: "")
        assertEquals(AssistantBackendErrorType.AUTH_INVALID, errorType)
    }

    @Test
    fun `429 with RATE_LIMITED code includes resetAt timestamp`() {
        val errorJson =
            """
            {
                "error": {
                    "code": "RATE_LIMITED",
                    "message": "Rate limit reached. Please try again later.",
                    "resetAt": "2026-01-05T17:00:00.000Z",
                    "correlationId": "test-789"
                }
            }
            """.trimIndent()

        val errorData =
            runCatching {
                json.decodeFromString<TestErrorResponse>(errorJson)
            }.getOrNull()

        assertNotNull("Error response should parse successfully", errorData)
        assertEquals("RATE_LIMITED", errorData?.error?.code)
        assertNotNull("resetAt should be present", errorData?.error?.resetAt)
        assertEquals("2026-01-05T17:00:00.000Z", errorData?.error?.resetAt)

        val errorType = parseErrorType(errorData?.error?.code ?: "")
        assertEquals(AssistantBackendErrorType.RATE_LIMITED, errorType)
    }

    @Test
    fun `AUTH_REQUIRED error displays correct UI message`() {
        val failure =
            AssistantBackendFailure(
                type = AssistantBackendErrorType.AUTH_REQUIRED,
                category = AssistantBackendErrorCategory.POLICY,
                retryable = false,
                message = "Sign in required",
            )

        val errorInfo = AssistantErrorDisplay.getErrorInfo(failure)

        assertNotNull("Error info should not be null", errorInfo)
        assertEquals("Sign In Required", errorInfo?.title)
        assertEquals(false, errorInfo?.showRetry)
        assert(errorInfo?.actionHint?.contains("sign in") == true)
    }

    @Test
    fun `AUTH_INVALID error displays correct UI message`() {
        val failure =
            AssistantBackendFailure(
                type = AssistantBackendErrorType.AUTH_INVALID,
                category = AssistantBackendErrorCategory.POLICY,
                retryable = false,
                message = "Session expired",
            )

        val errorInfo = AssistantErrorDisplay.getErrorInfo(failure)

        assertNotNull("Error info should not be null", errorInfo)
        assertEquals("Session Expired", errorInfo?.title)
        assertEquals(false, errorInfo?.showRetry)
        assert(errorInfo?.explanation?.contains("expired") == true)
    }

    @Test
    fun `RATE_LIMITED error displays retry hint with timestamp`() {
        val failure =
            AssistantBackendFailure(
                type = AssistantBackendErrorType.RATE_LIMITED,
                category = AssistantBackendErrorCategory.POLICY,
                retryable = true,
                retryAfterSeconds = 60,
                message = "Rate limit reached",
            )

        val errorInfo = AssistantErrorDisplay.getErrorInfo(failure)

        assertNotNull("Error info should not be null", errorInfo)
        assertEquals("Rate Limit Reached", errorInfo?.title)
        assertEquals(true, errorInfo?.showRetry)
        assert(errorInfo?.explanation?.contains("60 seconds") == true)
    }

    @Test
    fun `error status labels are correct for Phase B error types`() {
        assertEquals(
            "Sign In Required",
            AssistantErrorDisplay.getStatusLabel(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.AUTH_REQUIRED,
                    category = AssistantBackendErrorCategory.POLICY,
                    retryable = false,
                ),
            ),
        )

        assertEquals(
            "Session Expired",
            AssistantErrorDisplay.getStatusLabel(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.AUTH_INVALID,
                    category = AssistantBackendErrorCategory.POLICY,
                    retryable = false,
                ),
            ),
        )

        assertEquals(
            "Rate Limited",
            AssistantErrorDisplay.getStatusLabel(
                AssistantBackendFailure(
                    type = AssistantBackendErrorType.RATE_LIMITED,
                    category = AssistantBackendErrorCategory.POLICY,
                    retryable = true,
                ),
            ),
        )
    }

    // Helper function to simulate error type parsing (mirrors repository logic)
    private fun parseErrorType(code: String): AssistantBackendErrorType {
        return when (code.uppercase()) {
            "AUTH_REQUIRED" -> AssistantBackendErrorType.AUTH_REQUIRED
            "AUTH_INVALID" -> AssistantBackendErrorType.AUTH_INVALID
            "RATE_LIMITED" -> AssistantBackendErrorType.RATE_LIMITED
            "UNAUTHORIZED" -> AssistantBackendErrorType.UNAUTHORIZED
            else -> AssistantBackendErrorType.PROVIDER_UNAVAILABLE
        }
    }

    // Test DTOs for parsing (match backend contract)
    @kotlinx.serialization.Serializable
    private data class TestErrorResponse(
        val error: TestErrorDetails,
    )

    @kotlinx.serialization.Serializable
    private data class TestErrorDetails(
        val code: String,
        val message: String,
        val resetAt: String? = null,
        val correlationId: String? = null,
    )
}
