package com.sysadmindoc.alarmclock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.model.Alarm

/**
 * Last-resort alarm notification when Android denies a background foreground
 * service launch. This is not equivalent to the service's looping alarm audio:
 * notification permissions, user-muted channels, and DND remain authoritative.
 *
 * The normal alarm channel is intentionally silent because AlarmService owns
 * continuous music playback. A cold-start FGS denial must instead use a
 * separate, initially audible emergency channel or it would fail silently.
 */
object AlarmFullScreenFallback {
    private const val TAG = "AlarmFullScreenFallback"
    private const val CHANNEL_EMERGENCY = "alarm_fgs_emergency_v1"

    /** Existing silent fallback for recovery/wake-confirm notifications. */
    fun post(
        context: Context,
        alarm: Alarm,
        scheduledAt: Long,
        fireId: String,
        hideLabel: Boolean
    ): Boolean = postInternal(
        context = context,
        alarmId = alarm.id,
        scheduledAt = scheduledAt,
        fireId = fireId,
        label = alarm.label.ifBlank { context.getString(R.string.notif_alarm_title) },
        hideLabel = hideLabel,
        audible = false
    )

    /**
     * Must be callable synchronously from AlarmReceiver without loading a
     * credential-protected Room row or DataStore during a locked screen wake.
     * Never exposes the private alarm label in an emergency notification.
     */
    fun postEmergency(
        context: Context,
        alarmId: Long,
        scheduledAt: Long,
        fireId: String
    ): Boolean = postInternal(
        context = context,
        alarmId = alarmId,
        scheduledAt = scheduledAt,
        fireId = fireId,
        label = context.getString(R.string.notif_alarm_ringing),
        hideLabel = true,
        audible = true
    )

    private fun postInternal(
        context: Context,
        alarmId: Long,
        scheduledAt: Long,
        fireId: String,
        label: String,
        hideLabel: Boolean,
        audible: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channelId = if (audible) {
                // Android 8+ channel sound cannot be changed after creation.
                // Never reuse the deliberately silent standard alarm channel.
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                    ?: return false
                val channel = NotificationChannel(
                    CHANNEL_EMERGENCY,
                    context.getString(R.string.notif_alarm_ringing),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_alarm_desc)
                    if (soundUri != null) {
                        setSound(
                            soundUri,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                CHANNEL_EMERGENCY
            } else {
                AlarmService.CHANNEL_ALARM
            }

            val firingIntent = AlarmFireDismissContract.firingActivityIntent(
                context, alarmId, scheduledAt, fireId
            )
            val fullScreenPi = PendingIntent.getActivity(
                context,
                alarmId.toInt() + 30000,
                firingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(context.getString(R.string.notif_alarm_title))
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(fullScreenPi)
                .setAutoCancel(true)
                .setOngoing(false)
            if (audible && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Pre-26 devices use per-notification sound instead of channels.
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                if (soundUri != null) builder.setSound(soundUri)
            }
            if (hideLabel) {
                val publicVersion = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle(context.getString(R.string.notif_alarm_title))
                    .setContentText(context.getString(R.string.notif_alarm_ringing))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(fullScreenPi)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build()
                builder
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
            } else {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            // Same ID as the live AlarmService notification, so a late service
            // start replaces this fallback instead of creating two alerts.
            NotificationManagerCompat.from(context)
                .notify(AlarmService.NOTIFICATION_ID, builder.build())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback notification failed for $alarmId", e)
            false
        }
    }
}
