package com.glava.shield

import android.content.Context
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp

/**
 * Draws one of the ported GLava modules as a full-screen quad. Audio frames
 * arrive at the Visualizer capture rate (~20Hz), so the renderer eases
 * toward each new frame to keep the animation smooth at display rate.
 */
class VisualizerRenderer(
    private val context: Context,
    private val audio: AudioSource,
    private val settings: Settings,
) : GLSurfaceView.Renderer {

    companion object {
        private const val SMOOTH_SIZE = 1024
        private const val INTERP_RATE = 18.0
        private const val HEADER = "#version 300 es\nprecision highp float;\nprecision highp int;\n"
    }

    private val programs = IntArray(Settings.MODES.size)
    private val uniforms = HashMap<Pair<Int, String>, Int>()
    private val textures = IntArray(2) /* 0: smoothed fft, 1: waveform */
    private var width = 1
    private var height = 1

    private var audioSize = 0
    private var targetFft = FloatArray(0)
    private var targetWave = FloatArray(0)
    private var shownFft = FloatArray(0)
    private var shownWave = FloatArray(0)
    private val smoothed = FloatArray(SMOOTH_SIZE)
    private val upload: FloatBuffer = direct(SMOOTH_SIZE)
    private var lastFrame = 0L
    private var rotation = 0.0

    private val quad = direct(8).apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    private fun direct(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vert = compile(GL_VERTEX_SHADER, HEADER + asset("quad.vert"))
        uniforms.clear()
        Settings.MODES.forEachIndexed { i, name ->
            val frag = compile(GL_FRAGMENT_SHADER, HEADER + resolveIncludes(asset("$name.frag")))
            programs[i] = link(vert, frag)
        }
        glGenTextures(2, textures, 0)
        for (t in textures) {
            glBindTexture(GL_TEXTURE_2D, t)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        }
        allocTexture(textures[0], SMOOTH_SIZE)
        audioSize = 0
        /* empty fragments are transparent so the background color shows through */
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val bg = settings.background
        glClearColor(bg[0], bg[1], bg[2], 1f)
        glClear(GL_COLOR_BUFFER_BIT)

        val n = audio.size
        if (n == 0) return
        if (n != audioSize) {
            audioSize = n
            targetFft = FloatArray(n); shownFft = FloatArray(n)
            targetWave = FloatArray(n); shownWave = FloatArray(n)
            allocTexture(textures[1], n)
        }
        audio.read(targetFft, targetWave)

        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrame == 0L) 0.0 else (now - lastFrame) / 1000.0
        lastFrame = now
        val k = (1.0 - exp(-dt * INTERP_RATE)).toFloat()

        val modeName = settings.mode
        val wave = modeName == "wave"
        if (wave) {
            for (i in 0 until n) shownWave[i] += (targetWave[i] - shownWave[i]) * k
            uploadTexture(textures[1], shownWave)
        } else {
            for (i in 0 until n) shownFft[i] += (targetFft[i] - shownFft[i]) * k
            Dsp.smooth(shownFft, smoothed, settings["smooth"])
            uploadTexture(textures[0], smoothed)
        }

        rotation = (rotation + settings["spin"] * dt) % (2 * Math.PI)

        val prog = programs[Settings.MODES.indexOf(modeName)]
        glUseProgram(prog)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textures[if (wave) 1 else 0])
        glUniform1i(u(prog, "audio_l"), 0)
        glUniform1i(u(prog, "audio_r"), 0) /* mono mix: mirror both sides */
        glUniform1i(u(prog, "audio_sz"), if (wave) n else SMOOTH_SIZE)
        glUniform2i(u(prog, "screen"), width, height)
        glUniform1f(u(prog, "gain"), settings["gain"])

        val theme = settings.theme
        val defaults = Settings.DEFAULT_COLORS.getValue(modeName)
        val lo = theme.lo ?: defaults.first
        val hi = theme.hi ?: defaults.second
        glUniform3f(u(prog, "color_lo"), lo[0], lo[1], lo[2])
        glUniform3f(u(prog, "color_hi"), hi[0], hi[1], hi[2])
        glUniform1i(u(prog, "rainbow"), if (theme.rainbow) 1 else 0)

        when (modeName) {
            "bars" -> {
                glUniform1f(u(prog, "bar_width"), settings["bar_width"])
                glUniform1f(u(prog, "bar_gap"), settings["bar_gap"])
                glUniform1i(u(prog, "bar_outline"), if (settings.int("bar_outline") == 0) 1 else 0)
                glUniform1i(u(prog, "mirror"), if (settings.int("bar_layout") == 0) 1 else 0)
                glUniform1i(u(prog, "flip"), settings.int("bar_flip"))
            }
            "radial" -> {
                glUniform1f(u(prog, "radius"), settings["radius"])
                glUniform1f(u(prog, "nbars"), settings["nbars"])
                glUniform1f(u(prog, "bar_width"), settings["radial_width"])
                glUniform1f(u(prog, "rotation"), rotation.toFloat())
            }
            "wave" -> glUniform1f(u(prog, "thickness"), settings["thickness"])
        }

        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, quad)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun u(prog: Int, name: String) =
        uniforms.getOrPut(prog to name) { glGetUniformLocation(prog, name) }

    private fun allocTexture(tex: Int, size: Int) {
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, size, 1, 0, GL_RED, GL_FLOAT, null)
    }

    private fun uploadTexture(tex: Int, data: FloatArray) {
        upload.clear()
        upload.put(data, 0, data.size)
        upload.position(0)
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, data.size, 1, GL_RED, GL_FLOAT, upload)
    }

    private fun asset(name: String) =
        context.assets.open("shaders/$name").bufferedReader().use { it.readText() }

    /** Minimal stand-in for GLava's `#include` directive. */
    private fun resolveIncludes(src: String): String =
        Regex("""^\s*#include\s+"([^"]+)"\s*$""", RegexOption.MULTILINE)
            .replace(src) { resolveIncludes(asset(it.groupValues[1])) }

    private fun compile(type: Int, src: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, src)
        glCompileShader(s)
        val ok = IntArray(1)
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = glGetShaderInfoLog(s)
            Log.e(AudioSource.TAG, "Shader compile failed:\n$log")
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }

    private fun link(vert: Int, frag: Int): Int {
        val p = glCreateProgram()
        glAttachShader(p, vert)
        glAttachShader(p, frag)
        glBindAttribLocation(p, 0, "pos")
        glLinkProgram(p)
        val ok = IntArray(1)
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = glGetProgramInfoLog(p)
            Log.e(AudioSource.TAG, "Program link failed:\n$log")
            throw RuntimeException("Program link failed: $log")
        }
        return p
    }
}
