package com.scanium.app.auth

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scanium.app.config.SecureApiKeyStore
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Unit test for AuthRepository sign-in flow.
 *
 * PURPOSE: Verify the fix for the bug where tapping "Sign in to Google" did nothing.
 *
 * ROOT CAUSE (before fix):
 * - CredentialManager.getCredential() was called with Application context
 * - It requires Activity context to show the sign-in UI
 * - Exception was caught and only logged (no UI feedback)
 *
 * FIX:
 * - AuthRepository.signInWithGoogle() now accepts Activity parameter
 * - AuthLauncher abstraction allows testing without real Credential Manager
 * - UI shows loading state and error messages
 *
 * This test verifies:
 * 1. AuthLauncher.startGoogleSignIn() is called with Activity context
 * 2. Sign-in flow handles Activity properly
 * 3. Exceptions are propagated (not silently swallowed)
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositorySignInTest {
    private lateinit var mockAuthLauncher: AuthLauncher
    private lateinit var mockAuthApi: GoogleAuthApi
    private lateinit var mockApiKeyStore: SecureApiKeyStore
    private lateinit var authRepository: AuthRepository
    private lateinit var mockActivity: Activity

    @Before
    fun setUp() {
        // Create mocks
        mockAuthLauncher = mockk()
        mockAuthApi = mockk()
        mockApiKeyStore = mockk(relaxed = true)

        // Create test activity
        mockActivity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Create repository with mocks
        val context = ApplicationProvider.getApplicationContext<Context>()
        authRepository = AuthRepository(
            context = context,
            apiKeyStore = mockApiKeyStore,
            authApi = mockAuthApi,
            authLauncher = mockAuthLauncher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * CRITICAL TEST: Verify AuthLauncher is invoked with Activity context.
     * This test ensures the bug is fixed - AuthLauncher MUST be called.
     */
    @Test
    fun `signInWithGoogle invokes AuthLauncher with Activity context`() = runTest {
        // Arrange
        val fakeToken = "fake_google_id_token"
        val fakeAuthResponse = GoogleAuthResponse(
            accessToken = "fake_access_token",
            tokenType = "Bearer",
            expiresIn = 3600,
            user = UserResponse(
                id = "user123",
                email = "test@example.com",
                displayName = "Test User",
                pictureUrl = null
            )
        )

        coEvery { mockAuthLauncher.startGoogleSignIn(any()) } returns Result.success(fakeToken)
        coEvery { mockAuthApi.exchangeToken(fakeToken) } returns Result.success(fakeAuthResponse)

        // Act
        val result = authRepository.signInWithGoogle(mockActivity)

        // Assert
        assertTrue("Sign-in should succeed", result.isSuccess)

        // CRITICAL: Verify AuthLauncher was called with Activity
        coVerify(exactly = 1) {
            mockAuthLauncher.startGoogleSignIn(mockActivity)
        }

        // Verify backend exchange was called
        coVerify(exactly = 1) {
            mockAuthApi.exchangeToken(fakeToken)
        }

        // Verify token was stored
        verify {
            mockApiKeyStore.setAuthToken(fakeAuthResponse.accessToken)
        }
    }

    /**
     * Test: AuthLauncher failure is propagated (not silently swallowed).
     */
    @Test
    fun `signInWithGoogle propagates AuthLauncher exceptions`() = runTest {
        // Arrange
        val exception = Exception("User cancelled sign-in")
        coEvery { mockAuthLauncher.startGoogleSignIn(any()) } returns Result.failure(exception)

        // Act
        val result = authRepository.signInWithGoogle(mockActivity)

        // Assert
        assertTrue("Sign-in should fail", result.isFailure)
        assertTrue(
            "Exception message should be propagated",
            result.exceptionOrNull()?.message?.contains("User cancelled") == true
        )

        // Verify AuthLauncher was still called (not silently skipped)
        coVerify(exactly = 1) {
            mockAuthLauncher.startGoogleSignIn(mockActivity)
        }

        // Verify backend was NOT called (failed earlier)
        coVerify(exactly = 0) {
            mockAuthApi.exchangeToken(any())
        }

        // Verify no token was stored
        verify(exactly = 0) {
            mockApiKeyStore.setAuthToken(any())
        }
    }

    /**
     * Test: Backend exchange failure is propagated.
     */
    @Test
    fun `signInWithGoogle propagates backend exchange failures`() = runTest {
        // Arrange
        val fakeToken = "fake_google_id_token"
        val backendError = Exception("Backend unavailable")

        coEvery { mockAuthLauncher.startGoogleSignIn(any()) } returns Result.success(fakeToken)
        coEvery { mockAuthApi.exchangeToken(fakeToken) } returns Result.failure(backendError)

        // Act
        val result = authRepository.signInWithGoogle(mockActivity)

        // Assert
        assertTrue("Sign-in should fail when backend fails", result.isFailure)
        assertTrue(
            "Backend error should be propagated",
            result.exceptionOrNull()?.message?.contains("Backend unavailable") == true
        )

        // Verify no token was stored
        verify(exactly = 0) {
            mockApiKeyStore.setAuthToken(any())
        }
    }
}
