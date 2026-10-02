package com.example.aeroshare.service

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
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class TransferForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "aeroshare_transfers_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.aeroshare.action.START"
        const val ACTION_UPDATE = "com.example.aeroshare.action.UPDATE"
        const val ACTION_STOP = "com.example.aeroshare.action.STOP"

        const val EXTRA_IS_SENDING = "extra_is_sending"
        const val EXTRA_TOTAL_FILES = "extra_total_files"
        const val EXTRA_CURRENT_FILE = "extra_current_file"
        const val EXTRA_BYTES_TRANSFERRED = "extra_bytes_transferred"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_SPEED_BYTES = "extra_speed_bytes"

        fun startService(context: Context, isSending: Boolean, totalFiles: Int, currentFile: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IS_SENDING, isSending)
                putExtra(EXTRA_TOTAL_FILES, totalFiles)
                putExtra(EXTRA_CURRENT_FILE, currentFile)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(
            context: Context,
            bytesTransferred: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
            currentFile: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_BYTES_TRANSFERRED, bytesTransferred)
                putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                putExtra(EXTRA_SPEED_BYTES, speedBytesPerSec)
                putExtra(EXTRA_CURRENT_FILE, currentFile)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var isSending = true
    private var totalFiles = 1
    private var currentFileName = "File"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isSending = intent.getBooleanExtra(EXTRA_IS_SENDING, true)
                totalFiles = intent.getIntExtra(EXTRA_TOTAL_FILES, 1)
                currentFileName = intent.getStringExtra(EXTRA_CURRENT_FILE) ?: "File"
                val notification = buildNotification(
                    progressPercent = 0,
                    content = if (isSending) "Preparing to send $totalFiles files..." else "Preparing to receive $totalFiles files..."
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val transferred = intent.getLongExtra(EXTRA_BYTES_TRANSFERRED, 0L)
                val total = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                val speed = intent.getLongExtra(EXTRA_SPEED_BYTES, 0L)
                currentFileName = intent.getStringExtra(EXTRA_CURRENT_FILE) ?: currentFileName

                val percent = if (total > 0) ((transferred * 100) / total).toInt().coerceIn(0, 100) else 0
                val speedStr = formatSpeed(speed)
                val transferredStr = formatBytes(transferred)
                val totalStr = formatBytes(total)

                val content = "$transferredStr / $totalStr • $percent% • $speedStr"
                val notification = buildNotification(percent, content)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(progressPercent: Int, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSending) "Sending $currentFileName" else "Receiving $currentFileName"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for nearby file transfers"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
        return String.format("%.1f MB/s", mbPerSec)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
