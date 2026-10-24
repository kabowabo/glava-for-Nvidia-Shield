package com.glava.shield

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * GL surface + audio capture, shared by [MainActivity] and [VisualizerDream].
 * Renders at a fixed 1920x1080 (scaled by the hardware compositor) so GLava's
 * pixel-based shader settings look the same on any TV resolution.
 */
class VisualizerView(context: Context) : GLSurfaceView(context) {
    val settings = Settings(context)
    private val audio = AudioSource(settings)
    private val renderer = VisualizerRenderer(context, audio, settings)

    init {
        setEGLContextClientVersion(3)
        holder.setFixedSize(1920, 1080)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Starts rendering and audio capture; returns false if the Visualizer couldn't attach. */
    fun start(): Boolean {
        onResume()
        return audio.start()
    }

    fun stop() {
        audio.stop()
        onPause()
    }
}
