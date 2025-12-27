package com.scanium.app.audio

interface SoundManager {
    fun preload()
    fun play(sound: AppSound)
    fun release()
}
