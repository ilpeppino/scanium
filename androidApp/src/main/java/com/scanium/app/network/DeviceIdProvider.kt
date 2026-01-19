package com.scanium.app.network

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Provides device identification for backend rate limiting.
 *
 * The device ID is hashed before sending to the backend for privacy.
 * Backend uses this for per-device rate limiting without storing raw device identifiers.
 */
object DeviceIdProvider {
    /**
     * Get the raw Android device ID.
     * Returns empty string if unavailable.
     */
    fun getRawDeviceId(context: Context): String =
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }

    /**
     * Get the hashed device ID for sending to backend.
     * Backend expects this in the X-Scanium-Device-Id header.
     *
     * @param context Android context
     * @return SHA-256 hashed device ID, or empty string if unavailable
     */
    fun getHashedDeviceId(context: Context): String {
        val rawId = getRawDeviceId(context)
        if (rawId.isBlank()) return ""
        return hashDeviceId(rawId)
    }

    /**
     * Hash a device ID using SHA-256.
     */
    private fun hashDeviceId(deviceId: String): String =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceId.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            deviceId // Fallback to original if hashing fails
        }
}
