package com.scanium.app.diagnostics

import com.scanium.app.model.config.AssistantPrerequisiteState
import com.scanium.app.model.config.ConnectionTestResult

/**
 * Diagnostic state specifically for the AI Assistant.
 * Used in Developer Options to show assistant readiness and capabilities.
 */
data class AssistantDiagnosticsState(
    /** Backend health status (reachability) */
    val backendReachable: BackendReachabilityStatus = BackendReachabilityStatus.UNKNOWN,
    /** Assistant readiness based on prerequisites */
    val prerequisiteState: AssistantPrerequisiteState = AssistantPrerequisiteState.LOADING,
    /** Connection test result (last ping) */
    val connectionTestResult: ConnectionTestResult? = null,
    /** Whether network is connected */
    val isNetworkConnected: Boolean = false,
    /** Network type description */
    val networkType: String = "Unknown",
    /** Whether microphone permission is granted */
    val hasMicrophonePermission: Boolean = false,
    /** Whether speech recognition is available on device */
    val isSpeechRecognitionAvailable: Boolean = false,
    /** Whether text-to-speech is available */
    val isTextToSpeechAvailable: Boolean = false,
    /** Whether TTS is initialized and ready */
    val isTtsReady: Boolean = false,
    /** Whether a check is in progress */
    val isChecking: Boolean = false,
    /** Last check timestamp */
    val lastChecked: Long = 0L,
) {
    /**
     * Overall readiness status for the assistant.
     * Properly distinguishes between network failures and server-side issues.
     */
    val overallReadiness: AssistantReadiness
        get() =
            when {
                isChecking -> AssistantReadiness.CHECKING

                prerequisiteState.allSatisfied &&
                    isNetworkConnected &&
                    backendReachable == BackendReachabilityStatus.REACHABLE -> AssistantReadiness.READY

                !isNetworkConnected -> AssistantReadiness.NO_NETWORK

                // Map backend status to appropriate readiness (with accurate messages)
                backendReachable == BackendReachabilityStatus.UNREACHABLE -> AssistantReadiness.BACKEND_UNREACHABLE

                backendReachable == BackendReachabilityStatus.UNAUTHORIZED -> AssistantReadiness.BACKEND_UNAUTHORIZED

                backendReachable == BackendReachabilityStatus.SERVER_ERROR -> AssistantReadiness.BACKEND_SERVER_ERROR

                backendReachable == BackendReachabilityStatus.NOT_FOUND -> AssistantReadiness.BACKEND_NOT_FOUND

                backendReachable == BackendReachabilityStatus.NOT_CONFIGURED -> AssistantReadiness.BACKEND_NOT_CONFIGURED

                !prerequisiteState.allSatisfied -> AssistantReadiness.PREREQUISITES_NOT_MET

                else -> AssistantReadiness.UNKNOWN
            }

    /**
     * Voice capability readiness.
     */
    val voiceReadiness: VoiceReadiness
        get() =
            when {
                !hasMicrophonePermission -> VoiceReadiness.NO_MIC_PERMISSION
                !isSpeechRecognitionAvailable -> VoiceReadiness.STT_UNAVAILABLE
                !isTextToSpeechAvailable -> VoiceReadiness.TTS_UNAVAILABLE
                !isTtsReady -> VoiceReadiness.TTS_NOT_READY
                else -> VoiceReadiness.READY
            }
}

/**
 * Backend reachability status.
 * Distinguishes between "unreachable" (network issues) and "reachable but failing"
 * (auth errors, server errors, etc.) to provide accurate diagnostics.
 */
enum class BackendReachabilityStatus {
    /** Status not yet checked */
    UNKNOWN,

    /** Check in progress */
    CHECKING,

    /** Backend responded successfully (200) */
    REACHABLE,

    /** Network error - cannot reach backend (DNS, timeout, connection refused, SSL) */
    UNREACHABLE,

    /** Backend reached but returned 401/403 - invalid API key or unauthorized */
    UNAUTHORIZED,

    /** Backend reached but returned 5xx - server is having issues */
    SERVER_ERROR,

    /** Backend reached but returned 404 - wrong endpoint or route */
    NOT_FOUND,

    /** Backend URL or API key not configured */
    NOT_CONFIGURED,

    /** Backend reachable but returned unexpected status */
    DEGRADED,
}

/**
 * Overall assistant readiness.
 * Provides accurate, actionable status messages distinguishing network vs server issues.
 */
enum class AssistantReadiness {
    UNKNOWN,
    CHECKING,
    READY,
    NO_NETWORK,

    /** Network error - cannot reach backend (DNS, timeout, connection refused) */
    BACKEND_UNREACHABLE,

    /** Backend reachable but API key invalid or unauthorized (401/403) */
    BACKEND_UNAUTHORIZED,

    /** Backend reachable but server is having issues (5xx) */
    BACKEND_SERVER_ERROR,

    /** Backend reachable but endpoint not found (404) */
    BACKEND_NOT_FOUND,

    /** Backend URL or API key not configured */
    BACKEND_NOT_CONFIGURED,
    PREREQUISITES_NOT_MET,
}

/**
 * Voice input/output readiness.
 */
enum class VoiceReadiness {
    READY,
    NO_MIC_PERMISSION,
    STT_UNAVAILABLE,
    TTS_UNAVAILABLE,
    TTS_NOT_READY,
}
