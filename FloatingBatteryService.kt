package com.example.battery_float

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns the floating battery bubble.
 *
 * Power and memory design:
 *  - The bubble is one software-drawn view; no GPU context, no timers, no polling.
 *  - Battery updates are event driven and the receiver is only registered while the screen is on.
 *  - The view is redrawn only when the percentage or charging state really changes.
 *  - The ongoing notification is static (never updated), so no recurring work for the system.
 */
class FloatingBatteryService : Service() {

    companion object {
        const val ACTION_START = "com.example.battery_float.action.START"
        const val ACTION_STOP = "com.example.battery_float.action.STOP"

        private const val CHANNEL_ID = "bubble_service"
        private const val NOTIFICATION_ID = 1
        private const val LONG_PRESS_MS = 800L
        private const val SNAP_DURATION_MS = 180L

        /** True while the bubble is on screen. Read by the app screen. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Called on the main thread whenever [isRunning] changes. Set by the visible app screen. */
        @Volatile
        var onStateChanged: ((Boolean) -> Unit)? = null

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** Starts (or re-confirms) the bubble. Returns false if Android refused to start it. */
        fun start(context: Context): Boolean {
            val intent = Intent(context, FloatingBatteryService::class.java).setAction(ACTION_START)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: RuntimeException) {
                false
            }
        }

