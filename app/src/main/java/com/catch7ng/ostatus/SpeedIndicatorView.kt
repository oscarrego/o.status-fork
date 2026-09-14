package com.catch7ng.ostatus

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.abs

/**
 * SpeedIndicatorView — ultra-minimal floating overlay showing download speed.
 * Matches reference image style: plain "10.6KB/s" text with near-invisible pill background.
 *
 * Unit mode (pref: speed_unit_mode):
 *   "auto"  → auto-scales: KB/s below 1 MB/s, MB/s above (default)
 *   "kbps"  → always show KB/s
 *   "mbps"  → always show MB/s
 *
 * Thickness (pref: speed_thickness_pct 50–200, default 100):
 *   Multiplies the base text size (and thus pill height auto-sizes).
 *
 * Polling every 2 seconds, dirty-flag, pauses when screen off (managed by SpeedService).
 */
class SpeedIndicatorView(
    ctx: Context,
    private var colorMode: String,
    private val unitMode: String = "auto",
    private val thicknessPct: Int = 100
) : View(ctx) {

    fun updateColorMode(mode: String) { colorMode = mode; invalidate() }

    // ── Traffic state ─────────────────────────────────────────────────────────
    private var downloadKBs = 0f
    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var prevDown = -1f

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // ── Polling ───────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            val rxNow = TrafficStats.getTotalRxBytes()
            val down = if (rxNow >= lastRxBytes) (rxNow - lastRxBytes) / 2f / 1024f else 0f
            lastRxBytes = rxNow
            if (abs(down - prevDown) > 0.01f) {
                downloadKBs = down; prevDown = down
                invalidate()
            }
            handler.postDelayed(this, 2000)
        }
    }

    fun start() { lastRxBytes = TrafficStats.getTotalRxBytes(); handler.post(poll) }
    fun stop()  { handler.removeCallbacks(poll) }

    // ── Colour ────────────────────────────────────────────────────────────────
    private fun resolveActiveColor(): Int {
        if (colorMode.startsWith("#")) return try { Color.parseColor(colorMode) } catch (_: Exception) { Color.WHITE }
        val dark = isDark()
        return when (colorMode) { "black" -> Color.BLACK; "white" -> Color.WHITE; else -> if (!dark) Color.BLACK else Color.WHITE }
    }

    private fun isDark() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ── Speed formatting ──────────────────────────────────────────────────────
    private fun formatSpeed(): String {
        val dens = thicknessPct.coerceIn(50, 200) / 100f
        return when (unitMode) {
            "mbps" -> "${"%.2f".format(downloadKBs / 1024f)}MB/s"
            "kbps" -> "${"%.1f".format(downloadKBs)}KB/s"
            else   -> if (downloadKBs >= 1024f) "${"%.2f".format(downloadKBs / 1024f)}MB/s"
                      else "${"%.1f".format(downloadKBs)}KB/s"
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val dens = resources.displayMetrics.density
        val active = resolveActiveColor()
        val thick = thicknessPct.coerceIn(50, 200) / 100f

        // Minimal pill: very subtle semi-transparent background
        val bgAlpha = 140  // ~55% opacity — minimal, like the reference image
        bgPaint.color = if (isDark()) Color.argb(bgAlpha, 10, 10, 16)
                        else Color.argb(bgAlpha, 230, 230, 240)
        bgPaint.style = Paint.Style.FILL
        val r = h / 2f
        canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)

        // Hairline glass border (very subtle)
        bgPaint.color = Color.argb(30, 255, 255, 255)
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 0.8f * dens
        canvas.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, bgPaint)
        bgPaint.style = Paint.Style.FILL

        // Speed text centered — base 13sp scaled by thickness
        val textSp = 13f * thick
        txtPaint.textSize = textSp * dens
        txtPaint.color = active
        val fm = txtPaint.fontMetrics
        val textY = h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(formatSpeed(), w / 2f, textY, txtPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dens = resources.displayMetrics.density
        val thick = thicknessPct.coerceIn(50, 200) / 100f
        // Auto-size the view to fit the text
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f * thick * dens
        }
        val sampleText = "99.9MB/s"
        val textW = p.measureText(sampleText)
        val textH = p.fontMetrics.let { it.descent - it.ascent }
        val padH = 14f * dens
        val padV = 8f * dens
        setMeasuredDimension((textW + padH).toInt(), (textH + padV).toInt())
    }
}
