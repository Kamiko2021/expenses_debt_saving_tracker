package com.example.expenses_debt_saving_tracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.expenses_debt_saving_tracker.R
import com.example.expenses_debt_saving_tracker.ui.QuickAddActivity
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isHiddenByAction = false

    companion object {
        const val ACTION_HIDE = "com.example.expenses_debt_saving_tracker.HIDE_BUBBLE"
        const val ACTION_SHOW = "com.example.expenses_debt_saving_tracker.SHOW_BUBBLE"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_widget_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(isHidden = false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_HIDE -> {
                isHiddenByAction = true
                floatingView?.visibility = View.GONE
                updateNotification(isHidden = true)
            }
            ACTION_SHOW -> {
                isHiddenByAction = false
                if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    floatingView?.visibility = View.VISIBLE
                }
                updateNotification(isHidden = false)
            }
            else -> {
                if (floatingView == null) {
                    setupFloatingView()
                }
            }
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isHiddenByAction) return

        // Auto-hide floating button in Landscape mode (games / videos)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            floatingView?.visibility = View.GONE
        } else {
            floatingView?.visibility = View.VISIBLE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Add Floating Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isHidden: Boolean): Notification {
        val actionIntent = Intent(
            this,
            FloatingWidgetService::class.java
        ).apply {
            action = if (isHidden) ACTION_SHOW else ACTION_HIDE
        }

        val actionPendingIntent = PendingIntent.getService(
            this,
            0,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionTitle = if (isHidden) "Show Floating Bubble" else "Hide Bubble (Game Mode)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick Add Shortcut Active")
            .setContentText(if (isHidden) "Floating bubble hidden" else "Tap + button anytime to add expenses")
            .setSmallIcon(R.drawable.icon_picture)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, actionTitle, actionPendingIntent)
            .build()
    }

    private fun updateNotification(isHidden: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isHidden))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val button = ImageView(this).apply {
            setImageResource(R.drawable.ic_money_widget)
            setPadding(28, 28, 28, 28)
            setBackgroundResource(R.drawable.widget_circle_btn)
            elevation = 16f
            alpha = 0.6f // Semi-transparent when idle
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            140, // ~56dp in px
            140,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    button.alpha = 1.0f // Full opacity on touch
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(button, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.alpha = 0.6f // Return to subtle opacity
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        // Click detected! Launch QuickAddActivity
                        val intent = Intent(this, QuickAddActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = button

        // Hide initially if launched in Landscape orientation
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            floatingView?.visibility = View.GONE
        }

        try {
            windowManager?.addView(floatingView, layoutParams)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (_: Exception) {}
        }
    }
}
