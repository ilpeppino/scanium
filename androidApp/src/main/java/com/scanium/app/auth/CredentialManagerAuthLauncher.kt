package com.scanium.app.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production implementation of AuthLauncher using Credential Manager.
 * Requires Activity context to show the Google Sign-In UI.
 */
class CredentialManagerAuthLauncher : AuthLauncher {
    override suspend fun startGoogleSignIn(activity: Activity): Result<String> =
        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Starting Google Sign-In flow via Credential Manager")

                val credentialManager = CredentialManager.create(activity)

                val googleIdOption =
                    GetGoogleIdOption
                        .Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(GOOGLE_SERVER_CLIENT_ID)
                        .build()

                val request =
                    GetCredentialRequest
                        .Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                val result: GetCredentialResponse =
                    credentialManager.getCredential(
                        request = request,
                        context = activity,
                    )

                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = credential.idToken

                Log.i(TAG, "Successfully obtained Google ID token")
                Result.success(idToken)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-In failed - GetCredentialException: ${e.type}", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed - unexpected exception: ${e.javaClass.simpleName}", e)
                Result.failure(e)
            }
        }

    companion object {
        private const val TAG = "CredentialManagerAuthLauncher"

        // This must match GOOGLE_OAUTH_CLIENT_ID in backend .env
        private const val GOOGLE_SERVER_CLIENT_ID = "480326569434-9cje4dkffu16ol5126q7pt6oihshtn5k.apps.googleusercontent.com"
    }
}
