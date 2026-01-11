package com.scanium.app.monitoring

import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for notification permission decision logic.
 * Tests when to request permission and when to show settings CTA.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPermissionLogicTest {

    /**
     * Decision logic for whether notification permission should be requested.
     */
    private fun shouldRequestNotificationPermission(
        sdkVersion: Int,
        hasPermission: Boolean,
    ): Boolean {
        return sdkVersion >= Build.VERSION_CODES.TIRAMISU && !hasPermission
    }

    /**
     * Decision logic for whether to show settings CTA.
     */
    private fun shouldShowSettingsCTA(
        hasPermission: Boolean,
        areNotificationsEnabled: Boolean,
    ): Boolean {
        return !hasPermission || !areNotificationsEnabled
    }

    // =========================================================================
    // shouldRequestNotificationPermission tests
    // =========================================================================

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13
    fun `Android 13+ without permission - should request`() {
        val result = shouldRequestNotificationPermission(
            sdkVersion = Build.VERSION_CODES.TIRAMISU,
            hasPermission = false,
        )
        assertThat(result).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13
    fun `Android 13+ with permission - should not request`() {
        val result = shouldRequestNotificationPermission(
            sdkVersion = Build.VERSION_CODES.TIRAMISU,
            hasPermission = true,
        )
        assertThat(result).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // Android 12
    fun `Android 12 without permission - should not request`() {
        val result = shouldRequestNotificationPermission(
            sdkVersion = Build.VERSION_CODES.S,
            hasPermission = false,
        )
        assertThat(result).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]) // Android 14
    fun `Android 14 without permission - should request`() {
        val result = shouldRequestNotificationPermission(
            sdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            hasPermission = false,
        )
        assertThat(result).isTrue()
    }

    // =========================================================================
    // shouldShowSettingsCTA tests
    // =========================================================================

    @Test
    fun `no permission - should show settings CTA`() {
        val result = shouldShowSettingsCTA(
            hasPermission = false,
            areNotificationsEnabled = true,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `notifications disabled at system level - should show settings CTA`() {
        val result = shouldShowSettingsCTA(
            hasPermission = true,
            areNotificationsEnabled = false,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `both permission denied and notifications disabled - should show settings CTA`() {
        val result = shouldShowSettingsCTA(
            hasPermission = false,
            areNotificationsEnabled = false,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `permission granted and notifications enabled - should not show settings CTA`() {
        val result = shouldShowSettingsCTA(
            hasPermission = true,
            areNotificationsEnabled = true,
        )
        assertThat(result).isFalse()
    }

    // =========================================================================
    // Combined scenario tests
    // =========================================================================

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13
    fun `Android 13 fresh install - should request permission and show CTA`() {
        val sdkVersion = Build.VERSION_CODES.TIRAMISU
        val hasPermission = false
        val areNotificationsEnabled = false

        val shouldRequest = shouldRequestNotificationPermission(sdkVersion, hasPermission)
        val shouldShowSettings = shouldShowSettingsCTA(hasPermission, areNotificationsEnabled)

        assertThat(shouldRequest).isTrue()
        assertThat(shouldShowSettings).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13
    fun `Android 13 permission granted - should not request, should not show CTA`() {
        val sdkVersion = Build.VERSION_CODES.TIRAMISU
        val hasPermission = true
        val areNotificationsEnabled = true

        val shouldRequest = shouldRequestNotificationPermission(sdkVersion, hasPermission)
        val shouldShowSettings = shouldShowSettingsCTA(hasPermission, areNotificationsEnabled)

        assertThat(shouldRequest).isFalse()
        assertThat(shouldShowSettings).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // Android 12
    fun `Android 12 - should not request permission (not required)`() {
        val sdkVersion = Build.VERSION_CODES.S
        val hasPermission = false

        val shouldRequest = shouldRequestNotificationPermission(sdkVersion, hasPermission)

        assertThat(shouldRequest).isFalse()
    }
}
