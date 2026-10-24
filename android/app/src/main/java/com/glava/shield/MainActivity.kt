package com.glava.shield

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Full-screen visualizer. Start music in Spotify, then open this app.
 *
 * Remote controls:
 *   ◀ ▶      switch visualizer         ▲ ▼  amplify
 *   OK/MENU  open settings
 * In settings: ▲ ▼ select, ◀ ▶ change, OK on "Reset" resets, BACK closes.
 */
class MainActivity : Activity() {
    private lateinit var view: VisualizerView
    private val settings get() = view.settings

    private lateinit var menu: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var rows: LinearLayout
    private lateinit var hud: TextView
    private var rowViews = listOf<Pair<TextView, TextView>>()
    private var items = listOf<Settings.Setting?>() /* null = "Reset to defaults" */
    private var selected = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hideHud = Runnable { hud.animate().alpha(0f).setDuration(400) }
    private val hideMenu = Runnable { closeMenu() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = VisualizerView(this)

        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        buildMenu(root)
        buildHud(root)
        setContentView(root)

        @Suppress("DEPRECATION")
        root.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (!hasAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission()) startVisualizer()
    }

    override fun onPause() {
        view.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (hasAudioPermission()) startVisualizer()
        else showHud("GLava needs the audio permission to see what's playing")
    }

    private fun startVisualizer() {
        if (!view.start()) showHud("Couldn't attach to the audio output (see logcat tag GlavaShield)")
        else showHud("Press OK for settings")
    }

    private fun hasAudioPermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /* ---- remote ---- */

    private val menuOpen get() = menu.visibility == View.VISIBLE

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuOpen) {
            handler.removeCallbacks(hideMenu)
            handler.postDelayed(hideMenu, 20_000)
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> select(selected - 1)
                KeyEvent.KEYCODE_DPAD_DOWN -> select(selected + 1)
                KeyEvent.KEYCODE_DPAD_LEFT -> change(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> change(1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (items[selected] == null) resetSettings() else change(1)
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> closeMenu()
                else -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        val mode = Settings.ALL.first { it.key == "mode" }
        val gain = Settings.ALL.first { it.key == "gain" }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> stepAndShow(mode, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> stepAndShow(mode, -1)
            KeyEvent.KEYCODE_DPAD_UP -> stepAndShow(gain, 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> stepAndShow(gain, -1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> openMenu()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun stepAndShow(s: Settings.Setting, dir: Int) {
        settings.step(s, dir)
        showHud("${s.label}: ${settings.display(s)}")
    }

    /* ---- settings menu ---- */

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun buildMenu(root: FrameLayout) {
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(14f))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 12, 14, 22))
                cornerRadius = dp(14f).toFloat()
            }
            visibility = View.GONE
        }
        menu.addView(TextView(this).apply {
            text = "GLava settings"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8f), 0, 0, dp(10f))
        })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFocusable = false
            addView(rows)
        }
        menu.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        menu.addView(TextView(this).apply {
            text = "▲▼ select    ◀▶ change    BACK close"
            setTextColor(Color.argb(150, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8f), dp(10f), 0, 0)
        })
        root.addView(menu, FrameLayout.LayoutParams(dp(400f), MATCH_PARENT).apply {
            gravity = Gravity.END
            setMargins(dp(24f), dp(24f), dp(24f), dp(24f))
        })
    }

    private fun buildHud(root: FrameLayout) {
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 12, 14, 22))
                cornerRadius = dp(20f).toFloat()
            }
            alpha = 0f
        }
        root.addView(hud, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(36f)
        })
    }

    private fun showHud(msg: String) {
        hud.text = msg
        hud.animate().cancel()
        hud.alpha = 1f
        handler.removeCallbacks(hideHud)
        handler.postDelayed(hideHud, 2000)
    }

    private fun openMenu() {
        hud.alpha = 0f
        rebuildRows()
        menu.visibility = View.VISIBLE
        select(0)
        handler.postDelayed(hideMenu, 20_000)
    }

    private fun closeMenu() {
        handler.removeCallbacks(hideMenu)
        menu.visibility = View.GONE
    }

    /** Rows depend on the current visualizer (each has its own options). */
    private fun rebuildRows() {
        rows.removeAllViews()
        items = settings.visible() + listOf(null)
        rowViews = items.map { s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10f), dp(7f), dp(10f), dp(7f))
            }
            val label = TextView(this).apply {
                text = s?.label ?: "Reset to defaults"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
            val value = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.END
            }
            row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(value, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            rows.addView(row)
            label to value
        }
        refreshRows()
    }

    private fun refreshRows() {
        items.forEachIndexed { i, s ->
            val (label, value) = rowViews[i]
            val sel = i == selected
            val row = rows.getChildAt(i)
            row.background = if (sel) GradientDrawable().apply {
                setColor(Color.argb(60, 120, 160, 255))
                cornerRadius = dp(8f).toFloat()
            } else null
            label.setTextColor(if (sel) Color.WHITE else Color.argb(200, 220, 220, 230))
            value.text = when {
                s == null -> if (sel) "press OK" else ""
                sel -> "‹  ${settings.display(s)}  ›"
                else -> settings.display(s)
            }
            value.setTextColor(
                if (s != null && !settings.isDefault(s)) Color.rgb(140, 190, 255)
                else Color.argb(170, 220, 220, 230)
            )
        }
    }

    private fun select(i: Int) {
        selected = (i + items.size) % items.size
        refreshRows()
        val row = rows.getChildAt(selected)
        scroll.post { scroll.smoothScrollTo(0, row.top - scroll.height / 3) }
    }

    private fun change(dir: Int) {
        val s = items[selected] ?: return
        settings.step(s, dir)
        if (s.key == "mode") {
            /* the option list depends on the visualizer */
            rebuildRows()
            select(0)
        } else refreshRows()
    }

    private fun resetSettings() {
        settings.reset()
        rebuildRows()
        select(items.size - 1)
        showHud("Settings reset")
    }
}