        /** Stops the bubble. Safe to call when it is not running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBatteryService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var settings: AppSettings
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: BatteryGaugeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private var batteryReceiverRegistered = false
    private var screenReceiverRegistered = false
    private var settingsListenerRegistered = false

    // Gesture state
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragArea: Rect? = null
    private var dragging = false
    private var multiTouch = false
    private var longPressFired = false
    private var interruptedGlide = false
    private var pinchSize = 0f

    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val scaleDetector: ScaleGestureDetector by lazy {
        ScaleGestureDetector(this, ScaleListener()).apply { isQuickScaleEnabled = false }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showBattery(intent)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startBatteryUpdates()
                Intent.ACTION_SCREEN_OFF -> stopBatteryUpdates()
            }
        }
    }

    private val longPressRunnable = Runnable {
        longPressFired = true
        bubble?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        closeByUser()
    }

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onSettingChanged(key) }

    // ---------------------------------------------------------------------------------------
    // Service lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settings = AppSettings(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.bubbleWanted = false
            stopSelf()
            return START_NOT_STICKY
        }

        // After startForegroundService() Android requires startForeground() within seconds, so it
        // always comes first, before anything that could fail.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // A null intent means Android restarted us after killing the process: honour the last choice.
        val shouldRun = intent != null || settings.bubbleWanted
        if (!shouldRun || !canDrawOverlays(this)) {
            settings.bubbleWanted = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (bubble == null && !showBubble()) {
            settings.bubbleWanted = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation or display-size change: place the bubble again from its saved fraction.
        applyGeometry()
    }

    override fun onDestroy() {
        if (settingsListenerRegistered) {
            settings.unregister(settingsListener)
            settingsListenerRegistered = false
        }
        mainHandler.removeCallbacksAndMessages(null)
        snapAnimator?.cancel()
        snapAnimator = null
        stopBatteryUpdates()
        if (screenReceiverRegistered) {
            unregisterQuietly(screenReceiver)
            screenReceiverRegistered = false
        }
        removeBubble()
        if (isRunning) {
            isRunning = false
            onStateChanged?.invoke(false)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------
    // Bubble window
    // ---------------------------------------------------------------------------------------

    private fun showBubble(): Boolean {
        val view = BatteryGaugeView(this).apply { showDisc = true }
        val size = sizePx()
        val area = usableBounds()
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // LEFT, not START: x must always be measured from the physical left edge, also in RTL languages.
            gravity = Gravity.TOP or Gravity.LEFT
            alpha = settings.opacityPercent / 100f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            x = Geometry.fractionToPosition(settings.positionX, area.left, area.right - size)
            y = Geometry.fractionToPosition(settings.positionY, area.top, area.bottom - size)
        }
        view.setOnTouchListener { _, event -> handleTouch(event) }

        try {
            windowManager.addView(view, params)
        } catch (e: RuntimeException) {
            // Permission revoked, or the system refused the window.
            return false
        }
        bubble = view
        bubbleParams = params

        settings.register(settingsListener)
        settingsListenerRegistered = true
        registerScreenReceiver()
        if (isInteractive()) {
            startBatteryUpdates()
        } else {
            // Screen is off: show one reading now, live updates start when the screen turns on.
            showBattery(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        }

        isRunning = true
        onStateChanged?.invoke(true)
        return true
    }

    private fun removeBubble() {
        bubble?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: RuntimeException) {
                // Already gone.
            }
        }
        bubble = null
        bubbleParams = null
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun sizePx(): Int = dp(settings.sizeDp)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    /** The part of the screen the bubble may use: everything except system bars and cut-outs. */
    @Suppress("DEPRECATION")
    private fun usableBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom
            )
        }
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (id > 0) resources.getDimensionPixelSize(id) else 0
        return Rect(0, statusBar, metrics.widthPixels, metrics.heightPixels)
    }

    private fun updateLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: RuntimeException) {
            // The view was already removed.
        }
    }

    /** Re-applies size and position (from the saved fractions), e.g. after rotation or a new size. */
    private fun applyGeometry(size: Int = sizePx()) {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val area = usableBounds()
        params.width = size
        params.height = size
        params.x = Geometry.fractionToPosition(settings.positionX, area.left, area.right - size)
        params.y = Geometry.fractionToPosition(settings.positionY, area.top, area.bottom - size)
        updateLayout(view, params)
    }

    private fun applyOpacity() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        params.alpha = settings.opacityPercent / 100f
        updateLayout(view, params)
    }

    private fun onSettingChanged(key: String?) {
        when (key) {
            AppSettings.KEY_SIZE -> applyGeometry()
            AppSettings.KEY_OPACITY -> applyOpacity()
            AppSettings.KEY_SNAP -> if (settings.snapToEdge) settle()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Touch: drag, tap, long press, pinch
    // ---------------------------------------------------------------------------------------

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = bubbleParams ?: return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val gliding = snapAnimator?.isRunning == true
                snapAnimator?.cancel()
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragArea = usableBounds()
                dragging = false
                multiTouch = false
                longPressFired = false
                interruptedGlide = gliding
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    // Keep where the finger left the bubble so the pinch starts from there.
                    dragging = false
                    saveCurrentPosition()
                }
                multiTouch = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (multiTouch) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging) {
                    if (hypot(dx, dy) < touchSlop) return true
                    dragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                }
                moveTo(startX + dx.roundToInt(), startY + dy.roundToInt())
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                when {
                    dragging || multiTouch || interruptedGlide -> settle()
                    !longPressFired -> openApp()
                }
                resetGesture()
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (dragging || multiTouch || interruptedGlide) settle()
                resetGesture()
            }
        }
        return true
    }

    private fun resetGesture() {
        dragging = false
        multiTouch = false
        interruptedGlide = false
        dragArea = null
    }

    private fun moveTo(x: Int, y: Int) {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val area = dragArea ?: usableBounds()
        params.x = Geometry.clamp(x, area.left, area.right - params.width)
        params.y = Geometry.clamp(y, area.top, area.bottom - params.height)
        updateLayout(view, params)
    }

    /** Saves the position without moving anything. */
    private fun saveCurrentPosition() {
        val params = bubbleParams ?: return
        savePosition(params.x, params.y, params.width, usableBounds())
    }

    private fun savePosition(x: Int, y: Int, size: Int, area: Rect) {
        settings.savePosition(
            Geometry.positionToFraction(x, area.left, area.right - size),
            Geometry.positionToFraction(y, area.top, area.bottom - size)
        )
    }

    /** Called when a gesture ends: keeps the bubble on screen, snaps it to an edge and remembers it. */
    private fun settle() {
        val params = bubbleParams ?: return
        val area = usableBounds()
        val size = params.width
        val minX = area.left
        val maxX = area.right - size
        var targetX = Geometry.clamp(params.x, minX, maxX)
        val targetY = Geometry.clamp(params.y, area.top, area.bottom - params.height)
        if (settings.snapToEdge) targetX = Geometry.nearestEdge(targetX, minX, maxX)
        savePosition(targetX, targetY, size, area)
        if (targetX != params.x || targetY != params.y) glideTo(targetX, targetY)
    }

    private fun glideTo(targetX: Int, targetY: Int) {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val fromX = params.x
        val fromY = params.y
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedFraction
                params.x = fromX + ((targetX - fromX) * t).roundToInt()
                params.y = fromY + ((targetY - fromY) * t).roundToInt()
                updateLayout(view, params)
            }
            start()
        }
    }

    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (e: RuntimeException) {
            // Android may refuse to start an activity from the background; nothing else to do.
        }
    }

    private fun closeByUser() {
        settings.bubbleWanted = false
        stopSelf()
    }

    /** Two-finger resize. The position follows the saved fraction, so a bubble on an edge stays on it. */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mainHandler.removeCallbacks(longPressRunnable)
            pinchSize = (bubbleParams?.width ?: sizePx()).toFloat()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val min = dp(AppSettings.MIN_SIZE_DP).toFloat()
            val max = dp(AppSettings.MAX_SIZE_DP).toFloat()
            pinchSize = (pinchSize * detector.scaleFactor).coerceIn(min, max)
            applyGeometry(pinchSize.roundToInt())
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            settings.sizeDp = (pinchSize / resources.displayMetrics.density).roundToInt()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Battery updates (only while the screen is on)
    // ---------------------------------------------------------------------------------------

    private fun showBattery(intent: Intent?) {
        val status = BatteryStatus.from(intent)
        bubble?.setStatus(status.percent, status.charging, status.full)
    }

    private fun startBatteryUpdates() {
        if (batteryReceiverRegistered) return
        val sticky = registerSystemReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiverRegistered = true
        showBattery(sticky)
    }

    private fun stopBatteryUpdates() {
        if (!batteryReceiverRegistered) return
        unregisterQuietly(batteryReceiver)
        batteryReceiverRegistered = false
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerSystemReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
    }

    private fun isInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    // ---------------------------------------------------------------------------------------
    // Foreground notification
    // ---------------------------------------------------------------------------------------

    /** Returns false if Android refused to let the service run in the foreground. */
    private fun enterForeground(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: RuntimeException) {
        false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingBatteryService::class.java).setAction(ACTION_STOP),
            flags
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_stop), stop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }
}
