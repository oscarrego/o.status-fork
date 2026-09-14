package com.catch7ng.ostatus

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*

/**
 * SpeedService — lightweight foreground service for the internet speed overlay.
 * Shares the same notification channel as StatusBarService so there is only one
 * persistent notification visible to the user.
 */
class SpeedService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var v: SpeedIndicatorView
    private lateinit var lp: WindowManager.LayoutParams
    private var overlayAttached = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (!::v.isInitialized) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { v.stop(); v.visibility = View.INVISIBLE }
                Intent.ACTION_SCREEN_ON  -> { v.visibility = View.VISIBLE; v.start() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        val pref = getSharedPreferences("settings", MODE_PRIVATE)
        val mode = pref.getString("color_mode", "auto") ?: "auto"
        val resolvedMode = if (mode == "legacy") { if (pref.getBoolean("black5", false)) "black" else "white" } else mode

        // Shared notification channel (already created by StatusBarService, safe to re-create)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("duo5", "Duo overlay", NotificationManager.IMPORTANCE_LOW))
        startForeground(6,
            Notification.Builder(this, "duo5")
                .setContentTitle("O.status Speed")
                .setContentText("Speed indicator active")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        )

        val d = resources.displayMetrics.density
        val scale = (pref.getInt("speed_scale_pct", 100) / 100f)
        val isLeft = pref.getString("speed_side", "right") == "left"
        val hOff = pref.getInt("speed_h_offset", 0)
        val vOff = pref.getInt("speed_v_offset", 100) // default lower than Duo

        val unitMode = pref.getString("speed_unit_mode", "auto") ?: "auto"
        val thicknessPct = pref.getInt("speed_thickness_pct", 100)

        v = SpeedIndicatorView(this, resolvedMode, unitMode, thicknessPct)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Use WRAP_CONTENT so SpeedIndicatorView.onMeasure() decides exact pill size
        val pillW = WindowManager.LayoutParams.WRAP_CONTENT
        val pillH = WindowManager.LayoutParams.WRAP_CONTENT

        lp = WindowManager.LayoutParams(
            pillW, pillH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (isLeft) Gravity.START else Gravity.END
            x = ((7 + hOff) * d).toInt()
            y = ((vOff) * d).toInt()
            if (Build.VERSION.SDK_INT >= 28)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try { wm.addView(v, lp); overlayAttached = true } catch (_: Exception) {}

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)

        v.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::v.isInitialized && ::wm.isInitialized && ::lp.isInitialized && !overlayAttached) {
            try { wm.addView(v, lp); overlayAttached = true } catch (_: Exception) {}
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        if (::v.isInitialized) v.stop()
        if (::wm.isInitialized && ::v.isInitialized) try { wm.removeView(v); overlayAttached = false } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
