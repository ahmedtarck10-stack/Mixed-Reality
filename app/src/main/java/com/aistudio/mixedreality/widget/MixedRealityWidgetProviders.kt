package com.aistudio.mixedreality.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aistudio.mixedreality.MainActivity
import com.aistudio.mixedreality.R
import com.aistudio.mixedreality.ui.ModelItem
import com.aistudio.mixedreality.ui.ViewportMode
import com.aistudio.mixedreality.util.MRNotificationManager

/**
 * Interactive broadcast actions handled directly on the user's home screen without leaving the launcher.
 */
object MixedRealityWidgetActions {
    const val ACTION_PREV_MODEL = "com.aistudio.mixedreality.widget.ACTION_PREV_MODEL"
    const val ACTION_NEXT_MODEL = "com.aistudio.mixedreality.widget.ACTION_NEXT_MODEL"
}

/**
 * Coordinates live updates across both Studio (4x2) and Compact (2x2) home screen widgets.
 */
object MixedRealityWidgetUpdater {

    fun syncAndRefresh(
        context: Context,
        selectedModel: ModelItem,
        activeMode: ViewportMode,
        customModelName: String?,
        customStatsSummary: String?,
        isModelVisible: Boolean,
        isRecording: Boolean
    ) {
        val snapshot = WidgetStateStore.saveFromApp(
            context = context,
            selectedModel = selectedModel,
            activeMode = activeMode,
            customModelName = customModelName,
            customStatsSummary = customStatsSummary,
            isModelVisible = isModelVisible,
            isRecording = isRecording
        )
        refreshAll(context, snapshot)
    }

    fun refreshAll(context: Context, snapshot: WidgetSnapshot = WidgetStateStore.load(context)) {
        val manager = AppWidgetManager.getInstance(context) ?: return

        val studioIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityStudioWidgetProvider::class.java)
        )
        if (studioIds.isNotEmpty()) {
            val views = buildStudioRemoteViews(context, snapshot)
            manager.updateAppWidget(studioIds, views)
        }

        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityCompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            val views = buildCompactRemoteViews(context, snapshot)
            manager.updateAppWidget(compactIds, views)
        }
    }

    internal fun buildStudioRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_studio_mixed_reality)
        views.setTextViewText(R.id.widget_studio_mode_badge, snapshot.modeBadgeLabel)
        views.setTextViewText(R.id.widget_active_model_title, snapshot.displayTitle)
        views.setTextViewText(R.id.widget_active_model_meta, snapshot.displayMeta)

        // Interactive in-widget preset cycling buttons (update live on home screen!)
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev_model,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_PREV_MODEL, 201)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next_model,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_NEXT_MODEL, 202)
        )

        // Tap model showcase or header -> open app with the widget's currently selected model
        val modelIntent = if (snapshot.customModelName.isNullOrBlank()) {
            createLaunchIntent(
                context = context,
                requestCode = 203,
                shortcutAction = MRNotificationManager.ACTION_SELECT_MODEL,
                modelName = snapshot.selectedModel.name
            )
        } else {
            createLaunchIntent(
                context = context,
                requestCode = 203,
                shortcutAction = null,
                modelName = null
            )
        }
        views.setOnClickPendingIntent(R.id.widget_model_showcase_container, modelIntent)
        views.setOnClickPendingIntent(R.id.widget_studio_header, modelIntent)

        // 4 Quick-Launch Spatial Dock Buttons (3D / AR / MR / Open)
        views.setOnClickPendingIntent(
            R.id.widget_action_object,
            createLaunchIntent(
                context = context,
                requestCode = 204,
                shortcutAction = MRNotificationManager.ACTION_LAUNCH_OBJECT,
                modelName = if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_ar,
            createLaunchIntent(
                context = context,
                requestCode = 205,
                shortcutAction = MRNotificationManager.ACTION_LAUNCH_AR,
                modelName = if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_mr,
            createLaunchIntent(
                context = context,
                requestCode = 206,
                shortcutAction = MRNotificationManager.ACTION_LAUNCH_MR,
                modelName = if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_open,
            createLaunchIntent(
                context = context,
                requestCode = 207,
                shortcutAction = MRNotificationManager.ACTION_OPEN_PICKER,
                modelName = null
            )
        )
        return views
    }

    internal fun buildCompactRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_mixed_reality)
        views.setTextViewText(R.id.widget_compact_mode_chip, snapshot.compactModeLabel)
        views.setTextViewText(R.id.widget_compact_model_title, snapshot.displayTitle)
        views.setTextViewText(
            R.id.widget_compact_model_meta,
            snapshot.customStatsSummary?.takeIf { !snapshot.customModelName.isNullOrBlank() && it.isNotBlank() }
                ?: "Tap to launch • ▶ to switch"
        )

        views.setOnClickPendingIntent(
            R.id.widget_compact_next_btn,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_NEXT_MODEL, 211)
        )

        val launchActiveIntent = createLaunchIntent(
            context = context,
            requestCode = 212,
            shortcutAction = if (snapshot.customModelName.isNullOrBlank()) MRNotificationManager.ACTION_SELECT_MODEL else null,
            modelName = if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
        )
        views.setOnClickPendingIntent(R.id.widget_compact_launch, launchActiveIntent)
        views.setOnClickPendingIntent(R.id.widget_compact_model_card, launchActiveIntent)

        views.setOnClickPendingIntent(
            R.id.widget_compact_action_ar,
            createLaunchIntent(
                context = context,
                requestCode = 213,
                shortcutAction = MRNotificationManager.ACTION_LAUNCH_AR,
                modelName = if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_action_open,
            createLaunchIntent(
                context = context,
                requestCode = 214,
                shortcutAction = MRNotificationManager.ACTION_OPEN_PICKER,
                modelName = null
            )
        )
        return views
    }

    private fun createBroadcastIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MixedRealityStudioWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createLaunchIntent(
        context: Context,
        requestCode: Int,
        shortcutAction: String?,
        modelName: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (shortcutAction != null) {
                putExtra(MRNotificationManager.EXTRA_SHORTCUT_ACTION, shortcutAction)
            }
            if (modelName != null) {
                putExtra(MRNotificationManager.EXTRA_MODEL_NAME, modelName)
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

/**
 * 4×2 Interactive Studio Home Screen Widget with live preset switcher and 1-tap Object/AR/MR/Open actions.
 */
class MixedRealityStudioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetStateStore.load(context)
        val views = MixedRealityWidgetUpdater.buildStudioRemoteViews(context, snapshot)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            MixedRealityWidgetActions.ACTION_PREV_MODEL -> {
                val updated = WidgetStateStore.stepPreset(context, -1)
                MixedRealityWidgetUpdater.refreshAll(context, updated)
            }
            MixedRealityWidgetActions.ACTION_NEXT_MODEL -> {
                val updated = WidgetStateStore.stepPreset(context, +1)
                MixedRealityWidgetUpdater.refreshAll(context, updated)
            }
        }
    }
}

/**
 * 2×2 Compact Spatial Portal Home Screen Widget with quick model switching and AR/Open launch buttons.
 */
class MixedRealityCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetStateStore.load(context)
        val views = MixedRealityWidgetUpdater.buildCompactRemoteViews(context, snapshot)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }
}
