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
     */
    val overallReadiness: AssistantReadiness
        get() =
            when {
                isChecking -> AssistantReadiness.CHECKING
                prerequisiteState.allSatisfied &&
                    isNetworkConnected &&
                    backendReachable == BackendReachabilityStatus.REACHABLE -> AssistantReadiness.READY
                !isNetworkConnected -> AssistantReadiness.NO_NETWORK
                backendReachable == BackendReachabilityStatus.UNREACHABLE -> AssistantReadiness.BACKEND_UNREACHABLE
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
 */
enum class BackendReachabilityStatus {
    UNKNOWN,
    CHECKING,
    REACHABLE,
    UNREACHABLE,
    DEGRADED,
}

/**
 * Overall assistant readiness.
 */
enum class AssistantReadiness {
    UNKNOWN,
    CHECKING,
    READY,
    NO_NETWORK,
    BACKEND_UNREACHABLE,
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
