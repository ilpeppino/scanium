package com.scanium.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.scanium.app.BuildConfig
import com.scanium.app.config.AndroidCloudConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Status of a health check.
 */
enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    DOWN,
    UNKNOWN,
}

/**
 * Result of a single health check.
 */
data class HealthCheckResult(
    val name: String,
    val status: HealthStatus,
    val detail: String? = null,
    val latencyMs: Long? = null,
    val lastChecked: Long = System.currentTimeMillis(),
) {
    val lastCheckedFormatted: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastChecked))
}

/**
 * Network transport type.
 */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    UNKNOWN,
    NONE,
}

/**
 * Network status information.
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val transport: NetworkTransport,
    val isMetered: Boolean,
    val lastChecked: Long = System.currentTimeMillis(),
)

/**
 * Permission status.
 */
data class PermissionStatus(
    val name: String,
    val isGranted: Boolean,
    val permissionKey: String,
)

/**
 * Platform capability status.
 */
data class CapabilityStatus(
    val name: String,
    val isAvailable: Boolean,
    val detail: String? = null,
)

/**
 * App configuration snapshot (safe, non-sensitive).
 */
data class AppConfigSnapshot(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val baseUrl: String,
    val isDebugBuild: Boolean,
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
)

/**
 * Complete diagnostics state.
 */
data class DiagnosticsState(
    val backendHealth: HealthCheckResult = HealthCheckResult("Backend", HealthStatus.UNKNOWN),
    val networkStatus: NetworkStatus = NetworkStatus(false, NetworkTransport.UNKNOWN, false),
    val permissions: List<PermissionStatus> = emptyList(),
    val capabilities: List<CapabilityStatus> = emptyList(),
    val appConfig: AppConfigSnapshot? = null,
    val isRefreshing: Boolean = false,
)

/**
 * Repository for system diagnostics and health checks.
 * Debug-only - should not be used in release builds.
 */
