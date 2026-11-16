package com.kiminai.pet

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingPetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var container: FrameLayout
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams
    private val prefs by lazy { getSharedPreferences("pet_config", Context.MODE_PRIVATE) }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyConfig()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, IntentFilter("com.kiminai.pet.UPDATE_CONFIG"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(configReceiver, IntentFilter("com.kiminai.pet.UPDATE_CONFIG"))
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        container = FrameLayout(this)

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("file:///android_asset/pet.html")
        }
        container.addView(webView)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            360, 420,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 400
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isMoved = false

        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isMoved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        webView.evaluateJavascript("window.onPetPat && onPetPat()", null)
                    } else if (prefs.getBoolean("snap_edge", true)) {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        applyConfig()
    }

    private fun applyConfig() {
        val scale = prefs.getInt("scale", 100) / 100f
        val alpha = prefs.getInt("alpha", 100) / 100f
        val activityLevel = prefs.getInt("activity_level", 5)
        val showBubble = prefs.getBoolean("show_bubble", true)
        val clickThrough = prefs.getBoolean("click_through", false)

        params.width = (360 * scale).toInt()
        params.height = (420 * scale).toInt()
        params.alpha = alpha

        params.flags = if (clickThrough) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        windowManager.updateViewLayout(container, params)
        webView.evaluateJavascript(
            "window.updatePetSettings && updatePetSettings({activity:$activityLevel, bubble:$showBubble})",
            null
        )
    }

    private fun snapToNearestEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        params.x = if (params.x + params.width / 2 < screenWidth / 2) 0 else screenWidth - params.width
        windowManager.updateViewLayout(container, params)
    }

    private fun startForegroundNotification() {
        val channelId = "kiminai_pet_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "基米奈桌宠运行中", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("基米奈正在屏幕上陪着主人喵~")
            .setContentText("打开应用可随时调节体型、透明度和穿透模式")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(configReceiver)
        if (::container.isInitialized) windowManager.removeView(container)
    }
}
