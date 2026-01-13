package com.scanium.app.auth

import android.app.Activity

/**
 * Abstraction for launching Google Sign-In flow.
 * Production implementation uses Credential Manager.
 * Test implementation can return fake tokens.
 */
interface AuthLauncher {
    /**
     * Initiates Google Sign-In flow.
     *
     * @param activity The Activity context required for showing the sign-in UI
     * @return GoogleIdToken wrapped in Result
     */
    suspend fun startGoogleSignIn(activity: Activity): Result<String>
}
