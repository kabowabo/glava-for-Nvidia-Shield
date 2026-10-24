package com.glava.shield

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every user-adjustable option, persisted in SharedPreferences. Read live by
 * the renderer (GL thread) and the audio callback, written from the remote.
 */
class Settings(context: Context) {

    sealed class Setting(val key: String, val label: String, val module: String?, val default: Float) {
        abstract fun display(v: Float): String
        abstract fun step(v: Float, dir: Int): Float
    }

    class Choice(key: String, label: String, module: String?, val choices: List<String>, default: Int = 0) :
        Setting(key, label, module, default.toFloat()) {
        override fun display(v: Float) = choices[v.toInt().coerceIn(0, choices.size - 1)]
        override fun step(v: Float, dir: Int) =
            (((v.toInt() + dir) % choices.size + choices.size) % choices.size).toFloat()
    }

    class Range(
        key: String, label: String, module: String?, default: Float,
        val min: Float, val max: Float, val inc: Float, val format: String = "%.0f",
    ) : Setting(key, label, module, default) {
        override fun display(v: Float) = format.format(v)
        override fun step(v: Float, dir: Int): Float {
            val n = ((v + dir * inc) / inc).roundToInt() * inc /* snap to the step grid */
            return n.coerceIn(min, max)
        }
    }

    class Theme(val name: String, val lo: FloatArray?, val hi: FloatArray?, val rainbow: Boolean = false)

    companion object {
        val MODES = listOf("bars", "radial", "wave")

        val THEMES = listOf(
            Theme("GLava default", null, null),
            Theme("Ocean", rgb(0x1E5AA8), rgb(0x7FD8FF)),
            Theme("Crimson", rgb(0x8B0000), rgb(0xFF6B6B)),
            Theme("Spotify", rgb(0x1DB954), rgb(0xB7F5CB)),
            Theme("Sunset", rgb(0xFF5E3A), rgb(0xFFD36E)),
            Theme("Purple haze", rgb(0x6A1B9A), rgb(0xE1A6FF)),
            Theme("Ice", rgb(0x5C7C99), rgb(0xFFFFFF)),
            Theme("Neon", rgb(0xFF00A0), rgb(0x00F0FF)),
            Theme("Mono", rgb(0x9A9A9A), rgb(0xFFFFFF)),
            Theme("Rainbow", null, null, rainbow = true),
        )

        /** Per-module colors from the original GLava configs (bars.glsl, radial.glsl, wave.glsl). */
        val DEFAULT_COLORS = mapOf(
            "bars" to (rgb(0x3366B2) to rgb(0xA0A0B2)),
            "radial" to (rgb(0xCC3333) to rgb(0xCCA0A0)),
            "wave" to (floatArrayOf(0.7f, 0.2f, 0.45f) to floatArrayOf(1f, 0.75f, 0.95f)),
        )

        val BACKGROUNDS = listOf(
            "Black" to rgb(0x000000),
            "Midnight" to rgb(0x0A0F24),
            "Deep purple" to rgb(0x160A24),
            "Charcoal" to rgb(0x161616),
        )

        val ALL: List<Setting> = listOf(
            Choice("mode", "Visualizer", null, listOf("Bars", "Radial", "Wave")),
            Choice("theme", "Color theme", null, THEMES.map { it.name }),
            Choice("background", "Background", null, BACKGROUNDS.map { it.first }),
            Range("gain", "Amplify", null, 1f, 0.1f, 5f, 0.1f, "x%.1f"),
            Range("smooth", "Smoothing", null, 0.025f, 0.005f, 0.08f, 0.005f, "%.3f"),
            Range("gravity", "Fall speed", null, 4.2f, 0.5f, 12f, 0.5f, "%.1f"),
            Range("avg", "Averaging (frames)", null, 5f, 1f, 10f, 1f),

            Range("bar_width", "Bar width", "bars", 5f, 1f, 30f, 1f, "%.0f px"),
            Range("bar_gap", "Bar gap", "bars", 1f, 0f, 15f, 1f, "%.0f px"),
            Choice("bar_outline", "Bar outline", "bars", listOf("On", "Off")),
            Choice("bar_layout", "Layout", "bars", listOf("Mirrored from center", "Low to high")),
            Choice("bar_flip", "Position", "bars", listOf("Bottom", "Top")),

            Range("radius", "Circle radius", "radial", 200f, 50f, 450f, 10f, "%.0f px"),
            Range("nbars", "Number of bars", "radial", 160f, 40f, 320f, 20f),
            Range("radial_width", "Bar width", "radial", 4.5f, 1f, 12f, 0.5f, "%.1f px"),
            Range("spin", "Rotation speed", "radial", 0f, -2f, 2f, 0.1f, "%.1f"),

            Range("thickness", "Line thickness", "wave", 6f, 1f, 20f, 1f, "%.0f px"),
        )

        private fun rgb(c: Int) = floatArrayOf(
            ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f,
        )
    }

    private val prefs = context.getSharedPreferences("glava", Context.MODE_PRIVATE)
    private val values = ConcurrentHashMap<String, Float>()

    init {
        for (s in ALL) {
            val v = try { prefs.getFloat(s.key, s.default) } catch (e: ClassCastException) { s.default }
            values[s.key] = v
        }
    }

    operator fun get(key: String): Float = values[key] ?: 0f
    fun int(key: String): Int = get(key).toInt()

    val mode: String get() = MODES[int("mode").coerceIn(0, MODES.size - 1)]
    val theme: Theme get() = THEMES[int("theme").coerceIn(0, THEMES.size - 1)]
    val background: FloatArray get() = BACKGROUNDS[int("background").coerceIn(0, BACKGROUNDS.size - 1)].second

    /** Settings shown in the menu: the global ones plus those for the current module. */
    fun visible(): List<Setting> = ALL.filter { it.module == null || it.module == mode }

    fun step(s: Setting, dir: Int) = set(s, s.step(get(s.key), dir))

    fun set(s: Setting, v: Float) {
        values[s.key] = v
        prefs.edit().putFloat(s.key, v).apply()
    }

    fun display(s: Setting): String = s.display(get(s.key))

    fun isDefault(s: Setting) = abs(get(s.key) - s.default) < 1e-4f

    fun reset() {
        for (s in ALL) if (s.key != "mode") set(s, s.default)
    }
}
