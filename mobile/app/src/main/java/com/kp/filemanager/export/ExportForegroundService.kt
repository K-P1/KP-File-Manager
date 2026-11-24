/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ExportForegroundService.kt
 * Purpose: Keep long, user-initiated Shizuku exports foreground and mirror progress to a system notification.
 * Last changed: 2026-11-23, protect multi-gigabyte transfers from background restrictions.
 */
package com.kp.filemanager.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.kp.filemanager.MainActivity
import com.kp.filemanager.R

class ExportForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = notification(this, "Preparing protected media export", null)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "kp_protected_export"
        private const val NOTIFICATION_ID = 2189
        private const val ACTION_STOP = "com.kp.filemanager.export.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ExportForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun progress(context: Context, data: ExportProgress) {
            val title = data.stage.label
            val percent = data.percent
            val text = buildString {
                if (data.currentFile.isNotBlank()) append(data.currentFile.take(48))
                else append(data.message.take(70))
                if (data.totalFiles > 0) append(" · ${data.completedFiles}/${data.totalFiles} files")
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification(context, title, percent, text))
        }

        fun stop(context: Context) {
            // Android 8+ may reject startService() when the activity has moved to background.
            // stopService() does not require starting a new background service and removes
            // the foreground notification with the service itself.
            context.stopService(Intent(context, ExportForegroundService::class.java))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "Protected media transfers", NotificationManager.IMPORTANCE_LOW))
            }
        }

        @Suppress("DEPRECATION")
        private fun notification(context: Context, title: String, percent: Int?, detail: String = "Copy, verify and organize safely"): Notification {
            val intent = Intent(context, MainActivity::class.java)
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getActivity(context, 0, intent, pendingFlags)
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID)
                else Notification.Builder(context)
            return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(detail)
                .setContentIntent(pending)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(100, percent ?: 0, percent == null)
                .build()
        }
    }
}
