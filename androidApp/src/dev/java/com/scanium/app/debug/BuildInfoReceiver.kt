package com.scanium.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.scanium.app.BuildConfig

/**
 * Debug-only broadcast receiver for querying installed build information.
 * Used by install scripts to verify the installed APK matches the expected git SHA.
 *
 * Usage:
 *   adb shell am broadcast -a com.scanium.app.DEBUG_BUILD_INFO
 *
 * Output format in logcat:
 *   BUILD_INFO|<packageName>|<versionName>|<versionCode>|<flavor>|<buildType>|<gitSha>|<buildTime>
 *
 * This receiver is only included in dev flavor builds via src/dev directory.
 */
class BuildInfoReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != ACTION_DEBUG_BUILD_INFO) return

        val packageName = context.packageName
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }

        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

        val flavor = BuildConfig.FLAVOR
        val buildType = BuildConfig.BUILD_TYPE
        val gitSha = BuildConfig.GIT_SHA
        val buildTime = BuildConfig.BUILD_TIME_UTC

        // Format: BUILD_INFO|packageName|versionName|versionCode|flavor|buildType|gitSha|buildTime
        val buildInfo = "BUILD_INFO|$packageName|$versionName|$versionCode|$flavor|$buildType|$gitSha|$buildTime"

        Log.i(TAG, buildInfo)
        Log.i(TAG, "Build info query completed successfully")
    }

    companion object {
        private const val TAG = "BuildInfoReceiver"
        const val ACTION_DEBUG_BUILD_INFO = "com.scanium.app.DEBUG_BUILD_INFO"
    }
}
