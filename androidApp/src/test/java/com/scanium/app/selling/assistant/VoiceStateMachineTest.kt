package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceStateMachineTest {

    @Test
    fun `state machine follows happy path`() {
        val machine = VoiceStateMachine()

        assertThat(machine.state.value).isEqualTo(VoiceState.IDLE)

        machine.onStartListening()
        assertThat(machine.state.value).isEqualTo(VoiceState.LISTENING)

        machine.onTranscribing()
        assertThat(machine.state.value).isEqualTo(VoiceState.TRANSCRIBING)

        machine.onSpeaking()
        assertThat(machine.state.value).isEqualTo(VoiceState.SPEAKING)

        machine.onIdle()
        assertThat(machine.state.value).isEqualTo(VoiceState.IDLE)
    }

    @Test
    fun `state machine exposes error state and can recover`() {
        val machine = VoiceStateMachine()

        machine.onError("Microphone permission denied")
        assertThat(machine.state.value).isEqualTo(VoiceState.ERROR)
        assertThat(machine.error.value).isEqualTo("Microphone permission denied")

        machine.clearError()
        assertThat(machine.error.value).isNull()

        machine.onIdle()
        assertThat(machine.state.value).isEqualTo(VoiceState.IDLE)
    }
}
