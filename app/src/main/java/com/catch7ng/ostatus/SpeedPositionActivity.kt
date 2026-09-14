package com.catch7ng.ostatus

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * SpeedPositionActivity — Position & Size settings for the speed overlay.
 * Same UX as PositionSizeActivity but for SpeedService prefs with size step = 1.
 */
class SpeedPositionActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor  get() = if (dark) Color.BLACK else Color.rgb(242,242,247)
    private val cardColor  get() = if (dark) Color.rgb(28,28,30) else Color.WHITE
    private val primary    get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary  get() = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
    private val separator  get() = if (dark) Color.rgb(56,56,58) else Color.rgb(229,229,234)
    private val controlFill get() = if (dark) Color.rgb(58,58,60) else Color.rgb(229,229,234)

    private var sizeValue: TextView? = null
    private var hValue: TextView? = null
    private var vValue: TextView? = null
    private var leftBtn: TextView? = null
    private var rightBtn: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor; window.navigationBarColor = pageColor
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(pageColor) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(38), dp(20), dp(28)) }

        content.addView(BackChevronView(this).apply { setOnClickListener { finish() } },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = -dp(12); bottomMargin = dp(2) })

        content.addView(TextView(this).apply {
            text = "Speed Overlay"; textSize = 34f; setTextColor(primary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD); setPadding(dp(2), 0, 0, dp(14))
        })

        content.addView(TextView(this).apply { text = "POSITION"; textSize = 13f; setTextColor(secondary); setPadding(dp(16), dp(2), 0, dp(7)) })
        content.addView(sideSelector(), LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(18) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        val r1 = controlRow("Size",       { adjustSize(-1) },                     { adjustSize(1) });  sizeValue = r1.second; card.addView(r1.first); card.addView(sep())
        val r2 = controlRow("Horizontal", { adjust("speed_h_offset", -1) },       { adjust("speed_h_offset", 1) });  hValue = r2.second; card.addView(r2.first); card.addView(sep())
        val r3 = controlRow("Vertical",   { adjust("speed_v_offset", -1) },       { adjust("speed_v_offset", 1) });  vValue = r3.second; card.addView(r3.first)
        addThicknessAndUnitRows(card)   // adds Thickness row + separator
        content.addView(card)

        // Unit toggle (Auto / KB/s / MB/s) in its own card below
        content.addView(TextView(this).apply { text = "DISPLAY UNIT"; textSize = 13f; setTextColor(secondary); setPadding(dp(16), dp(16), 0, dp(7)) })
        val unitCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        unitCard.addView(unitToggleRow())
        content.addView(unitCard)

        content.addView(Button(this).apply {
            text = "Reset to Default"; textSize = 16f; isAllCaps = false
            stateListAnimator = null; elevation = 0f; translationZ = 0f
            setTextColor(primary)
            background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(14).toFloat() }
            setOnClickListener {
                prefs.edit().putString("speed_side", "right")
                    .putInt("speed_scale_pct", 100)
                    .putInt("speed_h_offset", 0)
                    .putInt("speed_v_offset", 100)
                    .putInt("speed_thickness_pct", 100)
                    .putString("speed_unit_mode", "auto")
                    .apply()
                refresh(); applyNow()
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(20) }
        })

        scroll.addView(content); GeistTypography.apply(scroll)
        setContentView(scroll); refresh()
    }

    private fun sideSelector(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(44,44,46) else Color.rgb(229,229,234)); cornerRadius = dp(12).toFloat()
            }
        }
        fun seg(label: String, side: String) = TextView(this).apply {
            text = label; textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setOnClickListener {
                if (prefs.getString("speed_side", "right") != side) {
                    prefs.edit().putString("speed_side", side).apply(); refresh(); applyNow()
                }
            }
        }
        leftBtn = seg("Left", "left"); rightBtn = seg("Right", "right")
        outer.addView(leftBtn, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(rightBtn, LinearLayout.LayoutParams(0, -1, 1f))
        return outer
    }

    private fun updateSelector() {
        val side = prefs.getString("speed_side", "right") ?: "right"
        fun style(v: TextView?, selected: Boolean) {
            v ?: return
            v.setTextColor(if (selected) primary else secondary)
            v.background = if (selected) GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(9).toFloat() } else null
        }
        style(leftBtn, side == "left"); style(rightBtn, side != "left")
    }

    private fun controlRow(title: String, minus: () -> Unit, plus: () -> Unit): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8)); minimumHeight = dp(62)
        }
        row.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(primary) }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(roundControl("−", minus), LinearLayout.LayoutParams(dp(38), dp(38)))
        val value = TextView(this).apply {
            textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER; text = "0"
            setOnClickListener {
                when (title) {
                    "Size"       -> prefs.edit().putInt("speed_scale_pct", 100).apply()
                    "Horizontal" -> prefs.edit().putInt("speed_h_offset", 0).apply()
                    "Vertical"   -> prefs.edit().putInt("speed_v_offset", 100).apply()
                    "Thickness"  -> prefs.edit().putInt("speed_thickness_pct", 100).apply()
                }
                refresh(); applyNow()
            }
        }
        row.addView(value, LinearLayout.LayoutParams(dp(72), dp(42)))
        row.addView(roundControl("+", plus), LinearLayout.LayoutParams(dp(38), dp(38)))
        return Pair(row, value)
    }

    private fun roundControl(symbol: String, action: () -> Unit): TextView {
        val handler = Handler(Looper.getMainLooper())
        var repeated = false
        lateinit var repeater: Runnable
        repeater = Runnable { repeated = true; action(); handler.postDelayed(repeater, 80) }
        return TextView(this).apply {
            text = symbol; textSize = 21f; gravity = Gravity.CENTER; setTextColor(primary)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(controlFill) }
            isClickable = true
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN   -> { repeated = false; v.isPressed = true; handler.postDelayed(repeater, 420); true }
                    MotionEvent.ACTION_UP     -> { handler.removeCallbacks(repeater); v.isPressed = false; if (!repeated) action(); v.performClick(); true }
                    MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeater); v.isPressed = false; true }
                    else -> true
                }
            }
            setOnClickListener { }
        }
    }

    private var thickValue: TextView? = null

    private fun addThicknessAndUnitRows(card: LinearLayout) {
        card.addView(sep())
        val r4 = controlRow("Thickness", { adjustThickness(-1) }, { adjustThickness(1) })
        thickValue = r4.second; card.addView(r4.first)
    }

    private fun unitToggleRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(14), dp(10)); minimumHeight = dp(54)
        }
        row.addView(TextView(this).apply { text = "Unit"; textSize = 16f; setTextColor(primary) }, LinearLayout.LayoutParams(0, -2, 1f))
        val modes = listOf("auto", "kbps", "mbps")
        val labels = listOf("Auto", "KB/s", "MB/s")
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply { setColor(if (dark) Color.rgb(44,44,46) else Color.rgb(229,229,234)); cornerRadius = dp(12).toFloat() }
        }
        val buttons = labels.mapIndexed { i, label ->
            TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    prefs.edit().putString("speed_unit_mode", modes[i]).apply()
                    applyNow()
                    refreshUnitBtns(this@apply, modes, i, outer)
                }
            }
        }
        buttons.forEach { outer.addView(it, LinearLayout.LayoutParams(-2, dp(32))) }
        row.addView(outer)
        refreshUnitBtns(null, modes, -1, outer)
        return row
    }

    private fun refreshUnitBtns(clicked: TextView?, modes: List<String>, clickedIdx: Int, outer: LinearLayout) {
        val cur = if (clickedIdx >= 0) modes[clickedIdx] else prefs.getString("speed_unit_mode", "auto") ?: "auto"
        val idx = modes.indexOf(cur).coerceAtLeast(0)
        for (i in 0 until outer.childCount) {
            val btn = outer.getChildAt(i) as TextView
            val selected = i == idx
            btn.setTextColor(if (selected) primary else secondary)
            btn.background = if (selected) GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(9).toFloat() } else null
        }
    }

    private fun adjustSize(delta: Int) {
        val n = (prefs.getInt("speed_scale_pct", 100) + delta).coerceIn(50, 250)
        prefs.edit().putInt("speed_scale_pct", n).apply(); refresh(); applyNow()
    }
    private fun adjustThickness(delta: Int) {
        val n = (prefs.getInt("speed_thickness_pct", 100) + delta).coerceIn(50, 200)
        prefs.edit().putInt("speed_thickness_pct", n).apply(); refresh(); applyNow()
    }
    private fun adjust(key: String, delta: Int) {
        val n = (prefs.getInt(key, if (key == "speed_v_offset") 100 else 0) + delta).coerceIn(-10000, 10000)
        prefs.edit().putInt(key, n).apply(); refresh(); applyNow()
    }
    private fun refresh() {
        sizeValue?.text = "${prefs.getInt("speed_scale_pct", 100)}%"
        hValue?.text = signed(prefs.getInt("speed_h_offset", 0))
        vValue?.text = signed(prefs.getInt("speed_v_offset", 100))
        thickValue?.text = "${prefs.getInt("speed_thickness_pct", 100)}%"
        updateSelector()
    }
    private fun signed(v: Int) = if (v > 0) "+$v" else "$v"
    private fun applyNow() {
        if (prefs.getBoolean("speed_enabled", false) && Settings.canDrawOverlays(this)) {
            stopService(Intent(this, SpeedService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, SpeedService::class.java))
        }
    }
    private fun sep() = View(this).apply {
        setBackgroundColor(separator)
        layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16) }
    }
    private fun dp(v: Int) = (v * d + 0.5f).toInt()
}
