package com.scanium.app.items.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.scanium.app.BuildConfig
import com.scanium.app.items.ItemsViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "AddPhotoHandler"

/**
 * State for the Add Photo flow.
 */
sealed class AddPhotoState {
    data object Idle : AddPhotoState()

    data class Pending(
        val itemId: String,
    ) : AddPhotoState()

    data object Completed : AddPhotoState()

    data class Error(
        val message: String,
    ) : AddPhotoState()
}

/**
 * Handler result callback.
 */
interface AddPhotoCallback {
    fun onPhotoAdded(itemId: String)

    fun onCancelled()

    fun onError(message: String)
}

/**
 * Composable that handles adding photos to items via camera or gallery.
 *
 * Usage:
 * 1. Call `triggerAddPhoto(itemId)` to start the flow
 * 2. The handler will:
 *    - Check camera permission
 *    - Launch camera (or gallery fallback)
 *    - On success, add photo to item and trigger vision extraction
 *
 * @param itemsViewModel The ViewModel to add photos to
 * @param onPhotoAdded Callback when photo is successfully added
 */
@Composable
fun rememberAddPhotoHandler(
    itemsViewModel: ItemsViewModel,
    onPhotoAdded: (itemId: String) -> Unit = {},
    onError: (message: String) -> Unit = {},
): AddPhotoTrigger {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<AddPhotoState>(AddPhotoState.Idle) }
    var pendingItemId by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val itemId = pendingItemId
            val uri = pendingCameraUri

            if (success && itemId != null && uri != null) {
                Log.i(TAG, "Camera captured photo for item $itemId")
                scope.launch {
                    try {
                        itemsViewModel.addPhotoToItem(context, itemId, uri)
                        state = AddPhotoState.Completed
                        onPhotoAdded(itemId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add photo", e)
                        state = AddPhotoState.Error(e.message ?: "Failed to add photo")
                        onError(e.message ?: "Failed to add photo")
                    }
                }
            } else {
                Log.d(TAG, "Camera capture cancelled or failed for item $itemId")
                state = AddPhotoState.Idle
            }

            pendingItemId = null
            pendingCameraUri = null
        }

    // Gallery launcher (fallback)
    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            val itemId = pendingItemId

            if (uri != null && itemId != null) {
                Log.i(TAG, "Gallery selected photo for item $itemId")
                scope.launch {
                    try {
                        itemsViewModel.addPhotoToItem(context, itemId, uri)
                        state = AddPhotoState.Completed
                        onPhotoAdded(itemId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add photo from gallery", e)
                        state = AddPhotoState.Error(e.message ?: "Failed to add photo")
                        onError(e.message ?: "Failed to add photo")
                    }
                }
            } else {
                Log.d(TAG, "Gallery selection cancelled for item $itemId")
                state = AddPhotoState.Idle
            }

            pendingItemId = null
        }

    // Permission launcher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val itemId = pendingItemId
            if (granted && itemId != null) {
                Log.i(TAG, "Camera permission granted, launching camera for item $itemId")
                // Create URI and launch camera
                val uri = createCameraUri(context)
                if (uri != null) {
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    Log.e(TAG, "Failed to create camera URI, falling back to gallery")
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            } else if (itemId != null) {
                Log.w(TAG, "Camera permission denied, falling back to gallery")
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } else {
                state = AddPhotoState.Idle
            }
        }

    return remember(cameraLauncher, galleryLauncher, permissionLauncher) {
        object : AddPhotoTrigger {
            override fun triggerAddPhoto(itemId: String) {
                Log.i(TAG, "Add photo triggered for item $itemId")
                pendingItemId = itemId
                state = AddPhotoState.Pending(itemId)

                // Check if camera is available
                if (!isCameraAvailable(context)) {
                    Log.w(TAG, "Camera not available, using gallery")
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    return
                }

                // Check camera permission
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Camera permission already granted, launching camera")
                    val uri = createCameraUri(context)
                    if (uri != null) {
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        Log.e(TAG, "Failed to create camera URI, falling back to gallery")
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                } else {
                    Log.d(TAG, "Requesting camera permission")
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override val currentState: AddPhotoState
                get() = state
        }
    }
}

/**
 * Interface for triggering photo addition.
 */
interface AddPhotoTrigger {
    fun triggerAddPhoto(itemId: String)

    val currentState: AddPhotoState
}

/**
 * Check if device has a camera.
 */
private fun isCameraAvailable(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/**
 * Create a content URI for camera capture using FileProvider.
 */
private fun createCameraUri(context: Context): Uri? =
    try {
        // Create the captures directory
        val capturesDir = File(context.cacheDir, "camera_captures")
        capturesDir.mkdirs()

        // Create a unique file for this capture
        val photoFile = File(capturesDir, "capture_${UUID.randomUUID()}.jpg")

        // Get the content URI via FileProvider
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create camera URI: ${e.message}", e)
        null
    }
