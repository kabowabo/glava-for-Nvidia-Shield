package com.glava.shield

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin

/**
 * Ports of GLava's CPU-side audio transforms (glava/render.c) and the
 * `smooth_audio` sampling function (shaders/glava/util/smooth.glsl), using
 * the default values from shaders/glava/smooth_parameters.glsl.
 */
object Dsp {
    const val FFT_SCALE = 10.2f      /* setfftscale */
    const val FFT_CUTOFF = 0.3f      /* setfftcutoff */
    const val AVG_WINDOW = true      /* setavgwindow */
    /* setgravitystep, setavgframes and setsmoothfactor are user settings (see Settings) */
    const val SAMPLE_SCALE = 8f
    const val SAMPLE_RANGE = 0.9f

    private const val TWOPI = 2.0 * PI

    private fun window(t: Int, sz: Int) = (0.53836 - 0.46164 * cos(TWOPI * t / sz)).toFloat()
    private fun windowFrame(t: Int, sz: Int) = (0.6 - 0.4 * cos(TWOPI * t / sz)).toFloat()
    private fun sinusoidal(x: Float) = (0.5 * sin(PI * x - PI / 2) + 0.5).toFloat()

    /** `transform_fft`: in-place window, radix-2 FFT, abs and log scale. */
    fun fft(data: FloatArray) {
        val sz = data.size
        for (i in 0 until sz) data[i] *= window(i, sz - 1)

        val nn = sz / 2
        val n = nn shl 1
        var j = 1
        var i = 1
        while (i < n) {
            if (j > i) {
                var t = data[j - 1]; data[j - 1] = data[i - 1]; data[i - 1] = t
                t = data[j]; data[j] = data[i]; data[i] = t
            }
            var m = nn
            while (m >= 2 && j > m) {
                j -= m
                m = m shr 1
            }
            j += m
            i += 2
        }

        var mmax = 2
        while (n > mmax) {
            val istep = mmax shl 1
            val theta = -(2 * PI / mmax)
            var wtemp = sin(0.5 * theta)
            val wpr = -2.0 * wtemp * wtemp
            val wpi = sin(theta)
            var wr = 1.0
            var wi = 0.0
            var m = 1
            while (m < mmax) {
                i = m
                while (i <= n) {
                    j = i + mmax
                    val tempr = (wr * data[j - 1] - wi * data[j]).toFloat()
                    val tempi = (wr * data[j] + wi * data[j - 1]).toFloat()
                    data[j - 1] = data[i - 1] - tempr
                    data[j] = data[i] - tempi
                    data[i - 1] += tempr
                    data[i] += tempi
                    i += istep
                }
                wtemp = wr
                wr += wr * wpr - wi * wpi
                wi += wi * wpr + wtemp * wpi
                m += 2
            }
            mmax = istep
        }

        for (k in 0 until sz) {
            data[k] = ln(abs(data[k]) + 1f) / 3f
            data[k] *= max((k.toFloat() / sz) * FFT_SCALE + (1f - FFT_CUTOFF), 1f)
        }
    }

    /** `transform_gravity`: peaks fall at `gravityStep` per second. */
    class Gravity(size: Int) {
        private val applied = FloatArray(size)

        fun apply(b: FloatArray, updatesPerSecond: Float, gravityStep: Float) {
            val g = gravityStep / updatesPerSecond
            for (t in b.indices) {
                if (b[t] >= applied[t]) applied[t] = b[t] - g
                else applied[t] -= g
                b[t] = applied[t]
            }
        }
    }

    /** `transform_average`: windowed average over the last `frameCount` frames. */
    class Average(private val size: Int, val frameCount: Int) {
        private val frames = Array(frameCount) { FloatArray(size) }
        private var head = 0

        fun apply(b: FloatArray) {
            b.copyInto(frames[head])
            head = (head + 1) % frameCount
            if (frameCount == 1) return
            for (t in 0 until size) {
                var v = 0f
                for (f in 0 until frameCount) {
                    /* f = 0 is the oldest frame, matching GLava's buffer order */
                    val w = if (AVG_WINDOW && frameCount > 2) windowFrame(f, frameCount - 1) else 1f
                    v += w * frames[(head + f) % frameCount][t]
                }
                b[t] = v / frameCount
            }
        }
    }

    private fun scaleAudio(idx: Float) = -ln(-SAMPLE_RANGE * idx + 1f) / SAMPLE_SCALE

    /**
     * `smooth_audio` in `average` mode, evaluated on the CPU for `out.size`
     * evenly spaced positions (GLava's "smooth pass"). Shaders then index the
     * result directly, like GLava does with `_PRE_SMOOTHED_AUDIO`.
     */
    fun smooth(tex: FloatArray, out: FloatArray, smoothFactor: Float) {
        val texSz = tex.size
        for (k in out.indices) {
            val idx = k.toFloat() / (out.size - 1)
            val smin = scaleAudio((idx - smoothFactor).coerceIn(0f, 1f)) * texSz
            val smax = scaleAudio((idx + smoothFactor).coerceIn(0f, 1f)) * texSz
            val m = (smax - smin) / 2f
            val rm = smin + m
            var avg = 0f
            var weight = 0f
            var s = smin
            while (s <= smax) {
                val w = sinusoidal(((m - abs(rm - s)) / m).coerceIn(0f, 1f))
                weight += w
                avg += tex[round(s).toInt().coerceIn(0, texSz - 1)] * w
                s += 1f
            }
            out[k] = if (weight > 0f) avg / weight else 0f
        }
    }
}
