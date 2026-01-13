package com.scanium.app.camera

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.scanium.app.R
import com.scanium.app.ui.theme.ScaniumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPermissionContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun permissionDeniedShowsGrantButton() {
        val permissionState = FakePermissionState(
            status = PermissionStatus.Denied(shouldShowRationale = true),
        )

        composeTestRule.setContent {
            ScaniumTheme {
                PermissionDeniedContent(
                    permissionState = permissionState,
                    onRequestPermission = {},
                    onOpenSettings = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.camera_permission_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.camera_grant_camera_access))
            .assertIsDisplayed()
    }

    @Test
    fun permissionPermanentlyDeniedShowsSettingsAction() {
        val permissionState = FakePermissionState(
            status = PermissionStatus.Denied(shouldShowRationale = false),
        )

        composeTestRule.setContent {
            ScaniumTheme {
                PermissionDeniedContent(
                    permissionState = permissionState,
                    onRequestPermission = {},
                    onOpenSettings = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.camera_permission_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.camera_open_settings))
            .assertIsDisplayed()
    }

    private class FakePermissionState(
        override val status: PermissionStatus,
    ) : PermissionState {
        override val permission: String = Manifest.permission.CAMERA

        override fun launchPermissionRequest() = Unit
    }
}
