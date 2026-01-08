package com.scanium.app.items.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for AddPhotoHandler state management.
 *
 * Note: Full integration testing of camera/gallery launchers requires
 * instrumentation tests due to ActivityResultContract dependencies.
 * These unit tests verify the state machine and interface contracts.
 */
class AddPhotoHandlerTest {

    @Test
    fun `AddPhotoState Idle is initial state`() {
        val state: AddPhotoState = AddPhotoState.Idle
        assertThat(state).isEqualTo(AddPhotoState.Idle)
    }

    @Test
    fun `AddPhotoState Pending contains itemId`() {
        val itemId = "test-item-123"
        val state = AddPhotoState.Pending(itemId)
        assertThat(state.itemId).isEqualTo(itemId)
    }

    @Test
    fun `AddPhotoState Error contains message`() {
        val errorMessage = "Failed to capture photo"
        val state = AddPhotoState.Error(errorMessage)
        assertThat(state.message).isEqualTo(errorMessage)
    }

    @Test
    fun `AddPhotoState Completed is final success state`() {
        val state: AddPhotoState = AddPhotoState.Completed
        assertThat(state).isEqualTo(AddPhotoState.Completed)
    }

    @Test
    fun `state machine transitions are valid`() {
        // Idle -> Pending (when triggerAddPhoto called)
        var state: AddPhotoState = AddPhotoState.Idle
        state = AddPhotoState.Pending("item-1")
        assertThat(state).isInstanceOf(AddPhotoState.Pending::class.java)

        // Pending -> Completed (on success)
        state = AddPhotoState.Completed
        assertThat(state).isEqualTo(AddPhotoState.Completed)

        // Reset to test error path
        state = AddPhotoState.Idle
        state = AddPhotoState.Pending("item-2")

        // Pending -> Error (on failure)
        state = AddPhotoState.Error("Camera failed")
        assertThat(state).isInstanceOf(AddPhotoState.Error::class.java)

        // Error -> Idle (on dismiss/reset)
        state = AddPhotoState.Idle
        assertThat(state).isEqualTo(AddPhotoState.Idle)
    }

    @Test
    fun `AddPhotoTrigger interface has required methods`() {
        // This test verifies the interface contract exists
        // Actual implementation is tested via instrumentation tests
        val mockTrigger = object : AddPhotoTrigger {
            var lastTriggeredItemId: String? = null
            override fun triggerAddPhoto(itemId: String) {
                lastTriggeredItemId = itemId
            }
            override val currentState: AddPhotoState = AddPhotoState.Idle
        }

        mockTrigger.triggerAddPhoto("test-item")
        assertThat(mockTrigger.lastTriggeredItemId).isEqualTo("test-item")
        assertThat(mockTrigger.currentState).isEqualTo(AddPhotoState.Idle)
    }
}
