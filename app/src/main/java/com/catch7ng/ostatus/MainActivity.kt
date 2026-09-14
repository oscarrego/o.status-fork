package com.catch7ng.ostatus

import android.Manifest
import android.app.Dialog
import android.app.NotificationManager
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val d by lazy { resources.displayMetrics.density }
    private var overlayValue: TextView? = null
    private var phoneValue: TextView? = null
    private var dndValue: TextView? = null
    private var colorValue: TextView? = null

    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor get() = if (dark) Color.BLACK else Color.rgb(242,242,247)
    private val cardColor get() = if (dark) Color.rgb(28,28,30) else Color.WHITE
    private val primary get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142,142,147) else Color.rgb(99,99,102)
    private val separator get() = if (dark) Color.rgb(56,56,58) else Color.rgb(229,229,234)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    override fun onResume() { super.onResume(); refreshPermissionStates() }

    private fun buildUi() {
        val scroll = object : ScrollView(this) {
            private fun hasScrollableContent(): Boolean =
                canScrollVertically(-1) || canScrollVertically(1)

            override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (!hasScrollableContent()) return false
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (!hasScrollableContent()) return false
                return super.onTouchEvent(ev)
            }
        }.apply {
            setBackgroundColor(pageColor)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(46), dp(20), dp(28))
        }

        content.addView(TextView(this).apply {
            text = "O.status"
            textSize = 34f
            setTextColor(primary)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(2), 0, 0, dp(4))
        })
        sectionTitle(content, "O STATUS BAR")
        val mainCard = card()
        val onRow = row("O Status Bar")
        val enabledSwitch = OStatusSwitch(this).apply {
            isOn = prefs.getBoolean("duo_enabled", true)
            onToggleRequested = { requested ->
                if (requested && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    false
                } else {
                    prefs.edit().putBoolean("duo_enabled", requested).apply()
                    BootPrefs.setEnabled(this@MainActivity, requested)
                    if (requested) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, StatusBarService::class.java))
                    else stopService(Intent(this@MainActivity, StatusBarService::class.java))
                    true
                }
            }
        }
        onRow.addView(enabledSwitch, LinearLayout.LayoutParams(dp(51), dp(31)))
        onRow.setOnClickListener { enabledSwitch.requestToggle() }
        mainCard.addView(onRow)
        content.addView(mainCard)

        sectionTitle(content, "APPEARANCE")
        val appearance = card()

        // Indicator Colour
        val colorRow = row("Indicator Colour")
        colorValue = colourChoiceLabel(colourLabel(prefs.getString("color_mode", "auto") ?: "auto"))
        colorRow.addView(colorValue)
        colorRow.setOnClickListener { showColourPickerDialog() }
        appearance.addView(colorRow)
        appearance.addView(separatorView())

        // Battery Percentage toggle
        val batteryPercentageRow = row("Battery Percentage")
        val batteryPercentageSwitch = OStatusSwitch(this).apply {
            isOn = prefs.getBoolean("battery_percentage", false)
            onToggleRequested = { requested ->
                prefs.edit().putBoolean("battery_percentage", requested).apply()
                BootPrefs.setBatteryPercentage(this@MainActivity, requested)
                restartDuoIfNeeded()
                true
            }
        }
        batteryPercentageRow.addView(batteryPercentageSwitch, LinearLayout.LayoutParams(dp(51), dp(31)))
        batteryPercentageRow.setOnClickListener { batteryPercentageSwitch.requestToggle() }
        appearance.addView(batteryPercentageRow)
        appearance.addView(separatorView())

        // Battery Number Size (sub-row, only visible when battery % is on)
        val batNumLabel = valueLabel("${prefs.getInt("battery_num_scale_pct", 100)}%").apply {
            gravity = Gravity.CENTER; minWidth = dp(52)
        }
        val batNumRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(6), dp(12), dp(6)); minimumHeight = dp(46)
            visibility = if (prefs.getBoolean("battery_percentage", false)) View.VISIBLE else View.GONE
        }
        val batMinus = glassStepBtn("−") {
            val n = (prefs.getInt("battery_num_scale_pct", 100) - 5).coerceIn(50, 200)
            prefs.edit().putInt("battery_num_scale_pct", n).apply(); batNumLabel.text = "$n%"; restartDuoIfNeeded()
        }
        val batPlus = glassStepBtn("+") {
            val n = (prefs.getInt("battery_num_scale_pct", 100) + 5).coerceIn(50, 200)
            prefs.edit().putInt("battery_num_scale_pct", n).apply(); batNumLabel.text = "$n%"; restartDuoIfNeeded()
        }
        batNumRow.addView(TextView(this).apply { text = "Number Size"; textSize = 15f; setTextColor(secondary) }, LinearLayout.LayoutParams(0, -2, 1f))
        batNumRow.addView(batMinus, LinearLayout.LayoutParams(dp(34), dp(34)))
        batNumRow.addView(batNumLabel, LinearLayout.LayoutParams(dp(56), -2))
        batNumRow.addView(batPlus, LinearLayout.LayoutParams(dp(34), dp(34)))
        // Wire battery switch to show/hide this row
        batteryPercentageSwitch.onToggleRequested = { requested ->
            prefs.edit().putBoolean("battery_percentage", requested).apply()
            BootPrefs.setBatteryPercentage(this@MainActivity, requested)
            batNumRow.visibility = if (requested) View.VISIBLE else View.GONE
            restartDuoIfNeeded()
            true
        }
        appearance.addView(batNumRow)
        appearance.addView(separatorView())

        // Position & Size
        val positionRow = row("Position & Size")
        positionRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        positionRow.setOnClickListener { startActivity(Intent(this, PositionSizeActivity::class.java)) }
        appearance.addView(positionRow)
        content.addView(appearance)

        // ── Speed Indicator section ──────────────────────────────────────────
        sectionTitle(content, "SPEED INDICATOR")
        val speedCard = card()

        val speedRow = row("Internet Speed Overlay")
        val speedSwitch = OStatusSwitch(this).apply {
            isOn = prefs.getBoolean("speed_enabled", false)
            onToggleRequested = { requested ->
                if (requested && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    false
                } else {
                    prefs.edit().putBoolean("speed_enabled", requested).apply()
                    if (requested) ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, SpeedService::class.java))
                    else stopService(Intent(this@MainActivity, SpeedService::class.java))
                    true
                }
            }
        }
        speedRow.addView(speedSwitch, LinearLayout.LayoutParams(dp(51), dp(31)))
        speedRow.setOnClickListener { speedSwitch.requestToggle() }
        speedCard.addView(speedRow)
        speedCard.addView(separatorView())

        val speedPosRow = row("Speed Position & Size")
        speedPosRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        speedPosRow.setOnClickListener { startActivity(Intent(this, SpeedPositionActivity::class.java)) }
        speedCard.addView(speedPosRow)
        content.addView(speedCard)

        sectionTitle(content, "PERMISSIONS")
        val permissions = card()
        val overlayRow = permissionRow("Display Over Apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        overlayValue = overlayRow.second; permissions.addView(overlayRow.first)
        permissions.addView(separatorView())
        val phoneRow = permissionRow("Phone Status") { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 10) }
        phoneValue = phoneRow.second; permissions.addView(phoneRow.first)
        permissions.addView(separatorView())
        val dndRow = permissionRow("Do Not Disturb Access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        dndValue = dndRow.second; permissions.addView(dndRow.first)
        content.addView(permissions)

        sectionTitle(content, "OPTIONS")
        val options = card()
        val detailsRow = row("Details")
        detailsRow.addView(ForwardChevronView(this), LinearLayout.LayoutParams(dp(18), dp(32)))
        detailsRow.setOnClickListener { startActivity(Intent(this, DetailsActivity::class.java)) }
        options.addView(detailsRow)
        content.addView(options)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
        }
        fun footerText(value: String, clickable: Boolean = false) = TextView(this).apply {
            text = value
            textSize = 11.5f
            setTextColor(secondary)
            gravity = Gravity.CENTER
            if (clickable) {
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CATCHINGL")))
                }
            }
        }
        footer.addView(footerText("V${displayVersion()}"))
        footer.addView(footerText("  ·  "))
        footer.addView(footerText("GitHub", true))
        footer.addView(footerText("  ·  "))
        footer.addView(footerText("© 2026 CATCH7NG.L"))
        content.addView(footer)

        scroll.addView(content)
        scroll.viewTreeObserver.addOnGlobalLayoutListener {
            val child = scroll.getChildAt(0)
            val fits = child != null && child.height <= scroll.height
            scroll.setOnTouchListener(if (fits) View.OnTouchListener { _, _ -> true } else null)
        }
        GeistTypography.apply(scroll)
        setContentView(scroll)
        refreshPermissionStates()
    }

    private fun showColourPickerDialog() {
        val current = prefs.getString("color_mode", "auto") ?: "auto"

        // ── outer dialog container ──────────────────────────────────────────
        val dialogBg = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(18).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = dialogBg
        }

        // Title
        container.addView(TextView(this).apply {
            text = "Indicator Colour"
            textSize = 20f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(primary)
            setPadding(dp(4), 0, 0, dp(14))
        })

        // ── Preset radio group ──────────────────────────────────────────────
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        var selectedMode = current
        // Track whether the user is in custom-colour mode
        var customColour = if (current.startsWith("#")) current else "#FFFFFF"

        fun makeRadio(label: String, value: String): RadioButton = RadioButton(this).apply {
            text = label
            textSize = 16f
            setTextColor(primary)
            id = View.generateViewId()
            buttonTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#3399EE"))
            isChecked = (current == value)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnCheckedChangeListener { _, checked ->
                if (checked) selectedMode = value
            }
        }

        val rbAuto  = makeRadio("Auto",  "auto")
        val rbBlack = makeRadio("Black", "black")
        val rbWhite = makeRadio("White", "white")
        val rbCustom = makeRadio("Custom colour", "__custom__").apply {
            isChecked = current.startsWith("#")
            if (isChecked) selectedMode = "__custom__"
        }

        radioGroup.addView(rbAuto)
        radioGroup.addView(rbBlack)
        radioGroup.addView(rbWhite)
        radioGroup.addView(rbCustom)

        // Radio group mutual exclusion wiring
        listOf(rbAuto, rbBlack, rbWhite, rbCustom).forEach { rb ->
            rb.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedMode = rb.tag as? String ?: selectedMode
                    listOf(rbAuto, rbBlack, rbWhite, rbCustom)
                        .filter { it != rb }.forEach { it.isChecked = false }
                }
            }
        }
        rbAuto.tag  = "auto";  rbBlack.tag = "black"
        rbWhite.tag = "white"; rbCustom.tag = "__custom__"

        container.addView(radioGroup)

        // ── Separator ───────────────────────────────────────────────────────
        container.addView(View(this).apply {
            setBackgroundColor(separator)
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = dp(4); bottomMargin = dp(12)
            }
        })

        // ── Colour wheel (shown for custom) ─────────────────────────────────
        val previewSwatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(8) }
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(if (current.startsWith("#")) Color.parseColor(current)
                         else Color.WHITE)
            }
        }
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(TextView(this@MainActivity).apply {
                text = "Custom colour"
                textSize = 15f
                setTextColor(secondary)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(previewSwatch)
        }
        container.addView(swatchRow)

        val picker = ColourPickerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(260))
            val initColour = if (current.startsWith("#"))
                Color.parseColor(current)
            else Color.parseColor("#3399EE")
            setColour(initColour)
            onColourChanged = { argb ->
                customColour = String.format("#%06X", 0xFFFFFF and argb)
                (previewSwatch.background as? GradientDrawable)?.setColor(argb)
                // Auto-select custom radio when wheel is touched
                rbCustom.isChecked = true
                selectedMode = "__custom__"
            }
        }
        container.addView(picker)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }

        val dialog = Dialog(this, android.R.style.Theme_Material_Dialog_Alert)

        fun applyAndDismiss() {
            val toSave = when (selectedMode) {
                "__custom__" -> customColour
                else -> selectedMode
            }
            prefs.edit().putString("color_mode", toSave).apply()
            colorValue?.text = colourLabel(toSave)
            (colorValue?.background as? GradientDrawable)?.setColor(previewColorForMode(toSave))
            if (prefs.getBoolean("duo_enabled", true) && Settings.canDrawOverlays(this)) {
                stopService(Intent(this, StatusBarService::class.java))
                ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
            }
            dialog.dismiss()
        }

        btnRow.addView(TextView(this).apply {
            text = "CANCEL"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.parseColor("#3399EE"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(TextView(this).apply {
            text = "OK"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.parseColor("#3399EE"))
            setPadding(dp(16), dp(8), dp(8), dp(8))
            setOnClickListener { applyAndDismiss() }
        })
        container.addView(btnRow)

        // ── Show dialog ─────────────────────────────────────────────────────
        dialog.apply {
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }.show()
    }

    /** Short human-readable label for the colour pill. */
    private fun colourLabel(mode: String) = when (mode) {
        "black" -> "Black"
        "white" -> "White"
        "auto"  -> "Auto"
        else    -> if (mode.startsWith("#")) mode.uppercase() else "Auto"
    }

    /** Background tint for the colour pill button in the main settings row. */
    private fun previewColorForMode(mode: String): Int = when (mode) {
        "black" -> Color.rgb(44, 44, 46)
        "white" -> Color.rgb(58, 58, 60)
        else    -> if (mode.startsWith("#")) {
            try { Color.parseColor(mode) }
            catch (_: Exception) { if (dark) Color.rgb(58, 58, 60) else Color.rgb(44, 44, 46) }
        } else if (dark) Color.rgb(58, 58, 60) else Color.rgb(44, 44, 46)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun restartDuoIfNeeded() {
        if (prefs.getBoolean("duo_enabled", true) && Settings.canDrawOverlays(this)) {
            stopService(Intent(this, StatusBarService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, StatusBarService::class.java))
        }
    }

    private fun refreshPermissionStates() {
        overlayValue?.let { setPermission(it, Settings.canDrawOverlays(this)) }
        phoneValue?.let { setPermission(it, ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
        dndValue?.let { setPermission(it, getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) }
    }

    private fun setPermission(v: TextView, granted: Boolean) {
        v.text = if (granted) "Allowed" else "Required  ›"
        v.setTextColor(if (granted) secondary else primary)
    }

    /** Glass-style small round step button with hold-to-repeat (same UX as PositionSizeActivity). */
    private fun glassStepBtn(symbol: String, action: () -> Unit): TextView {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var repeated = false
        lateinit var repeater: Runnable
        repeater = Runnable { repeated = true; action(); handler.postDelayed(repeater, 80) }
        return TextView(this).apply {
            text = symbol; textSize = 18f; gravity = Gravity.CENTER; setTextColor(primary)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.rgb(58,58,60) else Color.rgb(210,210,214))
            }
            isClickable = true
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN   -> { repeated = false; v.isPressed = true; handler.postDelayed(repeater, 420); true }
                    android.view.MotionEvent.ACTION_UP     -> { handler.removeCallbacks(repeater); v.isPressed = false; if (!repeated) action(); v.performClick(); true }
                    android.view.MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeater); v.isPressed = false; true }
                    else -> true
                }
            }
            setOnClickListener { }
        }
    }

    private fun displayVersion(): String {
        val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.3.0"
        return raw
    }

    private fun colorName() = when (prefs.getString("color_mode", "auto")) { "black" -> "Black"; "white" -> "White"; else -> "Auto" }

    private fun sectionTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(secondary)
            setPadding(dp(16), dp(18), 0, dp(6))
        })
    }

    private fun card(radius: Float = 14f) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(cardColor); cornerRadius = dp(radius.toInt()).toFloat() }
        clipToOutline = true
    }

    private fun row(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(9), dp(14), dp(9)); minimumHeight = dp(54)
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 16f; setTextColor(primary); gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun permissionRow(title: String, click: () -> Unit): Pair<LinearLayout, TextView> {
        val r = row(title)
        val v = valueLabel("")
        r.addView(v); r.setOnClickListener { click() }
        return Pair(r, v)
    }

    private fun valueLabel(value: String) = TextView(this).apply {
        text = value; textSize = 15f; setTextColor(secondary); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, 0, 0)
    }

    private fun colourChoiceLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        minWidth = dp(64)
        minimumHeight = dp(32)
        val mode = prefs.getString("color_mode", "auto") ?: "auto"
        val pillBg = if (mode.startsWith("#")) {
            try { Color.parseColor(mode) } catch (_: Exception) { if (dark) Color.rgb(58,58,60) else Color.rgb(44,44,46) }
        } else {
            if (dark) Color.rgb(58,58,60) else Color.rgb(44,44,46)
        }
        val luminance = (0.299f * Color.red(pillBg) + 0.587f * Color.green(pillBg) + 0.114f * Color.blue(pillBg)) / 255f
        setTextColor(if (luminance > 0.5f) Color.BLACK else Color.WHITE)
        background = GradientDrawable().apply { setColor(pillBg); cornerRadius = dp(9).toFloat() }
    }

    private fun separatorView() = View(this).apply {
        setBackgroundColor(separator)
        layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(16) }
    }

    private fun dp(v: Int) = (v * d + 0.5f).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refreshPermissionStates()
    }

    private inner class OStatusSwitch(context: android.content.Context) : View(context) {
        var isOn: Boolean = false
            set(value) {
                field = value
                progress = if (value) 1f else 0f
                invalidate()
            }

        var onToggleRequested: ((Boolean) -> Boolean)? = null
        private var progress = 0f
        private var animator: ValueAnimator? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            isFocusable = true
            contentDescription = "O Status Bar"
            setOnClickListener { requestToggle() }
        }

        fun requestToggle() {
            val requested = !isOn
            val accepted = onToggleRequested?.invoke(requested) ?: true
            if (accepted) setOnAnimated(requested)
        }

        private fun setOnAnimated(value: Boolean) {
            if (isOn == value) return
            val start = progress
            isOn = value
            progress = start
            val target = if (value) 1f else 0f
            animator?.cancel()
            animator = ValueAnimator.ofFloat(start, target).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val offTrack = if (dark) Color.rgb(58,58,60) else Color.rgb(229,229,234)
            val onTrack  = if (dark) Color.WHITE else Color.BLACK
            paint.color = blend(offTrack, onTrack, progress)
            canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)

            val pad = dp(2).toFloat()
            val radius = (h - pad * 2f) / 2f
            val leftCx  = pad + radius
            val rightCx = w - pad - radius
            val cx = leftCx + (rightCx - leftCx) * progress

            paint.color = if (dark && progress > .5f) Color.BLACK else Color.WHITE
            paint.setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0x33000000)
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
            canvas.drawCircle(cx, h / 2f, radius, paint)
            paint.clearShadowLayer()
        }

        private fun blend(a: Int, b: Int, t: Float): Int {
            val clamped = t.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(a)   + (Color.red(b)   - Color.red(a))   * clamped).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * clamped).toInt(),
                (Color.blue(a)  + (Color.blue(b)  - Color.blue(a))  * clamped).toInt()
            )
        }
    }
}
