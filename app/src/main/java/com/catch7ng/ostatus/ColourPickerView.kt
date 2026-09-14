package com.catch7ng.ostatus

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * A self-contained HSV colour picker rendered entirely on Canvas.
 *
 * Layout (square view):
 *   ┌──────────────────────────────┐
 *   │        Hue ring (outer)      │
 *   │   ┌──────────────────────┐   │
 *   │   │  SV square (inner)   │   │
 *   │   └──────────────────────┘   │
 *   └──────────────────────────────┘
 *
 * The hue ring lets you choose hue (0–360°).
 * The inner square selects Saturation (X axis) and Value/brightness (Y axis).
 *
 * Usage:
 *   val picker = ColourPickerView(context)
 *   picker.setColour(Color.RED)          // initialise
 *   picker.onColourChanged = { argb -> … }
 */
class ColourPickerView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Called whenever the user picks a new colour. ARGB int. */
    var onColourChanged: ((Int) -> Unit)? = null

    /** Set the currently selected colour programmatically. */
    fun setColour(argb: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        buildHueShader()
        invalidate()
    }

    /** Returns the currently selected colour as ARGB. */
    fun getColour(): Int = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    // ─── Internal State ──────────────────────────────────────────────────────

    private var hue = 0f          // 0..360
    private var saturation = 1f   // 0..1
    private var value = 1f        // 0..1  (brightness)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Geometry (computed in onSizeChanged)
    private var cx = 0f
    private var cy = 0f
    private var outerR = 0f      // outer radius of hue ring
    private var innerR = 0f      // inner radius of hue ring = outer edge of SV square circle
    private var ringThick = 0f   // outerR - innerR
    private var sqHalf = 0f      // half-side of the inner SV square

    // Shaders rebuilt whenever geometry or hue changes
    private var hueSweepShader: SweepGradient? = null
    private var satShader: LinearGradient? = null
    private var valShader: LinearGradient? = null

    // Which region is being dragged
    private enum class region { NONE, HUE_RING, SV_SQUARE }
    private var dragging = region.NONE

    // ─── Geometry ────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        cx = w / 2f
        cy = h / 2f
        outerR = size / 2f * 0.96f
        ringThick = outerR * 0.18f
        innerR = outerR - ringThick
        // Largest square that fits inside the inner circle
        sqHalf = innerR / sqrt(2f) * 0.97f
        buildHueShader()
        buildSVShaders()
    }

    private fun buildHueShader() {
        if (outerR == 0f) return
        // 13-stop rainbow for smooth hue ring, closing back to red
        val colours = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF8000.toInt(), 0xFFFFFF00.toInt(),
            0xFF80FF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FF80.toInt(),
            0xFF00FFFF.toInt(), 0xFF0080FF.toInt(), 0xFF0000FF.toInt(),
            0xFF8000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0080.toInt(),
            0xFFFF0000.toInt()
        )
        hueSweepShader = SweepGradient(cx, cy, colours, null)
    }

    private fun buildSVShaders() {
        if (sqHalf == 0f) return
        val left = cx - sqHalf; val right = cx + sqHalf
        val top  = cy - sqHalf; val bottom = cy + sqHalf
        // Horizontal: white → pure hue
        val pureHue = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        satShader = LinearGradient(left, top, right, top,
            Color.WHITE, pureHue, Shader.TileMode.CLAMP)
        // Vertical: transparent → black (multiplied on top of sat)
        valShader = LinearGradient(left, top, left, bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
    }

    // ─── Drawing ─────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (outerR == 0f) return
        drawHueRing(canvas)
        drawSVSquare(canvas)
        drawHueThumb(canvas)
        drawSVThumb(canvas)
    }

    private fun drawHueRing(canvas: Canvas) {
        // Outer filled circle (hue colours) minus inner circle (cut out)
        paint.shader = hueSweepShader
        paint.style = Paint.Style.FILL
        // Rotate shader so hue=0 (red) points right (3-o'clock) which is SweepGradient default
        val matrix = Matrix().apply { setRotate(-90f, cx, cy) }
        hueSweepShader?.setLocalMatrix(matrix)

        paint.shader = hueSweepShader
        canvas.drawCircle(cx, cy, outerR, paint)

        // Cut inner hole
        paint.shader = null
        paint.color = backgroundColor()
        canvas.drawCircle(cx, cy, innerR - 1f, paint)
    }

    private fun backgroundColor(): Int {
        // Match the dialog card background. We use the same dark/light detection.
        val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val dark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (dark) Color.rgb(28, 28, 30) else Color.WHITE
    }

    private fun drawSVSquare(canvas: Canvas) {
        val left = cx - sqHalf; val right = cx + sqHalf
        val top  = cy - sqHalf; val bottom = cy + sqHalf
        buildSVShaders()

        // Layer 1: saturation (white → hue colour)
        paint.style = Paint.Style.FILL
        paint.shader = satShader
        canvas.drawRect(left, top, right, bottom, paint)

        // Layer 2: value/brightness (transparent → black, multiply)
        paint.shader = valShader
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    private fun drawHueThumb(canvas: Canvas) {
        val rad = Math.toRadians((hue - 90.0))
        val r = innerR + ringThick / 2f
        val tx = cx + cos(rad).toFloat() * r
        val ty = cy + sin(rad).toFloat() * r
        val thumbR = ringThick * 0.42f

        paint.style = Paint.Style.FILL
        paint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(tx, ty, thumbR, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        canvas.drawCircle(tx, ty, thumbR, paint)
    }

    private fun drawSVThumb(canvas: Canvas) {
        val tx = cx - sqHalf + saturation * sqHalf * 2f
        val ty = cy + sqHalf - value * sqHalf * 2f
        val thumbR = sqHalf * 0.08f

        paint.style = Paint.Style.FILL
        paint.color = getColour()
        canvas.drawCircle(tx, ty, thumbR, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.WHITE
        canvas.drawCircle(tx, ty, thumbR, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x55000000
        canvas.drawCircle(tx, ty, thumbR + 2.5f, paint)
    }

    // ─── Touch ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ex = event.x; val ey = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = hitTest(ex, ey)
                if (dragging == region.NONE) return false
                handleDrag(ex, ey)
            }
            MotionEvent.ACTION_MOVE -> handleDrag(ex, ey)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = region.NONE
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): region {
        val dx = x - cx; val dy = y - cy
        val dist = sqrt(dx * dx + dy * dy)
        return when {
            dist in (innerR - ringThick * 0.3f)..(outerR + ringThick * 0.3f) -> region.HUE_RING
            abs(x - cx) <= sqHalf * 1.1f && abs(y - cy) <= sqHalf * 1.1f -> region.SV_SQUARE
            else -> region.NONE
        }
    }

    private fun handleDrag(x: Float, y: Float) {
        when (dragging) {
            region.HUE_RING -> {
                val angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                hue = ((angle + 90f) % 360f + 360f) % 360f
                buildSVShaders()
                invalidate()
                onColourChanged?.invoke(getColour())
            }
            region.SV_SQUARE -> {
                saturation = ((x - (cx - sqHalf)) / (sqHalf * 2f)).coerceIn(0f, 1f)
                value       = (1f - (y - (cy - sqHalf)) / (sqHalf * 2f)).coerceIn(0f, 1f)
                invalidate()
                onColourChanged?.invoke(getColour())
            }
            region.NONE -> {}
        }
    }
}
