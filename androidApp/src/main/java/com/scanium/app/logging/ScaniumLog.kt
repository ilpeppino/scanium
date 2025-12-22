package com.scanium.app.logging

import android.util.Log
import com.scanium.app.BuildConfig

object ScaniumLog {
    private const val PREFIX = "Scanium"

    @Volatile
    var verboseEnabled: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (!verboseEnabled) return
        Log.d("$PREFIX/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$PREFIX/$tag", message, throwable)
        } else {
            Log.w("$PREFIX/$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$PREFIX/$tag", message, throwable)
        } else {
            Log.e("$PREFIX/$tag", message)
        }
    }
}
