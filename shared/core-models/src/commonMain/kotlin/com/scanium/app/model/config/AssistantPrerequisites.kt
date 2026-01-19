package com.scanium.app.model.config

/**
 * Represents a single prerequisite required for the assistant to function.
 */
data class AssistantPrerequisite(
    val id: String,
    val displayName: String,
    val description: String,
    val satisfied: Boolean,
    val category: PrerequisiteCategory,
)

enum class PrerequisiteCategory {
    /** Subscription/billing related */
    SUBSCRIPTION,

    /** Server-side configuration */
    REMOTE_CONFIG,

    /** Local app configuration */
    LOCAL_CONFIG,

    /** Network/connectivity related */
    CONNECTIVITY,
}

/**
 * Aggregated state of all assistant prerequisites.
 * Used to determine if the assistant toggle can be enabled and to
 * explain to users what's blocking the feature.
 */
data class AssistantPrerequisiteState(
    val prerequisites: List<AssistantPrerequisite>,
    val allSatisfied: Boolean = prerequisites.all { it.satisfied },
    val unsatisfiedCount: Int = prerequisites.count { !it.satisfied },
) {
    companion object {
        val LOADING =
            AssistantPrerequisiteState(
                prerequisites = emptyList(),
                allSatisfied = false,
                unsatisfiedCount = 0,
            )
    }

    fun unsatisfiedPrerequisites(): List<AssistantPrerequisite> = prerequisites.filter { !it.satisfied }

    fun satisfiedPrerequisites(): List<AssistantPrerequisite> = prerequisites.filter { it.satisfied }
}

/**
 * Result of a backend connection test.
 * Includes detailed debug information for diagnostics display.
 */
sealed class ConnectionTestResult {
    /** Successful connection - backend responded with 2xx */
    data class Success(
        /** HTTP status code (e.g., 200) */
        val httpStatus: Int = 200,
        /** Tested endpoint path (e.g., "/health") */
        val endpoint: String = "/health",
    ) : ConnectionTestResult()

    data class Failure(
        val errorType: ConnectionTestErrorType,
        val message: String,
        /** HTTP status code if available (null for network errors) */
        val httpStatus: Int? = null,
        /** Tested endpoint path (e.g., "/health", "/v1/assist/chat") */
        val endpoint: String? = null,
        /** HTTP method used (e.g., "GET", "POST") */
        val method: String = "GET",
    ) : ConnectionTestResult() {
        /**
         * Debug string for display in UI, e.g., "GET /health -> 401"
         */
        val debugDetail: String
            get() =
                buildString {
                    append(method)
                    append(" ")
                    append(endpoint ?: "/unknown")
                    httpStatus?.let { append(" -> $it") }
                }
    }
}

enum class ConnectionTestErrorType {
    /** Network error - DNS failure, connection refused, SSL error */
    NETWORK_UNREACHABLE,

    /** HTTP 5xx response */
    SERVER_ERROR,

    /** HTTP 401/403 response */
    UNAUTHORIZED,

    /** HTTP 404 response */
    NOT_FOUND,

    /** Socket/connection timeout */
    TIMEOUT,

    /** Backend URL or API key not configured */
    NOT_CONFIGURED,
}
