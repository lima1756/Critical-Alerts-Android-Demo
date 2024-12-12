package com.surendar4.criticalnotifications.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.surendar4.criticalnotifications.BaseApplication
import com.surendar4.criticalnotifications.R
import kotlin.math.ceil

object NotificationNotifier : ContextWrapper(BaseApplication.getInstance()) {

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    fun postCriticalNotification(ringerVolume: Float) {
        val channelId = createDefaultChannel(true)
        val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.alarm}")
        val notification = buildNotification(channelId, "Critical Notification", "It's a critical notification", soundUri)

        overrideSilentModeAndConfigureCustomVolume(ringerVolume, soundUri)
        notificationManager.notify(getNotificationId(), notification)
    }

    fun postNormalNotification() {
        val channelId = createDefaultChannel(false)
        val notification = buildNotification(channelId, "Normal Notification", "It's a normal notification", null)

        notificationManager.notify(getNotificationId(), notification)
    }

    private fun createDefaultChannel(isCriticalNotification: Boolean = false): String {
        val channelId = "default"
        val notificationChannel = NotificationChannel(
            channelId,
            "Default",
            if (isCriticalNotification) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationChannel.setBypassDnd(isCriticalNotification)
        if (isCriticalNotification) {
            val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.alarm}")
            notificationChannel.setSound(soundUri, null)
        }
        notificationManager.createNotificationChannel(notificationChannel)
        return channelId
    }

    private fun buildNotification(channel: String, title: String, message: String, soundUri: Uri?): Notification {
        return NotificationCompat.Builder(this, channel)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                if (soundUri != null) {
                    setSound(soundUri)
                }
            }
            .build()
    }

    private fun getNotificationId(): Int {
        val prefs: SharedPreferences = getSharedPreferences("notification_ids", MODE_PRIVATE)
        val notificationId = prefs.getInt("notification_id", 0)
        prefs.edit().putInt("notification_id", notificationId + 1).apply()
        return notificationId
    }

    private fun overrideSilentModeAndConfigureCustomVolume(ringerVolume: Float?, soundUri: Uri) {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            var originalRingMode = audioManager.ringerMode
            val originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val maxNotificationVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

            // When DND mode is enabled, we get ringerMode as silent even though actual ringer mode is Normal
            val isDndModeEnabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            if (isDndModeEnabled && originalRingMode == AudioManager.RINGER_MODE_SILENT && originalNotificationVolume != 0) {
                originalRingMode = AudioManager.RINGER_MODE_NORMAL
            }

            val newVolume = if (ringerVolume != null) {
                ceil(maxNotificationVolume * ringerVolume).toInt()
            } else {
                originalNotificationVolume
            }

            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, newVolume, 0)

            // Resetting the original ring mode, volume, and DND mode
            Handler(Looper.getMainLooper()).postDelayed({
                audioManager.ringerMode = originalRingMode
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
            }, getSoundFileDuration(soundUri))
        } catch (ex: Exception) {
        }
    }

    private fun getSoundFileDuration(uri: Uri): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLong() ?: 0
        } catch (ex: Exception) {
            5000
        }
    }
}
