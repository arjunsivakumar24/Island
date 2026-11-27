package com.islandx.widget

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Needed for two reasons:
 * 1. Android only lets apps read the active media session if they hold notification access.
 * 2. It forwards new notifications to the island so they can pop up briefly.
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return  // media is handled separately

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) { sbn.packageName }

        sendBroadcast(Intent(IslandService.ACTION_NOTIF).setPackage(packageName)
            .putExtra("app", label)
            .putExtra("title", title)
            .putExtra("text", text))
    }
}
