package com.islandx.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

class IslandService : Service() {

    companion object {
        const val ACTION_NOTIF = "com.islandx.widget.NOTIF"
        const val PREFS = "island_prefs"
        const val KEY_Y = "y_dp"
        const val KEY_W = "w_dp"
        const val KEY_MUSIC = "music"
        const val KEY_CALL = "call"
        const val KEY_CHARGE = "charge"
        const val KEY_NOTIF = "notif"
        private const val CHANNEL = "island_fg"
    }

    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var view: IslandView
    private lateinit var winParams: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    // state
    private var callState = TelephonyManager.CALL_STATE_IDLE
    private var callStart = 0L
    private var controller: MediaController? = null
    private var transient: IslandView.Ui? = null
    private var userExpanded = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- lifecycle ----------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = IslandView(this)
        root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(dp(140), dp(36), Gravity.CENTER_HORIZONTAL))

        winParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        applyPrefs()

        view.onTap = { toggleExpanded() }
        view.onLongPress = { openSourceApp() }
        view.onPrev = { controller?.transportControls?.skipToPrevious() }
        view.onNext = { controller?.transportControls?.skipToNext() }
        view.onPlayPause = {
            val c = controller
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            }
        }

        view.visibility = android.view.View.GONE
        wm.addView(root, winParams)

        registerReceivers()
        registerCallListener()
        registerMedia()
        refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyPrefs()
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            tm.unregisterTelephonyCallback(callCallback)
        } catch (_: Exception) {}
        try {
            getSystemService(MediaSessionManager::class.java)
                .removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {}
        controller?.unregisterCallback(mediaCallback)
        try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Island X running", NotificationManager.IMPORTANCE_MIN)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Island X is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    private fun applyPrefs() {
        view.compactWidthDp = prefs.getInt(KEY_W, 140)
        winParams.y = dp(prefs.getInt(KEY_Y, 8))
        if (root.isAttachedToWindow) wm.updateViewLayout(root, winParams)
    }

    // ---------- inputs ----------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> if (prefs.getBoolean(KEY_CHARGE, true)) showCharging()
                ACTION_NOTIF -> if (prefs.getBoolean(KEY_NOTIF, true)) {
                    showTransient(
                        IslandView.Ui(
                            left = (i.getStringExtra("app") ?: "").take(10),
                            leftColor = Color.rgb(140, 200, 255),
                            title = i.getStringExtra("title").orEmpty(),
                            subtitle = i.getStringExtra("text").orEmpty()
                        ), 3500
                    )
                }
            }
        }
    }

    private fun registerReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(ACTION_NOTIF)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun showCharging() {
        val bm = getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        showTransient(
            IslandView.Ui(
                left = "⚡", leftColor = Color.rgb(120, 255, 120),
                right = "$pct%", rightColor = Color.rgb(120, 255, 120)
            ), 2500, expand = false
        )
    }

    private val callCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            h.post {
                callState = state
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) callStart = SystemClock.elapsedRealtime()
                refresh()
                h.removeCallbacks(callTick)
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) h.postDelayed(callTick, 1000)
            }
        }
    }

    private val callTick = object : Runnable {
        override fun run() {
            if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                refresh()
                h.postDelayed(this, 1000)
            }
        }
    }

    private fun registerCallListener() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            getSystemService(TelephonyManager::class.java)
                .registerTelephonyCallback(mainExecutor, callCallback)
        } catch (_: Exception) {}
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pickController(list)
    }

    private fun registerMedia() {
        val cn = ComponentName(this, NotifListener::class.java)
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            pickController(msm.getActiveSessions(cn))
        } catch (_: SecurityException) {
            // Notification access not granted yet
        }
    }

    private fun pickController(list: List<MediaController>?) {
        controller?.unregisterCallback(mediaCallback)
        controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        controller?.registerCallback(mediaCallback)
        refresh()
    }

    // ---------- rendering ----------

    private fun showTransient(ui: IslandView.Ui, ms: Long, expand: Boolean = true) {
        transient = ui
        view.render(ui, expand)
        h.removeCallbacks(clearTransient)
        h.postDelayed(clearTransient, ms)
    }

    private val clearTransient = Runnable { transient = null; refresh() }

    private val autoCollapse = Runnable { userExpanded = false; refresh() }

    private fun toggleExpanded() {
        userExpanded = !userExpanded
        h.removeCallbacks(autoCollapse)
        if (userExpanded) h.postDelayed(autoCollapse, 6000)
        refresh()
    }

    private fun refresh() {
        transient?.let { view.render(it, it.title.isNotEmpty()); return }

        if (prefs.getBoolean(KEY_CALL, true) &&
            callState != TelephonyManager.CALL_STATE_IDLE) {
            val ringing = callState == TelephonyManager.CALL_STATE_RINGING
            val secs = ((SystemClock.elapsedRealtime() - callStart) / 1000).toInt()
            val timer = if (ringing) "Ringing" else "%d:%02d".format(secs / 60, secs % 60)
            view.render(
                IslandView.Ui(
                    left = "📞 $timer", leftColor = Color.rgb(120, 255, 120),
                    wave = true, waveColor = Color.rgb(120, 255, 120)
                ), false
            )
            return
        }

        val c = controller
        val md = c?.metadata
        if (prefs.getBoolean(KEY_MUSIC, true) && c != null && md != null) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val t = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val a = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            if (playing || userExpanded) {
                view.render(
                    IslandView.Ui(
                        left = "♪", leftColor = Color.rgb(255, 120, 160),
                        wave = true, waveColor = Color.rgb(255, 150, 90),
                        title = t, subtitle = a, controls = true,
                        playing = playing, expandable = true
                    ), userExpanded
                )
                return
            }
        }
        view.hide()
    }

    private fun openSourceApp() {
        try {
            controller?.sessionActivity?.send()
        } catch (_: Exception) {}
    }
}
