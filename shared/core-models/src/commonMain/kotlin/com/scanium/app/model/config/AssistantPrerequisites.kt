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
 */
sealed class ConnectionTestResult {
    object Success : ConnectionTestResult()

    data class Failure(
        val errorType: ConnectionTestErrorType,
        val message: String,
    ) : ConnectionTestResult()
}

enum class ConnectionTestErrorType {
    NETWORK_UNREACHABLE,
    SERVER_ERROR,
    UNAUTHORIZED,
    TIMEOUT,
    NOT_CONFIGURED,
}
