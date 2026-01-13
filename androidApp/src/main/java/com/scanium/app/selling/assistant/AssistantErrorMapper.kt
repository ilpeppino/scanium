package com.scanium.app.selling.assistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

internal class AssistantErrorMapper {
    fun mapHttpFailure(
        code: Int,
        responseBody: String?,
    ): AssistantBackendException {
        val assistantError =
            responseBody?.let {
                runCatching { ASSISTANT_JSON.decodeFromString(AssistantErrorResponse.serializer(), it) }.getOrNull()
            }?.assistantError

        if (assistantError != null) {
            return AssistantBackendException(assistantError.toFailure())
        }

        val errorResponse = responseBody?.let {
            runCatching { ASSISTANT_JSON.decodeFromString<ErrorResponseDto>(it) }.getOrNull()
        }

        val failure =
            when (code) {
                400 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.VALIDATION_ERROR,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Assistant request invalid",
                    )
                401 -> {
                    val errorCode = errorResponse?.error?.code?.uppercase()
                    when (errorCode) {
                        "AUTH_REQUIRED" ->
                            AssistantBackendFailure(
                                type = AssistantBackendErrorType.AUTH_REQUIRED,
                                category = AssistantBackendErrorCategory.POLICY,
                                retryable = false,
                                message = errorResponse.error.message ?: "Sign in required",
                            )
                        "AUTH_INVALID" ->
                            AssistantBackendFailure(
                                type = AssistantBackendErrorType.AUTH_INVALID,
                                category = AssistantBackendErrorCategory.POLICY,
                                retryable = false,
                                message = errorResponse.error.message ?: "Session expired",
                            )
                        else ->
                            AssistantBackendFailure(
                                type = AssistantBackendErrorType.UNAUTHORIZED,
                                category = AssistantBackendErrorCategory.POLICY,
                                retryable = false,
                                message = "Not authorized to use assistant",
                            )
                    }
                }
                403 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.UNAUTHORIZED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Access to assistant denied",
                    )
                429 -> {
                    val resetAt = errorResponse?.error?.resetAt
                    val retryAfterSeconds = resetAt?.let {
                        val resetTime = runCatching {
                            java.time.Instant.parse(it).toEpochMilli()
                        }.getOrNull()
                        if (resetTime != null) {
                            ((resetTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        } else null
                    }
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.RATE_LIMITED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = true,
                        retryAfterSeconds = retryAfterSeconds,
                        message = errorResponse?.error?.message ?: "Assistant rate limit exceeded",
                    )
                }
                503 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant provider unavailable",
                    )
                504 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant gateway timeout",
                    )
                else ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant backend error",
                    )
            }
        return AssistantBackendException(failure)
    }
}

@Serializable
private data class AssistantErrorResponse(
    val assistantError: AssistantErrorDto? = null,
)

@Serializable
private data class ErrorResponseDto(
    val error: ErrorDetailsDto,
)

@Serializable
private data class ErrorDetailsDto(
    val code: String,
    val message: String,
    val resetAt: String? = null,
)

@Serializable
internal data class AssistantErrorDto(
    val type: String,
    val category: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val message: String? = null,
) {
    fun toFailure(): AssistantBackendFailure {
        return AssistantBackendFailure(
            type = parseType(type),
            category = parseCategory(category),
            retryable = retryable,
            retryAfterSeconds = retryAfterSeconds,
            message = message,
        )
    }

    private fun parseType(raw: String): AssistantBackendErrorType {
        return when (raw.lowercase()) {
            "unauthorized" -> AssistantBackendErrorType.UNAUTHORIZED
            "auth_required" -> AssistantBackendErrorType.AUTH_REQUIRED
            "auth_invalid" -> AssistantBackendErrorType.AUTH_INVALID
            "provider_not_configured" -> AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED
            "rate_limited" -> AssistantBackendErrorType.RATE_LIMITED
            "network_timeout" -> AssistantBackendErrorType.NETWORK_TIMEOUT
            "network_unreachable" -> AssistantBackendErrorType.NETWORK_UNREACHABLE
            "vision_unavailable" -> AssistantBackendErrorType.VISION_UNAVAILABLE
            "validation_error" -> AssistantBackendErrorType.VALIDATION_ERROR
            else -> AssistantBackendErrorType.PROVIDER_UNAVAILABLE
        }
    }

    private fun parseCategory(raw: String): AssistantBackendErrorCategory {
        return when (raw.lowercase()) {
            "policy" -> AssistantBackendErrorCategory.POLICY
            else -> AssistantBackendErrorCategory.TEMPORARY
        }
    }
}
