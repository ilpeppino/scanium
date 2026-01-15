package com.scanium.app.selling.assistant

/**
 * Pure state reducer for AssistantViewModel.
 *
 * Part of ARCH-001: Extracted from AssistantViewModel for testability.
 * All functions are pure (no I/O, deterministic, no side effects).
 *
 * This class computes derived state values without modifying any external state.
 */
object AssistantStateReducer {

    /**
     * Computes the assistant mode based on online status and failure state.
     *
     * @param isOnline Whether the device is online.
     * @param failure The last backend failure, if any.
     * @return The computed AssistantMode.
     */
    fun resolveMode(
        isOnline: Boolean,
        failure: AssistantBackendFailure?,
    ): AssistantMode {
        return if (!isOnline) {
            AssistantMode.OFFLINE
        } else if (failure != null) {
            AssistantMode.LIMITED
        } else {
            AssistantMode.ONLINE
        }
    }

    /**
     * Computes the explicit availability state from current conditions.
     *
     * @param isOnline Whether the device is online.
     * @param isLoading Whether a request is currently loading.
     * @param failure The last backend failure, if any.
     * @return The computed AssistantAvailability.
     */
    fun computeAvailability(
        isOnline: Boolean,
        isLoading: Boolean,
        failure: AssistantBackendFailure?,
    ): AssistantAvailability {
        // Loading state - user must wait
        if (isLoading) {
            return AssistantAvailability.Unavailable(
                reason = UnavailableReason.LOADING,
                canRetry = false,
            )
        }

        // Offline - cannot send
        if (!isOnline) {
            return AssistantAvailability.Unavailable(
                reason = UnavailableReason.OFFLINE,
                canRetry = true,
            )
        }

        // Map backend failures to availability
        if (failure != null) {
            val (reason, canRetry, retryAfter) = when (failure.type) {
                AssistantBackendErrorType.AUTH_REQUIRED,
                AssistantBackendErrorType.AUTH_INVALID,
                AssistantBackendErrorType.UNAUTHORIZED ->
                    Triple(UnavailableReason.UNAUTHORIZED, false, null)
                AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED ->
                    Triple(UnavailableReason.NOT_CONFIGURED, false, null)
                AssistantBackendErrorType.RATE_LIMITED ->
                    Triple(UnavailableReason.RATE_LIMITED, true, failure.retryAfterSeconds)
                AssistantBackendErrorType.VALIDATION_ERROR ->
                    Triple(UnavailableReason.VALIDATION_ERROR, false, null)
                AssistantBackendErrorType.NETWORK_TIMEOUT,
                AssistantBackendErrorType.NETWORK_UNREACHABLE,
                AssistantBackendErrorType.VISION_UNAVAILABLE,
                AssistantBackendErrorType.PROVIDER_UNAVAILABLE ->
                    Triple(UnavailableReason.BACKEND_ERROR, true, null)
            }
            return AssistantAvailability.Unavailable(
                reason = reason,
                canRetry = canRetry,
                retryAfterSeconds = retryAfter,
            )
        }

        // All good
        return AssistantAvailability.Available
    }

    /**
     * Builds a user-friendly snackbar message for fallback scenarios.
     *
     * @param failure The backend failure to describe.
     * @return A user-friendly snackbar message.
     */
    fun buildFallbackSnackbarMessage(failure: AssistantBackendFailure): String {
        val statusLabel = AssistantErrorDisplay.getStatusLabel(failure)
        val base = "Switched to Local Helper"

        return when (failure.type) {
            AssistantBackendErrorType.AUTH_REQUIRED ->
                "$base: Sign in required. Tap Settings to sign in."
            AssistantBackendErrorType.AUTH_INVALID ->
                "$base: Session expired. Sign in again in Settings."
            AssistantBackendErrorType.UNAUTHORIZED ->
                "$base: $statusLabel. Check your account."
            AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED ->
                "$base: $statusLabel. Contact support."
            AssistantBackendErrorType.RATE_LIMITED -> {
                val retryHint = failure.retryAfterSeconds?.let { " (wait ${it}s)" } ?: ""
                "$base: $statusLabel$retryHint."
            }
            AssistantBackendErrorType.NETWORK_TIMEOUT ->
                "$base: $statusLabel. Check your connection."
            AssistantBackendErrorType.NETWORK_UNREACHABLE ->
                "$base: $statusLabel. Connect to internet."
            AssistantBackendErrorType.VISION_UNAVAILABLE ->
                "$base: Image analysis unavailable."
            AssistantBackendErrorType.VALIDATION_ERROR ->
                "$base: Invalid request. Try rephrasing."
            AssistantBackendErrorType.PROVIDER_UNAVAILABLE ->
                "$base: Service temporarily unavailable."
        }
    }

    /**
     * Returns a debug-friendly string representation of availability.
     *
     * @param availability The availability state to describe.
     * @return A debug string representation.
     */
    fun availabilityDebugString(availability: AssistantAvailability): String {
        return when (availability) {
            is AssistantAvailability.Available -> "Available"
            is AssistantAvailability.Checking -> "Checking"
            is AssistantAvailability.Unavailable ->
                "Unavailable(${availability.reason}, canRetry=${availability.canRetry}, retryAfter=${availability.retryAfterSeconds})"
        }
    }

    /**
     * Returns the alternative key for an attribute when there's a conflict.
     * Maps: color -> secondaryColor, brand -> brand2, model -> model2
     *
     * @param key The original attribute key.
     * @return The alternative key for conflict resolution.
     */
    fun getAlternativeKey(key: String): String {
        return when (key.lowercase()) {
            "color" -> "secondaryColor"
            "brand" -> "brand2"
            "model" -> "model2"
            else -> "${key}2"
        }
    }
}
