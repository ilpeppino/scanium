package com.scanium.app.selling.assistant

/**
 * Maps backend failure types to user-facing display information.
 * This provides explicit, debuggable error states for the Online AI Assistant.
 */
object AssistantErrorDisplay {
    /**
     * Error display information for UI rendering.
     */
    data class ErrorInfo(
        val title: String,
        val explanation: String,
        val actionHint: String,
        val showRetry: Boolean,
    )

    /**
     * Get user-friendly display information for a backend failure.
     */
    fun getErrorInfo(failure: AssistantBackendFailure?): ErrorInfo? {
        if (failure == null) return null

        return when (failure.type) {
            AssistantBackendErrorType.AUTH_REQUIRED ->
                ErrorInfo(
                    title = "Sign In Required",
                    explanation =
                        "You need to sign in to use the online assistant. " +
                            "The assistant requires authentication for personalized responses.",
                    actionHint = "Tap to sign in with Google.",
                    showRetry = false,
                )

            AssistantBackendErrorType.AUTH_INVALID ->
                ErrorInfo(
                    title = "Session Expired",
                    explanation =
                        "Your session has expired or is no longer valid. " +
                            "Please sign in again to continue using the assistant.",
                    actionHint = "Sign in again to continue.",
                    showRetry = false,
                )

            AssistantBackendErrorType.UNAUTHORIZED ->
                ErrorInfo(
                    title = "Authorization Required",
                    explanation =
                        "Your session is not authorized to use the online assistant. " +
                            "This may happen if your subscription has expired or credentials are invalid.",
                    actionHint = "Check your account status or sign in again.",
                    showRetry = false,
                )

            AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED ->
                ErrorInfo(
                    title = "Assistant Not Configured",
                    explanation =
                        "The online assistant backend is not set up for this app build. " +
                            "This is a configuration issue.",
                    actionHint = "Contact support or use a production build.",
                    showRetry = false,
                )

            AssistantBackendErrorType.RATE_LIMITED ->
                ErrorInfo(
                    title = "Rate Limit Reached",
                    explanation = buildRateLimitExplanation(failure),
                    actionHint = "Wait a moment before sending another message.",
                    showRetry = true,
                )

            AssistantBackendErrorType.NETWORK_TIMEOUT ->
                ErrorInfo(
                    title = "Request Timed Out",
                    explanation =
                        "The assistant server took too long to respond. " +
                            "This may be due to high load or a slow connection.",
                    actionHint = "Try again or check your connection.",
                    showRetry = true,
                )

            AssistantBackendErrorType.NETWORK_UNREACHABLE ->
                ErrorInfo(
                    title = "Cannot Reach Server",
                    explanation =
                        "Unable to connect to the assistant server. " +
                            "Check that you have an internet connection.",
                    actionHint = "Connect to the internet and retry.",
                    showRetry = true,
                )

            AssistantBackendErrorType.VISION_UNAVAILABLE ->
                ErrorInfo(
                    title = "Image Analysis Unavailable",
                    explanation =
                        "The image analysis service is temporarily unavailable. " +
                            "Text-based assistance is still working.",
                    actionHint = "Retry or continue with text questions.",
                    showRetry = true,
                )

            AssistantBackendErrorType.VALIDATION_ERROR ->
                ErrorInfo(
                    title = "Invalid Request",
                    explanation =
                        "The request could not be processed. " +
                            "This may be a bug in the app.",
                    actionHint = "Try rephrasing your question or restart the app.",
                    showRetry = false,
                )

            AssistantBackendErrorType.PROVIDER_UNAVAILABLE ->
                ErrorInfo(
                    title = "Assistant Temporarily Unavailable",
                    explanation =
                        "The online assistant service is experiencing issues. " +
                            "Using local helper in the meantime.",
                    actionHint = "Retry later or continue with local suggestions.",
                    showRetry = true,
                )
        }
    }

    /**
     * Get a short status label for the error type.
     */
    fun getStatusLabel(failure: AssistantBackendFailure?): String {
        if (failure == null) return "Online"

        return when (failure.type) {
            AssistantBackendErrorType.AUTH_REQUIRED -> "Sign In Required"
            AssistantBackendErrorType.AUTH_INVALID -> "Session Expired"
            AssistantBackendErrorType.UNAUTHORIZED -> "Not Authorized"
            AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED -> "Not Configured"
            AssistantBackendErrorType.RATE_LIMITED -> "Rate Limited"
            AssistantBackendErrorType.NETWORK_TIMEOUT -> "Timed Out"
            AssistantBackendErrorType.NETWORK_UNREACHABLE -> "Offline"
            AssistantBackendErrorType.VISION_UNAVAILABLE -> "Vision Unavailable"
            AssistantBackendErrorType.VALIDATION_ERROR -> "Request Error"
            AssistantBackendErrorType.PROVIDER_UNAVAILABLE -> "Unavailable"
        }
    }

    /**
     * Get a concise error reason for logging/debugging.
     */
    fun getDebugReason(failure: AssistantBackendFailure): String {
        val typeStr = failure.type.name.lowercase()
        val categoryStr = failure.category.name.lowercase()
        val retryStr = if (failure.retryable) "retryable" else "non-retryable"
        val retryAfter = failure.retryAfterSeconds?.let { ", retry_after=${it}s" } ?: ""
        return "[$typeStr/$categoryStr/$retryStr$retryAfter] ${failure.message ?: "no message"}"
    }

    private fun buildRateLimitExplanation(failure: AssistantBackendFailure): String {
        val base = "You've sent too many requests in a short time."
        val retryAfter = failure.retryAfterSeconds
        return if (retryAfter != null && retryAfter > 0) {
            "$base Please wait $retryAfter seconds before trying again."
        } else {
            "$base Please wait a moment before trying again."
        }
    }
}
