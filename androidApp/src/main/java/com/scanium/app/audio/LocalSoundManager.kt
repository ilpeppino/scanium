package com.scanium.app.audio

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSoundManager = staticCompositionLocalOf<SoundManager> { NoopSoundManager }

object NoopSoundManager : SoundManager {
    override fun preload() = Unit

    override fun play(sound: AppSound) = Unit

    override fun release() = Unit
}
