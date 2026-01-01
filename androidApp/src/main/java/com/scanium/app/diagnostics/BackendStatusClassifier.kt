package com.scanium.app.diagnostics

import com.scanium.app.model.config.ConnectionTestErrorType
import com.scanium.app.model.config.ConnectionTestResult

/**
 * Classifies backend connection test results into appropriate reachability statuses.
 *
 * Key distinction: "unreachable" means network issues (DNS, timeout, connection refused),
 * while other failures (401, 404, 5xx) indicate the backend IS reachable but having issues.
 *
 * This classification is critical for providing accurate, actionable diagnostics to users.
 */
object BackendStatusClassifier {

    /**
     * Maps a [ConnectionTestResult] to a [BackendReachabilityStatus].
     *
     * @param result The connection test result from testing the backend
     * @return Appropriate reachability status that distinguishes network vs server issues
     */
    fun classify(result: ConnectionTestResult): BackendReachabilityStatus =
        when (result) {
            is ConnectionTestResult.Success -> BackendReachabilityStatus.REACHABLE
            is ConnectionTestResult.Failure -> classifyFailure(result.errorType)
        }

    /**
     * Maps a [ConnectionTestErrorType] to a [BackendReachabilityStatus].
     */
    fun classifyFailure(errorType: ConnectionTestErrorType): BackendReachabilityStatus =
        when (errorType) {
            // Network-level failures - backend truly unreachable
            ConnectionTestErrorType.NETWORK_UNREACHABLE -> BackendReachabilityStatus.UNREACHABLE
            ConnectionTestErrorType.TIMEOUT -> BackendReachabilityStatus.UNREACHABLE

            // HTTP-level failures - backend IS reachable, but returning errors
            ConnectionTestErrorType.UNAUTHORIZED -> BackendReachabilityStatus.UNAUTHORIZED
            ConnectionTestErrorType.SERVER_ERROR -> BackendReachabilityStatus.SERVER_ERROR
            ConnectionTestErrorType.NOT_FOUND -> BackendReachabilityStatus.NOT_FOUND

            // Configuration issues
            ConnectionTestErrorType.NOT_CONFIGURED -> BackendReachabilityStatus.NOT_CONFIGURED
        }

    /**
     * Determines if the failure indicates the backend is actually reachable
     * (just returning an HTTP error) vs truly unreachable (network error).
     */
    fun isBackendReachable(result: ConnectionTestResult): Boolean =
        when (result) {
            is ConnectionTestResult.Success -> true
            is ConnectionTestResult.Failure ->
                when (result.errorType) {
                    ConnectionTestErrorType.UNAUTHORIZED,
                    ConnectionTestErrorType.SERVER_ERROR,
                    ConnectionTestErrorType.NOT_FOUND -> true

                    ConnectionTestErrorType.NETWORK_UNREACHABLE,
                    ConnectionTestErrorType.TIMEOUT,
                    ConnectionTestErrorType.NOT_CONFIGURED -> false
                }
        }

    /**
     * Gets a user-friendly status message for the given result.
     */
    fun getStatusMessage(result: ConnectionTestResult): String =
        when (result) {
            is ConnectionTestResult.Success -> "Backend Healthy"
            is ConnectionTestResult.Failure ->
                when (result.errorType) {
                    ConnectionTestErrorType.NETWORK_UNREACHABLE -> "Backend Unreachable"
                    ConnectionTestErrorType.TIMEOUT -> "Backend Unreachable (Timeout)"
                    ConnectionTestErrorType.UNAUTHORIZED -> "Backend Reachable — Invalid API Key"
                    ConnectionTestErrorType.SERVER_ERROR -> "Backend Reachable — Server Error"
                    ConnectionTestErrorType.NOT_FOUND -> "Backend Reachable — Endpoint Not Found"
                    ConnectionTestErrorType.NOT_CONFIGURED -> "Backend Not Configured"
                }
        }
}
