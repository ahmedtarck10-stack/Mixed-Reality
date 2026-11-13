package com.aistudio.mixedreality.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aistudio.mixedreality.MainActivity
import com.aistudio.mixedreality.R

/**
 * Manages system notifications displayed in Android's top notification shade & Control Center.
 * Uses Android O+ NotificationChannels and Android 13+ POST_NOTIFICATIONS runtime checks.
 */
object MRNotificationManager {

    const val CHANNEL_SESSION_ID = "mr_session_status"
    const val CHANNEL_EVENTS_ID = "mr_model_events"

    const val NOTIFICATION_ID_SESSION = 1001
    const val NOTIFICATION_ID_EVENT = 1002

    const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    const val ACTION_LAUNCH_AR = "launch_ar"
    const val ACTION_OPEN_PICKER = "open_picker"
    const val ACTION_CLOUD_LIBRARY = "cloud_library"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val sessionChannel = NotificationChannel(
            CHANNEL_SESSION_ID,
            context.getString(R.string.notification_channel_session_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_session_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val eventsChannel = NotificationChannel(
            CHANNEL_EVENTS_ID,
            context.getString(R.string.notification_channel_events_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_events_desc)
            setShowBadge(true)
        }

        manager.createNotificationChannel(sessionChannel)
        manager.createNotificationChannel(eventsChannel)
    }

    /**
     * Pure formatting helper for session notification copy (directly unit-testable).
     */
    fun buildSessionSummaryText(
        modeLabel: String,
        modelLabel: String,
        statsSummary: String?,
        isRecording: Boolean
    ): Pair<String, String> {
        val cleanMode = modeLabel.ifBlank { "Object" }
        val cleanModel = modelLabel.ifBlank { "3D Workspace" }
        val title = if (isRecording) {
            "REC Active • $cleanMode Mode"
        } else {
            "Mixed Reality • $cleanMode Mode"
        }
        val body = buildString {
            append(cleanModel)
            if (!statsSummary.isNullOrBlank()) {
                append(" • ")
                append(statsSummary)
            }
            if (isRecording) {
                append(" • Recording screen")
            }
        }
        return title to body
    }

    fun showSessionNotification(
        context: Context,
        modeLabel: String,
        modelLabel: String,
        statsSummary: String? = null,
        isRecording: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val (title, body) = buildSessionSummaryText(
            modeLabel = modeLabel,
            modelLabel = modelLabel,
            statsSummary = statsSummary,
            isRecording = isRecording
        )

        val contentIntent = createMainPendingIntent(context, requestCode = 100, shortcutAction = null)
        val arIntent = createMainPendingIntent(context, requestCode = 101, shortcutAction = ACTION_LAUNCH_AR)
        val openIntent = createMainPendingIntent(context, requestCode = 102, shortcutAction = ACTION_OPEN_PICKER)

        val notification = NotificationCompat.Builder(context, CHANNEL_SESSION_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, context.getString(R.string.notification_action_ar), arIntent)
            .addAction(0, context.getString(R.string.notification_action_open), openIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SESSION, notification)
        }
    }

    fun showEventNotification(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val contentIntent = createMainPendingIntent(context, requestCode = 103, shortcutAction = null)
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EVENT, notification)
        }
    }

    fun cancelSessionNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SESSION)
        }
    }

    private fun createMainPendingIntent(
        context: Context,
        requestCode: Int,
        shortcutAction: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (shortcutAction != null) {
                putExtra(EXTRA_SHORTCUT_ACTION, shortcutAction)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
