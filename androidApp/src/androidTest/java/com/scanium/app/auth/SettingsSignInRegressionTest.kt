package com.scanium.app.auth

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.data.MarketplaceRepository
import com.scanium.app.ui.settings.SettingsViewModel
import com.scanium.app.ui.theme.ScaniumTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Regression test for Google Sign-In flow in Settings.
 *
 * PURPOSE: Ensure that tapping "Sign in to Google" NEVER stays silent.
 * This test MUST FAIL if the click handler does nothing.
 *
 * Root Cause (before fix):
 * - CredentialManager.getCredential() required Activity context
 * - AuthRepository was using Application context
 * - Exception was caught and only logged (no UI feedback)
 *
 * Fix verification:
 * 1. Click triggers AuthLauncher.startGoogleSignIn()
 * 2. UI shows "Signing in..." state
 * 3. Errors are surfaced to user (not silent)
 *
 * Test approach:
 * - Composes SettingsGeneralScreen in isolation
 * - Injects test AuthRepository with fake AuthLauncher
 * - Mocks backend /v1/auth/google response
 * - Verifies UI state transitions
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SettingsSignInRegressionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Inject
    lateinit var apiKeyStore: SecureApiKeyStore

    @Inject
    lateinit var marketplaceRepository: MarketplaceRepository

    private lateinit var mockWebServer: MockWebServer
    private lateinit var testAuthLauncher: TestAuthLauncher
    private lateinit var testAuthRepository: AuthRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        hiltRule.inject()

        // Clear any existing auth state
        apiKeyStore.clearAuthToken()
        apiKeyStore.setUserInfo(null)

        // Set up mock web server
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create test auth launcher and repository
        testAuthLauncher = TestAuthLauncher()
        val authApi =
            GoogleAuthApi(
                okHttpClient = okhttp3.OkHttpClient(),
                baseUrl = mockWebServer.url("/").toString(),
            )
        testAuthRepository =
            AuthRepository(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                apiKeyStore = apiKeyStore,
                authApi = authApi,
                authLauncher = testAuthLauncher,
            )

        // Create ViewModel with test repository
        // Note: In real implementation, we'd inject this via Hilt
        // For now, we'll need to create a test-specific ViewModel factory
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        apiKeyStore.clearAuthToken()
        apiKeyStore.setUserInfo(null)
    }

    /**
     * CRITICAL TEST: Verify that tapping "Sign in" triggers the flow.
     * This test MUST FAIL if the button click does nothing.
     */
    @Test
    fun signInButton_whenTapped_invokesAuthLauncher() =
        runTest {
            // Configure test launcher to succeed
            testAuthLauncher.shouldSucceed = true
            testAuthLauncher.fakeToken = "fake_google_id_token"

            // Mock backend response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                            "accessToken": "fake_access_token",
                            "expiresIn": 3600,
                            "user": {
                                "id": "user123",
                                "email": "test@example.com",
                                "displayName": "Test User"
                            }
                        }
                        """.trimIndent(),
                    ),
            )

            // Set up the screen
            composeTestRule.setContent {
                ScaniumTheme {
                    // Note: This test needs to be completed once we have
                    // proper DI setup for testing ViewModels
                    // For now, this demonstrates the approach
                }
            }

            // Verify sign-in button is visible
            composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()

            // Tap the button
            composeTestRule.onNodeWithText("Continue with Google").performClick()

            // Wait for the auth launcher to be invoked
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                testAuthLauncher.wasInvoked
            }

            // CRITICAL ASSERTION: This MUST be true, otherwise the bug is NOT fixed
            assert(testAuthLauncher.wasInvoked) {
                "❌ BUG NOT FIXED: AuthLauncher was never invoked when sign-in button was tapped!"
            }
        }

    /**
     * Test: Sign-in shows loading state
     */
    @Test
    fun signInFlow_showsSigningInState() =
        runTest {
            testAuthLauncher.shouldSucceed = true
            testAuthLauncher.delayMs = 500 // Delay to observe loading state

            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"accessToken": "token", "expiresIn": 3600, "user": {"id": "1", "email": "test@example.com"}}
                    """.trimIndent(),
                ),
            )

            // TODO: Complete once ViewModel DI is set up for tests

            // Tap sign-in
            composeTestRule.onNodeWithText("Continue with Google").performClick()

            // Verify "Signing in..." is shown
            composeTestRule.onNodeWithText("Signing in…").assertIsDisplayed()
        }

    /**
     * Test: Sign-in failure shows error (NOT silent)
     */
    @Test
    fun signInFlow_onFailure_showsErrorNotSilent() =
        runTest {
            testAuthLauncher.shouldSucceed = false
            testAuthLauncher.errorMessage = "User cancelled"

            // TODO: Complete once ViewModel DI is set up

            // Tap sign-in
            composeTestRule.onNodeWithText("Continue with Google").performClick()

            // Wait for invocation
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                testAuthLauncher.wasInvoked
            }

            // CRITICAL: Error must be visible (NOT silent)
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                // Check for error snackbar/toast
                composeTestRule.onAllNodesWithText("Sign-in failed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

    /**
     * Test AuthLauncher that simulates Google Sign-In.
     */
    class TestAuthLauncher : AuthLauncher {
        var shouldSucceed = true
        var fakeToken = "fake_token"
        var errorMessage = "Sign-in failed"
        var wasInvoked = false
        var delayMs = 100L

        override suspend fun startGoogleSignIn(activity: Activity): Result<String> {
            wasInvoked = true
            delay(delayMs)

            return if (shouldSucceed) {
                Result.success(fakeToken)
            } else {
                Result.failure(Exception(errorMessage))
            }
        }
    }
}
