package tw.chehu.displayfaultsimulator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt
import kotlin.random.Random

class LineOverlayService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var settingsStore: LineSettings
    private lateinit var scenes: SceneRepository
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: DamageSceneView? = null
    private var movementOffsetPx = 0
    private lateinit var sensorManager: SensorManager
    private var lastShakeAt = 0L
    private var lastFlipAt = 0L
    private var lastZSign = 0
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dynamics = scenes.activeScene().dynamics
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> if (dynamics.chargingTrigger) triggerSceneEvent()
                Intent.ACTION_USER_PRESENT -> if (dynamics.unlockTrigger) triggerSceneEvent()
            }
        }
    }

    private val showRunnable = Runnable {
        settingsStore.pendingStartAt = 0L
        showOrUpdateOverlay()
        scheduleAutoStop()
        refreshNotification(getString(R.string.notification_effect_active))
    }

    private val stopRunnable = Runnable { stopFromTimer() }

    private val movementRunnable = object : Runnable {
        override fun run() {
            val scene = scenes.activeScene()
            if (!scene.movementEnabled || overlayView == null) return
            val amplitude = dpToPx(scene.movementDp)
            movementOffsetPx = Random.nextInt(-amplitude, amplitude + 1)
            overlayView?.movementOffsetPx = movementOffsetPx
            handler.postDelayed(this, scene.movementSeconds * 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsStore = LineSettings(this)
        scenes = SceneRepository(this)
        sensorManager = getSystemService(SensorManager::class.java)
        val eventFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(eventReceiver, eventFilter)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_preparing)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopExplicitly()
                return START_NOT_STICKY
            }
            ACTION_START -> configureStart(intent)
            ACTION_RESTORE -> Unit
            ACTION_REFRESH -> Unit
        }

        if (!settingsStore.serviceEnabled || !Settings.canDrawOverlays(this)) {
            settingsStore.serviceEnabled = false
            settingsStore.clearSchedule()
            stopSelf()
            updateTile()
            return START_NOT_STICKY
        }

        restoreScheduleOrShow()
        updateTile()
        return START_STICKY
    }

    private fun configureStart(intent: Intent) {
        val now = System.currentTimeMillis()
        val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 0).coerceIn(0, 3_600)
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0).coerceIn(0, 86_400)
        val startAt = now + delaySeconds * 1_000L
        settingsStore.serviceEnabled = true
        settingsStore.pendingStartAt = if (delaySeconds > 0) startAt else 0L
        settingsStore.autoStopAt = if (durationSeconds > 0) startAt + durationSeconds * 1_000L else 0L
    }

    private fun restoreScheduleOrShow() {
        handler.removeCallbacks(showRunnable)
        handler.removeCallbacks(stopRunnable)
        val now = System.currentTimeMillis()
        val stopAt = settingsStore.autoStopAt
        if (stopAt > 0L && stopAt <= now) {
            stopFromTimer()
            return
        }

        val startAt = settingsStore.pendingStartAt
        if (startAt > now) {
            removeOverlay()
            handler.postDelayed(showRunnable, startAt - now)
            val seconds = ((startAt - now + 999L) / 1_000L).coerceAtLeast(1L)
            refreshNotification(resources.getQuantityString(R.plurals.notification_starts_in, seconds.toInt(), seconds))
        } else {
            settingsStore.pendingStartAt = 0L
            showOrUpdateOverlay()
            scheduleAutoStop()
            refreshNotification(getString(R.string.notification_effect_active))
        }
    }

    @Suppress("DEPRECATION")
    private fun showOrUpdateOverlay() {
        val scene = scenes.activeScene()
        if (overlayView == null) {
            overlayView = DamageSceneView(this).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                updateScene(scene, keepSelection = false)
            }
            windowManager.addView(overlayView, buildLayoutParams())
        } else {
            overlayView?.updateScene(scene)
        }
        movementOffsetPx = 0
        overlayView?.movementOffsetPx = 0
        scheduleMovement(scene)
        configureSensors(scene)
    }

    private fun configureSensors(scene: DamageScene) {
        sensorManager.unregisterListener(this)
        val needsMotion = scene.effects.crackParallax || scene.dynamics.shakeTrigger || scene.dynamics.flipTrigger
        if (needsMotion) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            overlayView?.parallaxX = 0f
            overlayView?.parallaxY = 0f
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        val scene = scenes.activeScene()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (scene.effects.crackParallax) {
            overlayView?.parallaxX = (x / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            overlayView?.parallaxY = (-y / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        }
        val now = System.currentTimeMillis()
        if (scene.dynamics.shakeTrigger) {
            val force = kotlin.math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (force > 2.15f && now - lastShakeAt > 1_500L) {
                lastShakeAt = now
                triggerSceneEvent()
            }
        }
        if (scene.dynamics.flipTrigger && kotlin.math.abs(z) > 7f) {
            val sign = if (z >= 0f) 1 else -1
            if (lastZSign != 0 && sign != lastZSign && now - lastFlipAt > 1_500L) {
                lastFlipAt = now
                triggerSceneEvent()
            }
            lastZSign = sign
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun triggerSceneEvent() {
        val triggerId = scenes.activeScene().dynamics.triggerSceneId
        if (!triggerId.isNullOrBlank()) {
            scenes.find(triggerId)?.let { target ->
                scenes.activeSceneId = target.id
                overlayView?.updateScene(target, keepSelection = false)
                configureSensors(target)
                scheduleMovement(target)
            }
        }
        overlayView?.triggerEventPulse()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val displayBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
                .let { android.graphics.Rect(0, 0, it.x, it.y) }
        }
        return WindowManager.LayoutParams(
            displayBounds.width(),
            displayBounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            title = "Display Fault Simulator overlay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                setFitInsetsSides(0)
                setFitInsetsIgnoringVisibility(true)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun scheduleMovement(scene: DamageScene) {
        handler.removeCallbacks(movementRunnable)
        if (scene.movementEnabled) {
            handler.postDelayed(movementRunnable, scene.movementSeconds * 1_000L)
        }
    }

    private fun scheduleAutoStop() {
        handler.removeCallbacks(stopRunnable)
        val remaining = settingsStore.autoStopAt - System.currentTimeMillis()
        if (settingsStore.autoStopAt > 0L && remaining > 0L) handler.postDelayed(stopRunnable, remaining)
    }

    private fun stopFromTimer() {
        settingsStore.serviceEnabled = false
        settingsStore.clearSchedule()
        removeOverlay()
        updateTile()
        stopSelf()
    }

    private fun stopExplicitly() {
        settingsStore.serviceEnabled = false
        settingsStore.clearSchedule()
        removeOverlay()
        updateTile()
        stopSelf()
    }

    private fun removeOverlay() {
        handler.removeCallbacks(movementRunnable)
        sensorManager.unregisterListener(this)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        movementOffsetPx = 0
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        movementOffsetPx = 0
        overlayView?.apply {
            movementOffsetPx = 0
            updateScene(scenes.activeScene())
            windowManager.updateViewLayout(this, buildLayoutParams())
        }
        createNotificationChannel()
        val remaining = settingsStore.pendingStartAt - System.currentTimeMillis()
        refreshNotification(
            if (remaining > 0L) {
                val seconds = ((remaining + 999L) / 1_000L).coerceAtLeast(1L).toInt()
                resources.getQuantityString(R.plurals.notification_starts_in, seconds, seconds)
            } else {
                getString(R.string.notification_effect_active)
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(this)
        runCatching { unregisterReceiver(eventReceiver) }
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LineOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_stop), stopIntent).build())
            .build()
    }

    private fun refreshNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateTile() {
        TileService.requestListeningState(this, android.content.ComponentName(this, DamageTileService::class.java))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ACTION_START = "tw.chehu.displayfaultsimulator.action.START"
        const val ACTION_STOP = "tw.chehu.displayfaultsimulator.action.STOP"
        const val ACTION_REFRESH = "tw.chehu.displayfaultsimulator.action.REFRESH"
        const val ACTION_RESTORE = "tw.chehu.displayfaultsimulator.action.RESTORE"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val CHANNEL_ID = "damage_overlay"
        private const val NOTIFICATION_ID = 4101
    }
}
