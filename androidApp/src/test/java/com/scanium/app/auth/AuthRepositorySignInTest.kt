package com.scanium.app.auth

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scanium.app.config.SecureApiKeyStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        // Default: not signed in (userInfo is null)
        every { mockApiKeyStore.getUserInfo() } returns null

        // Create test activity
        mockActivity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Create repository with mocks
        val context = ApplicationProvider.getApplicationContext<Context>()
        authRepository =
            AuthRepository(
                context = context,
                apiKeyStore = mockApiKeyStore,
                authApi = mockAuthApi,
                authLauncher = mockAuthLauncher,
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
    fun `signInWithGoogle invokes AuthLauncher with Activity context`() =
        runTest {
            // Arrange
            val fakeToken = "fake_google_id_token"
            val fakeAuthResponse =
                GoogleAuthResponse(
                    accessToken = "fake_access_token",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    user =
                        UserResponse(
                            id = "user123",
                            email = "test@example.com",
                            displayName = "Test User",
                            pictureUrl = null,
                        ),
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
    fun `signInWithGoogle propagates AuthLauncher exceptions`() =
        runTest {
            // Arrange
            val exception = Exception("User cancelled sign-in")
            coEvery { mockAuthLauncher.startGoogleSignIn(any()) } returns Result.failure(exception)

            // Act
            val result = authRepository.signInWithGoogle(mockActivity)

            // Assert
            assertTrue("Sign-in should fail", result.isFailure)
            assertTrue(
                "Exception message should be propagated",
                result.exceptionOrNull()?.message?.contains("User cancelled") == true,
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
    fun `signInWithGoogle propagates backend exchange failures`() =
        runTest {
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
                result.exceptionOrNull()?.message?.contains("Backend unavailable") == true,
            )

            // Verify no token was stored
            verify(exactly = 0) {
                mockApiKeyStore.setAuthToken(any())
            }
        }

    // =========================================================================
    // Regression tests for userInfoFlow reactivity (AUTH-UX-001)
    // =========================================================================

    /**
     * CRITICAL REGRESSION TEST: userInfoFlow emits immediately after sign-in.
     *
     * This test guards against the stale label bug where Settings → General
     * showed "Continue with Google" after sign-in because state was read once
     * via `remember { viewModel.getUserInfo() }` instead of collected reactively.
     *
     * Fix: AuthRepository.userInfoFlow is a StateFlow that emits on sign-in/sign-out.
     */
    @Test
    fun `userInfoFlow emits user info immediately after successful sign-in`() =
        runTest {
            // Arrange
            val fakeToken = "fake_google_id_token"
            val expectedUserInfo =
                SecureApiKeyStore.UserInfo(
                    id = "user123",
                    email = "test@example.com",
                    displayName = "Test User",
                    pictureUrl = null,
                )
            val fakeAuthResponse =
                GoogleAuthResponse(
                    accessToken = "fake_access_token",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    user =
                        UserResponse(
                            id = expectedUserInfo.id,
                            email = expectedUserInfo.email,
                            displayName = expectedUserInfo.displayName,
                            pictureUrl = expectedUserInfo.pictureUrl,
                        ),
                )

            coEvery { mockAuthLauncher.startGoogleSignIn(any()) } returns Result.success(fakeToken)
            coEvery { mockAuthApi.exchangeToken(fakeToken) } returns Result.success(fakeAuthResponse)

            // Verify initial state is null (not signed in)
            assertNull(
                "userInfoFlow should initially be null when not signed in",
                authRepository.userInfoFlow.value,
            )

            // Act: Sign in
            val result = authRepository.signInWithGoogle(mockActivity)

            // Assert: Sign-in succeeded
            assertTrue("Sign-in should succeed", result.isSuccess)

            // CRITICAL ASSERTION: userInfoFlow should emit immediately (not require navigation)
            val emittedUserInfo = authRepository.userInfoFlow.value
            assertNotNull(
                "userInfoFlow must emit user info immediately after sign-in (fixes stale label bug)",
                emittedUserInfo,
            )
            assertEquals(
                "userInfoFlow should emit correct user ID",
                expectedUserInfo.id,
                emittedUserInfo?.id,
            )
            assertEquals(
                "userInfoFlow should emit correct email",
                expectedUserInfo.email,
                emittedUserInfo?.email,
            )
            assertEquals(
                "userInfoFlow should emit correct display name",
                expectedUserInfo.displayName,
                emittedUserInfo?.displayName,
            )
        }

    /**
     * REGRESSION TEST: userInfoFlow emits null immediately after sign-out.
     */
    @Test
    fun `userInfoFlow emits null immediately after sign-out`() =
        runTest {
            // Arrange: Set up mock to return user info initially
            val existingUser =
                SecureApiKeyStore.UserInfo(
                    id = "user123",
                    email = "test@example.com",
                    displayName = "Test User",
                    pictureUrl = null,
                )
            every { mockApiKeyStore.getUserInfo() } returns existingUser
            every { mockApiKeyStore.getAuthToken() } returns "existing_token"
            coEvery { mockAuthApi.logout(any()) } returns Result.success(Unit)

            // Create a new repository to pick up the initial user
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repositoryWithUser =
                AuthRepository(
                    context = context,
                    apiKeyStore = mockApiKeyStore,
                    authApi = mockAuthApi,
                    authLauncher = mockAuthLauncher,
                )

            // Verify initial state has user
            assertNotNull(
                "userInfoFlow should have user info initially",
                repositoryWithUser.userInfoFlow.value,
            )

            // Act: Sign out
            val result = repositoryWithUser.signOut()

            // Assert
            assertTrue("Sign-out should succeed", result.isSuccess)

            // CRITICAL: userInfoFlow should emit null immediately
            assertNull(
                "userInfoFlow must emit null immediately after sign-out",
                repositoryWithUser.userInfoFlow.value,
            )
        }
}
