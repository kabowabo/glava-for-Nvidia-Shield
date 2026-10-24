package com.glava.shield

import android.service.dreams.DreamService

/**
 * Screensaver: when the Shield goes idle while Spotify plays, the visualizer
 * takes over the screen. Uses the module/amplify chosen in [MainActivity].
 * Requires RECORD_AUDIO to have been granted (open the app once, or `adb shell pm grant`).
 */
class VisualizerDream : DreamService() {
    private var view: VisualizerView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        view = VisualizerView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }
}
