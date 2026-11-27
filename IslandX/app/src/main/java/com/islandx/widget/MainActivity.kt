package com.islandx.widget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences(IslandService.PREFS, Context.MODE_PRIVATE) }
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(col) }
        setContentView(scroll)

        fun label(t: String, size: Float = 14f, color: Int = Color.WHITE) =
            TextView(this).apply { text = t; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(4)) }

        fun button(t: String, action: () -> Unit) =
            Button(this).apply { text = t; setOnClickListener { action() } }

        col.addView(label("Island X", 28f))
        col.addView(label("Dynamic Island for your Oppo A3 Pro 5G", 14f, Color.LTGRAY))
        status = label("", 13f, Color.rgb(140, 200, 255))
        col.addView(status)

        col.addView(label("1. Permissions", 16f))
        col.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("Allow notification access (music + alerts)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(button("Allow phone state (call timer) and notifications") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS), 1)
        })

        col.addView(label("2. What to show", 16f))
        fun toggle(text: String, key: String) {
            col.addView(CheckBox(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                isChecked = prefs.getBoolean(key, true)
                setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply(); poke() }
            })
        }
        toggle("Music player", IslandService.KEY_MUSIC)
        toggle("Call timer", IslandService.KEY_CALL)
        toggle("Charging", IslandService.KEY_CHARGE)
        toggle("Notification pop-ups", IslandService.KEY_NOTIF)

        col.addView(label("3. Fit it to your camera hole", 16f))
        fun slider(text: String, key: String, max: Int, def: Int, min: Int) {
            val t = label("$text: ${prefs.getInt(key, def)} dp")
            col.addView(t)
            col.addView(SeekBar(this).apply {
                this.max = max - min
                progress = prefs.getInt(key, def) - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        t.text = "$text: ${p + min} dp"
                        if (fromUser) { prefs.edit().putInt(key, p + min).apply(); poke() }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }
        slider("Pill width", IslandService.KEY_W, 220, 140, 90)
        slider("Distance from top", IslandService.KEY_Y, 40, 8, 0)

        col.addView(label("4. Start", 16f))
        col.addView(button("Start Island") {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "Grant 'display over other apps' first."
            } else {
                startForegroundService(Intent(this, IslandService::class.java))
                status.text = "Island is running."
            }
        })
        col.addView(button("Stop Island") {
            stopService(Intent(this, IslandService::class.java))
            status.text = "Stopped."
        })

        col.addView(label(
            "Oppo tip: Settings > Battery > Island X > allow background activity, and lock the app in Recents so ColorOS does not kill it.",
            12f, Color.GRAY
        ))
        scroll.setBackgroundColor(Color.parseColor("#0B0B10"))
        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun poke() {
        // tell a running service to re-read settings
        if (Settings.canDrawOverlays(this)) {
            try { startForegroundService(Intent(this, IslandService::class.java)) } catch (_: Exception) {}
        }
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val notif = listeners.contains(packageName)
        val phone = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        status.text = "Overlay: ${if (overlay) "OK" else "needed"}  |  " +
            "Notification access: ${if (notif) "OK" else "needed"}  |  " +
            "Phone: ${if (phone) "OK" else "needed"}"
    }
}
