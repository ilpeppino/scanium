# Voice Mode Validation Checklist

Use this quick list on a device/emulator before shipping changes to PR6.

## Speech-to-Text input
1. Open Settings → Voice Mode and enable **Voice input (microphone)**. Approve the microphone permission prompt.
2. Return to the assistant, tap the mic, and ask “What color is it?”.
3. Confirm the listening banner appears, the transcript is inserted into the text field, and you can edit it before tapping Send.
4. Disable **Auto-send after voice recognition**, repeat the flow, and verify the message is *not* sent automatically.
5. Enable Auto-send, dictate “What brand is it?”, and confirm it sends immediately after transcription.

## Text-to-Speech output
1. Enable **Read assistant replies aloud** in Settings.
2. Ask any question in the assistant and confirm the final answer is spoken with the “Speaking...” chip visible.
3. Tap the chip to stop playback and verify TTS stops immediately.

## Privacy, lifecycle, and fallbacks
1. Deny the microphone permission when prompted and confirm the toggle snaps back off with the snackbar explanation.
2. Disable **Voice input (microphone)** in Settings and verify the mic icon disappears in the assistant.
3. Background the app (or rotate the device) while speaking/listening; the mic/tts should stop with no crash.
4. On a device without SpeechRecognizer support, verify the mic toggle stays disabled and the “Voice input unavailable on this device” banner is shown.
