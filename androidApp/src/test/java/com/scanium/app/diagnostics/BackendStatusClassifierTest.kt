package com.scanium.app.diagnostics

import com.scanium.app.model.config.ConnectionTestErrorType
import com.scanium.app.model.config.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BackendStatusClassifier].
 *
 * These tests verify that backend connection test results are correctly classified
 * into reachability statuses, distinguishing between:
 * - Network unreachable (DNS/timeout/SSL/connection refused)
 * - HTTP 401/403 (reachable but unauthorized)
 * - HTTP 404 (reachable but endpoint not found)
 * - HTTP 5xx (reachable but server error)
 */
class BackendStatusClassifierTest {
    // ==================== Success Cases ====================

    @Test
    fun `success result classifies as REACHABLE`() {
        val result = ConnectionTestResult.Success(httpStatus = 200, endpoint = "/health")
        assertEquals(BackendReachabilityStatus.REACHABLE, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `success result with 201 classifies as REACHABLE`() {
        val result = ConnectionTestResult.Success(httpStatus = 201, endpoint = "/health")
        assertEquals(BackendReachabilityStatus.REACHABLE, BackendStatusClassifier.classify(result))
    }

    // ==================== Network Unreachable Cases ====================

    @Test
    fun `NETWORK_UNREACHABLE error classifies as UNREACHABLE`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                message = "Cannot resolve server address",
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.UNREACHABLE, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `TIMEOUT error classifies as UNREACHABLE`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.TIMEOUT,
                message = "Connection timed out",
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.UNREACHABLE, BackendStatusClassifier.classify(result))
    }

    // ==================== HTTP Error Cases (Backend IS Reachable) ====================

    @Test
    fun `UNAUTHORIZED error classifies as UNAUTHORIZED`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Invalid API key or unauthorized access",
                httpStatus = 401,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.UNAUTHORIZED, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `UNAUTHORIZED error with 403 classifies as UNAUTHORIZED`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Forbidden",
                httpStatus = 403,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.UNAUTHORIZED, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `SERVER_ERROR classifies as SERVER_ERROR`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Server error (500)",
                httpStatus = 500,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.SERVER_ERROR, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `SERVER_ERROR with 502 classifies as SERVER_ERROR`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Bad Gateway",
                httpStatus = 502,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.SERVER_ERROR, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `SERVER_ERROR with Cloudflare 530 classifies as SERVER_ERROR`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Cloudflare origin error",
                httpStatus = 530,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.SERVER_ERROR, BackendStatusClassifier.classify(result))
    }

    @Test
    fun `NOT_FOUND error classifies as NOT_FOUND`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_FOUND,
                message = "Endpoint not found (wrong base URL or route)",
                httpStatus = 404,
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.NOT_FOUND, BackendStatusClassifier.classify(result))
    }

    // ==================== Configuration Cases ====================

    @Test
    fun `NOT_CONFIGURED error classifies as NOT_CONFIGURED`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_CONFIGURED,
                message = "Backend URL is not configured",
                endpoint = "/health",
            )
        assertEquals(BackendReachabilityStatus.NOT_CONFIGURED, BackendStatusClassifier.classify(result))
    }

    // ==================== isBackendReachable Tests ====================

    @Test
    fun `isBackendReachable returns true for success`() {
        val result = ConnectionTestResult.Success(httpStatus = 200, endpoint = "/health")
        assertTrue(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns true for UNAUTHORIZED`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Invalid API key",
                httpStatus = 401,
                endpoint = "/health",
            )
        assertTrue(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns true for SERVER_ERROR`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Server error",
                httpStatus = 500,
                endpoint = "/health",
            )
        assertTrue(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns true for NOT_FOUND`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_FOUND,
                message = "Not found",
                httpStatus = 404,
                endpoint = "/health",
            )
        assertTrue(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns false for NETWORK_UNREACHABLE`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                message = "Cannot connect",
                endpoint = "/health",
            )
        assertFalse(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns false for TIMEOUT`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.TIMEOUT,
                message = "Timed out",
                endpoint = "/health",
            )
        assertFalse(BackendStatusClassifier.isBackendReachable(result))
    }

    @Test
    fun `isBackendReachable returns false for NOT_CONFIGURED`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_CONFIGURED,
                message = "Not configured",
                endpoint = "/health",
            )
        assertFalse(BackendStatusClassifier.isBackendReachable(result))
    }

    // ==================== getStatusMessage Tests ====================

    @Test
    fun `getStatusMessage for success returns healthy`() {
        val result = ConnectionTestResult.Success(httpStatus = 200, endpoint = "/health")
        assertEquals("Backend Healthy", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for NETWORK_UNREACHABLE returns unreachable`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                message = "Cannot connect",
                endpoint = "/health",
            )
        assertEquals("Backend Unreachable", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for TIMEOUT returns unreachable with timeout`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.TIMEOUT,
                message = "Timed out",
                endpoint = "/health",
            )
        assertEquals("Backend Unreachable (Timeout)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for UNAUTHORIZED with status code returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Invalid key",
                httpStatus = 401,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Unauthorized (401)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for UNAUTHORIZED 403 returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Forbidden",
                httpStatus = 403,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Unauthorized (403)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for UNAUTHORIZED without status code returns fallback`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Invalid key",
                httpStatus = null,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Invalid API Key", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for SERVER_ERROR 500 returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Server error",
                httpStatus = 500,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Server Error (500)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for SERVER_ERROR 502 returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Bad Gateway",
                httpStatus = 502,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Server Error (502)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for SERVER_ERROR 530 Cloudflare returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Cloudflare origin error",
                httpStatus = 530,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Server Error (530)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for SERVER_ERROR without status code returns fallback`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Server error",
                httpStatus = null,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Server Error", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for NOT_FOUND with status code returns reachable with code`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_FOUND,
                message = "Not found",
                httpStatus = 404,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Endpoint Not Found (404)", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for NOT_FOUND without status code returns fallback`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_FOUND,
                message = "Not found",
                httpStatus = null,
                endpoint = "/health",
            )
        assertEquals("Backend Reachable — Endpoint Not Found", BackendStatusClassifier.getStatusMessage(result))
    }

    @Test
    fun `getStatusMessage for NOT_CONFIGURED returns not configured`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NOT_CONFIGURED,
                message = "Not configured",
                endpoint = "/health",
            )
        assertEquals("Backend Not Configured", BackendStatusClassifier.getStatusMessage(result))
    }

    // ==================== Debug Detail Tests ====================

    @Test
    fun `failure debugDetail includes method endpoint and status`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                message = "Invalid key",
                httpStatus = 401,
                endpoint = "/health",
                method = "GET",
            )
        assertEquals("GET /health -> 401", result.debugDetail)
    }

    @Test
    fun `failure debugDetail without httpStatus omits arrow`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                message = "Cannot connect",
                endpoint = "/health",
                method = "GET",
            )
        assertEquals("GET /health", result.debugDetail)
    }

    @Test
    fun `failure debugDetail with POST method`() {
        val result =
            ConnectionTestResult.Failure(
                errorType = ConnectionTestErrorType.SERVER_ERROR,
                message = "Server error",
                httpStatus = 500,
                endpoint = "/v1/assist/chat",
                method = "POST",
            )
        assertEquals("POST /v1/assist/chat -> 500", result.debugDetail)
    }
}