class DiagnosticsRepository(
    private val context: Context,
) {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val configProvider = AndroidCloudConfigProvider(context)

    /**
     * Refresh all diagnostics checks.
     */
    suspend fun refreshAll() {
        _state.value = _state.value.copy(isRefreshing = true)

        val backendHealth = checkBackendHealth()
        val networkStatus = checkNetworkStatus()
        val permissions = checkPermissions()
        val capabilities = checkCapabilities()
        val appConfig = getAppConfigSnapshot()

        _state.value =
            DiagnosticsState(
                backendHealth = backendHealth,
                networkStatus = networkStatus,
                permissions = permissions,
                capabilities = capabilities,
                appConfig = appConfig,
                isRefreshing = false,
            )
    }

    /**
     * Check backend health by pinging the /health endpoint.
     */
    suspend fun checkBackendHealth(): HealthCheckResult =
        withContext(Dispatchers.IO) {
            val config = configProvider.current()
            val baseUrl = config.baseUrl

            if (baseUrl.isBlank()) {
                return@withContext HealthCheckResult(
                    name = "Backend",
                    status = HealthStatus.DOWN,
                    detail = "Base URL not configured",
                )
            }

            val healthUrl = "${baseUrl.trimEnd('/')}/health"
            val startTime = System.currentTimeMillis()

            try {
                val result =
                    withTimeoutOrNull(HEALTH_CHECK_TIMEOUT_MS) {
                        val request =
                            Request.Builder()
                                .url(healthUrl)
                                .get()
                                .build()

                        httpClient.newCall(request).execute().use { response ->
                            val latency = System.currentTimeMillis() - startTime

                            when {
                                response.isSuccessful ->
                                    HealthCheckResult(
                                        name = "Backend",
                                        status = HealthStatus.HEALTHY,
                                        detail = "OK (${response.code})",
                                        latencyMs = latency,
                                    )
                                response.code in 401..403 ->
                                    HealthCheckResult(
                                        name = "Backend",
                                        status = HealthStatus.DEGRADED,
                                        detail = "Reachable (Auth required: ${response.code})",
                                        latencyMs = latency,
                                    )
                                response.code in 500..599 ->
                                    HealthCheckResult(
                                        name = "Backend",
                                        status = HealthStatus.DOWN,
                                        detail = "Server error (${response.code})",
                                        latencyMs = latency,
                                    )
                                else ->
                                    HealthCheckResult(
                                        name = "Backend",
                                        status = HealthStatus.DEGRADED,
                                        detail = "Unexpected response (${response.code})",
                                        latencyMs = latency,
                                    )
                            }
                        }
                    }

                result ?: HealthCheckResult(
                    name = "Backend",
                    status = HealthStatus.DOWN,
                    detail = "Timeout after ${HEALTH_CHECK_TIMEOUT_SECONDS}s",
                )
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                HealthCheckResult(
                    name = "Backend",
                    status = HealthStatus.DOWN,
                    detail = e.message?.take(50) ?: "Connection failed",
                    latencyMs = latency,
                )
            }
        }

    /**
     * Check network connectivity status.
     */
    fun checkNetworkStatus(): NetworkStatus {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        if (network == null || capabilities == null) {
            return NetworkStatus(
                isConnected = false,
                transport = NetworkTransport.NONE,
                isMetered = false,
            )
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val transport =
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
                else -> NetworkTransport.UNKNOWN
            }

        return NetworkStatus(
            isConnected = hasInternet,
            transport = transport,
            isMetered = isMetered,
        )
    }

    /**
     * Check critical permissions.
     */
    fun checkPermissions(): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                name = "Camera",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                permissionKey = Manifest.permission.CAMERA,
            ),
            PermissionStatus(
                name = "Microphone",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                permissionKey = Manifest.permission.RECORD_AUDIO,
            ),
        )
    }

    /**
     * Check platform capabilities.
     */
    fun checkCapabilities(): List<CapabilityStatus> {
        val capabilities = mutableListOf<CapabilityStatus>()

        // Speech recognition
        val speechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        capabilities.add(
            CapabilityStatus(
                name = "Speech Recognition",
                isAvailable = speechAvailable,
                detail = if (speechAvailable) "Available" else "Not available on this device",
            ),
        )

        // Text-to-Speech - basic availability check
        capabilities.add(
            CapabilityStatus(
                name = "Text-to-Speech",
                isAvailable = true,
// TTS is generally available on all Android devices
                detail = "Available",
            ),
        )

        // Camera availability
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList

            var hasBack = false
            var hasFront = false

            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK -> hasBack = true
                    CameraCharacteristics.LENS_FACING_FRONT -> hasFront = true
                }
            }

            val cameraDetail =
                buildString {
                    if (hasBack) append("Back")
                    if (hasBack && hasFront) append(", ")
                    if (hasFront) append("Front")
                    if (!hasBack && !hasFront) append("None detected")
                }

            capabilities.add(
                CapabilityStatus(
                    name = "Camera Lenses",
                    isAvailable = hasBack || hasFront,
                    detail = cameraDetail,
                ),
            )
        } catch (e: Exception) {
            capabilities.add(
                CapabilityStatus(
                    name = "Camera Lenses",
                    isAvailable = false,
                    detail = "Error checking cameras",
                ),
            )
        }

        return capabilities
    }

    /**
     * Get app configuration snapshot (safe, non-sensitive).
     */
    fun getAppConfigSnapshot(): AppConfigSnapshot {
        val config = configProvider.current()

        return AppConfigSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = if (BuildConfig.DEBUG) "debug" else "release",
            baseUrl = config.baseUrl.ifBlank { "(not configured)" },
            isDebugBuild = BuildConfig.DEBUG,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    /**
     * Generate a plaintext diagnostics summary for copying.
     * Does NOT include sensitive data.
     */
    fun generateDiagnosticsSummary(): String {
        val state = _state.value
        val appConfig = state.appConfig ?: getAppConfigSnapshot()

        return buildString {
            appendLine("=== Scanium Diagnostics ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()

            appendLine("--- App Info ---")
            appendLine("Version: ${appConfig.versionName} (${appConfig.versionCode})")
            appendLine("Build: ${appConfig.buildType}")
            appendLine("Device: ${appConfig.deviceModel}")
            appendLine("Android: ${appConfig.androidVersion} (SDK ${appConfig.sdkInt})")
            appendLine()

            appendLine("--- Backend ---")
            appendLine("Status: ${state.backendHealth.status}")
            state.backendHealth.detail?.let { appendLine("Detail: $it") }
            state.backendHealth.latencyMs?.let { appendLine("Latency: ${it}ms") }
            appendLine("Base URL: ${appConfig.baseUrl}")
            appendLine("Last checked: ${state.backendHealth.lastCheckedFormatted}")
            appendLine()

            appendLine("--- Network ---")
            appendLine("Connected: ${if (state.networkStatus.isConnected) "Yes" else "No"}")
            appendLine("Transport: ${state.networkStatus.transport}")
            appendLine("Metered: ${if (state.networkStatus.isMetered) "Yes" else "No"}")
            appendLine()

            appendLine("--- Permissions ---")
            state.permissions.forEach { perm ->
                appendLine("${perm.name}: ${if (perm.isGranted) "Granted" else "Not granted"}")
            }
            appendLine()

            appendLine("--- Capabilities ---")
            state.capabilities.forEach { cap ->
                appendLine("${cap.name}: ${if (cap.isAvailable) "Available" else "Unavailable"}")
                cap.detail?.let { appendLine("  $it") }
            }
        }
    }

    companion object {
        private const val HEALTH_CHECK_TIMEOUT_SECONDS = 3L
        private const val HEALTH_CHECK_TIMEOUT_MS = HEALTH_CHECK_TIMEOUT_SECONDS * 1000L
    }
}
