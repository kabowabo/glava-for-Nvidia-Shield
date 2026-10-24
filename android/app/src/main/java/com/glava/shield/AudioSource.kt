package com.glava.shield

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

/**
 * Captures the device's global output mix (audio session 0) with
 * [Visualizer], so whatever Spotify is playing in the background feeds the
 * visualizer. Each capture runs through GLava's bars pipeline
 * (window -> fft -> gravity -> avg); the raw waveform is kept for `wave`.
 */
class AudioSource(private val settings: Settings) {
    companion object {
        const val TAG = "GlavaShield"
    }

    private var visualizer: Visualizer? = null
    private var gravity: Dsp.Gravity? = null
    private var average: Dsp.Average? = null
    private var updatesPerSecond = 20f
    private var captures = 0

    private val lock = Any()
    private var fftOut = FloatArray(0)
    private var waveOut = FloatArray(0)
    private var fresh = false

    /** Capture size in samples, 0 until [start] succeeds. */
    @Volatile
    var size = 0
        private set

    fun start(): Boolean {
        if (visualizer != null) return true
        return try {
            val v = Visualizer(0)
            v.enabled = false
            val n = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.captureSize = n
            val rate = Visualizer.getMaxCaptureRate()
            updatesPerSecond = rate / 1000f
            synchronized(lock) {
                fftOut = FloatArray(n)
                waveOut = FloatArray(n)
            }
            gravity = Dsp.Gravity(n)
            average = Dsp.Average(n, settings.int("avg"))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    process(waveform)
                }

                override fun onFftDataCapture(vis: Visualizer, fft: ByteArray, samplingRate: Int) {}
            }, rate, true, false)
            v.enabled = true
            visualizer = v
            size = n
            Log.i(TAG, "Visualizer started: captureSize=$n rate=${rate / 1000f}Hz")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach Visualizer to the output mix", e)
            false
        }
    }

    fun stop() {
        visualizer?.let {
            it.enabled = false
            it.release()
        }
        visualizer = null
        size = 0
    }

    private fun process(waveform: ByteArray) {
        val n = waveform.size
        if (n != fftOut.size) return
        val samples = FloatArray(n)
        val wave = FloatArray(n)
        var peak = 0f
        for (i in 0 until n) {
            /* 8-bit unsigned PCM centered at 128 */
            val s = ((waveform[i].toInt() and 0xFF) - 128) / 128f
            samples[i] = s
            wave[i] = (s + 1f) / 2f /* `wrange` transform */
            peak = max(peak, abs(s))
        }
        Dsp.fft(samples)
        gravity?.apply(samples, updatesPerSecond, settings["gravity"])
        val frames = settings.int("avg").coerceAtLeast(1)
        if (average?.frameCount != frames) average = Dsp.Average(n, frames)
        average?.apply(samples)

        synchronized(lock) {
            samples.copyInto(fftOut)
            wave.copyInto(waveOut)
            fresh = true
        }

        if (++captures % 100 == 0) {
            Log.d(TAG, "capture #$captures peak=%.3f fftMax=%.3f".format(peak, samples.max()))
        }
    }

    /** Copies the latest frame into the given arrays; returns false if nothing new arrived. */
    fun read(fft: FloatArray, wave: FloatArray): Boolean = synchronized(lock) {
        if (!fresh || fft.size != fftOut.size) return false
        fftOut.copyInto(fft)
        waveOut.copyInto(wave)
        fresh = false
        true
    }
}
