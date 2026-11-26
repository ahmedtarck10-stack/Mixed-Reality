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
    const val ACTION_SELECT_PRESET = "com.aistudio.mixedreality.widget.ACTION_SELECT_PRESET"
    const val EXTRA_PRESET_NAME = "preset_model_name"
}

/**
 * Coordinates live Liquid-Glass updates across all 5 Mixed Reality home screen widgets:
 * 1) 4x2 Mixed Reality Studio
 * 2) 2x2 MR Quick Portal
 * 3) 4x1 Liquid Glass Capsule Bar
 * 4) 4x3 3D Model & Cloud Deck
 * 5) 2x1 Liquid Glass Mini Pill
 */
object MixedRealityWidgetUpdater {

    private const val COLOR_TEXT_SELECTED = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_UNSELECTED = 0xB3CAD5E2.toInt()

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
            manager.updateAppWidget(studioIds, buildStudioRemoteViews(context, snapshot))
        }

        val compactIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityCompactWidgetProvider::class.java)
        )
        if (compactIds.isNotEmpty()) {
            manager.updateAppWidget(compactIds, buildCompactRemoteViews(context, snapshot))
        }

        val capsuleIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityCapsuleBarWidgetProvider::class.java)
        )
        if (capsuleIds.isNotEmpty()) {
            manager.updateAppWidget(capsuleIds, buildCapsuleBarRemoteViews(context, snapshot))
        }

        val deckIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityModelDeckWidgetProvider::class.java)
        )
        if (deckIds.isNotEmpty()) {
            manager.updateAppWidget(deckIds, buildModelDeckRemoteViews(context, snapshot))
        }

        val miniOrbIds = manager.getAppWidgetIds(
            ComponentName(context, MixedRealityMiniOrbWidgetProvider::class.java)
        )
        if (miniOrbIds.isNotEmpty()) {
            manager.updateAppWidget(miniOrbIds, buildMiniOrbRemoteViews(context, snapshot))
        }
    }

    internal fun buildStudioRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_studio_mixed_reality)
        views.setTextViewText(R.id.widget_active_model_title, snapshot.displayTitle)
        views.setTextViewText(R.id.widget_active_model_meta, snapshot.displayMeta)

        applySegmentedModeHighlight(
            views = views,
            activeMode = snapshot.activeMode,
            mrTabId = R.id.widget_studio_tab_mr,
            arTabId = R.id.widget_studio_tab_ar,
            objectTabId = R.id.widget_studio_tab_object
        )

        val selectedPreset = activePresetNameOrNull(snapshot)

        // Interactive top segmented tabs (launch directly into chosen spatial mode)
        views.setOnClickPendingIntent(
            R.id.widget_studio_tab_mr,
            createLaunchIntent(context, 200, MRNotificationManager.ACTION_LAUNCH_MR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_studio_tab_ar,
            createLaunchIntent(context, 208, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_studio_tab_object,
            createLaunchIntent(context, 209, MRNotificationManager.ACTION_LAUNCH_OBJECT, selectedPreset)
        )

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
        val modelIntent = createLaunchIntent(
            context = context,
            requestCode = 203,
            shortcutAction = if (selectedPreset != null) MRNotificationManager.ACTION_SELECT_MODEL else null,
            modelName = selectedPreset
        )
        views.setOnClickPendingIntent(R.id.widget_model_showcase_container, modelIntent)
        views.setOnClickPendingIntent(R.id.widget_studio_header, modelIntent)

        // Unified Bottom Dock Buttons (3D / Glowing AR Orb / MR / Open)
        views.setOnClickPendingIntent(
            R.id.widget_action_object,
            createLaunchIntent(context, 204, MRNotificationManager.ACTION_LAUNCH_OBJECT, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_ar,
            createLaunchIntent(context, 205, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_mr,
            createLaunchIntent(context, 206, MRNotificationManager.ACTION_LAUNCH_MR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_action_open,
            createLaunchIntent(context, 207, MRNotificationManager.ACTION_OPEN_PICKER, null)
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

        val selectedPreset = activePresetNameOrNull(snapshot)

        views.setOnClickPendingIntent(
            R.id.widget_compact_next_btn,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_NEXT_MODEL, 211)
        )

        val launchActiveIntent = createLaunchIntent(
            context = context,
            requestCode = 212,
            shortcutAction = if (selectedPreset != null) MRNotificationManager.ACTION_SELECT_MODEL else null,
            modelName = selectedPreset
        )
        views.setOnClickPendingIntent(R.id.widget_compact_launch, launchActiveIntent)
        views.setOnClickPendingIntent(R.id.widget_compact_model_card, launchActiveIntent)

        views.setOnClickPendingIntent(
            R.id.widget_compact_action_ar,
            createLaunchIntent(context, 213, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_action_open,
            createLaunchIntent(context, 214, MRNotificationManager.ACTION_OPEN_PICKER, null)
        )
        return views
    }

    internal fun buildCapsuleBarRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_capsule_bar_mixed_reality)
        applySegmentedModeHighlight(
            views = views,
            activeMode = snapshot.activeMode,
            mrTabId = R.id.widget_capsule_tab_mr,
            arTabId = R.id.widget_capsule_tab_ar,
            objectTabId = R.id.widget_capsule_tab_object
        )

        val selectedPreset = activePresetNameOrNull(snapshot)

        views.setOnClickPendingIntent(
            R.id.widget_capsule_app_icon,
            createLaunchIntent(
                context = context,
                requestCode = 220,
                shortcutAction = if (selectedPreset != null) MRNotificationManager.ACTION_SELECT_MODEL else null,
                modelName = selectedPreset
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_capsule_tab_mr,
            createLaunchIntent(context, 221, MRNotificationManager.ACTION_LAUNCH_MR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_capsule_tab_ar,
            createLaunchIntent(context, 222, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_capsule_tab_object,
            createLaunchIntent(context, 223, MRNotificationManager.ACTION_LAUNCH_OBJECT, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_capsule_open_btn,
            createLaunchIntent(context, 224, MRNotificationManager.ACTION_OPEN_PICKER, null)
        )
        views.setOnClickPendingIntent(
            R.id.widget_capsule_live_orb,
            createLaunchIntent(context, 225, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        return views
    }

    internal fun buildModelDeckRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_model_deck_mixed_reality)
        views.setTextViewText(R.id.widget_deck_mode_caption, "${snapshot.modeBadgeLabel} READY")
        views.setTextViewText(R.id.widget_deck_mode_pill, snapshot.activeMode.title.uppercase())
        views.setTextViewText(R.id.widget_deck_model_title, snapshot.displayTitle)
        views.setTextViewText(R.id.widget_deck_model_meta, snapshot.displayMeta)

        val selectedPreset = activePresetNameOrNull(snapshot)
        val launchActiveIntent = createLaunchIntent(
            context = context,
            requestCode = 235,
            shortcutAction = if (selectedPreset != null) MRNotificationManager.ACTION_SELECT_MODEL else null,
            modelName = selectedPreset
        )
        views.setOnClickPendingIntent(R.id.widget_deck_header, launchActiveIntent)
        views.setOnClickPendingIntent(R.id.widget_deck_showcase_tap, launchActiveIntent)

        // Carousel cycling
        views.setOnClickPendingIntent(
            R.id.widget_deck_prev_btn,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_PREV_MODEL, 236)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_next_btn,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_NEXT_MODEL, 237)
        )

        // Direct-select preset micro-chips with active Liquid Glass pill highlighting
        val chipMappings = listOf(
            R.id.widget_deck_chip_headset to (ModelItem.VISION_HEADSET to 230),
            R.id.widget_deck_chip_car to (ModelItem.TOY_CAR to 231),
            R.id.widget_deck_chip_cube to (ModelItem.HOLO_CUBE to 232),
            R.id.widget_deck_chip_orb to (ModelItem.QUANTUM_ORB to 233),
            R.id.widget_deck_chip_card to (ModelItem.SPATIAL_CARD to 234)
        )
        val usingPreset = snapshot.customModelName.isNullOrBlank() && snapshot.isModelVisible
        chipMappings.forEach { (viewId, modelAndReq) ->
            val (modelItem, reqCode) = modelAndReq
            val isSelected = usingPreset && snapshot.selectedModel == modelItem
            views.setInt(
                viewId,
                "setBackgroundResource",
                if (isSelected) R.drawable.widget_pill_selected_bg else R.drawable.widget_pill_dark_bg
            )
            views.setTextColor(
                viewId,
                if (isSelected) COLOR_TEXT_SELECTED else COLOR_TEXT_UNSELECTED
            )
            views.setOnClickPendingIntent(
                viewId,
                createPresetSelectBroadcastIntent(context, modelItem, reqCode)
            )
        }

        // Bottom dock: 3D | MR | glowing AR orb | Cloud | Open
        views.setOnClickPendingIntent(
            R.id.widget_deck_action_object,
            createLaunchIntent(context, 238, MRNotificationManager.ACTION_LAUNCH_OBJECT, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_action_mr,
            createLaunchIntent(context, 239, MRNotificationManager.ACTION_LAUNCH_MR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_action_ar,
            createLaunchIntent(context, 240, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_action_cloud,
            createLaunchIntent(context, 241, MRNotificationManager.ACTION_CLOUD_LIBRARY, null)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_action_open,
            createLaunchIntent(context, 242, MRNotificationManager.ACTION_OPEN_PICKER, null)
        )
        return views
    }

    internal fun buildMiniOrbRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_mini_orb_mixed_reality)
        views.setTextViewText(R.id.widget_orb_model_title, snapshot.displayTitle)
        views.setTextViewText(R.id.widget_orb_mode_label, "${snapshot.modeBadgeLabel} • ▶")

        val selectedPreset = activePresetNameOrNull(snapshot)
        val launchActiveIntent = createLaunchIntent(
            context = context,
            requestCode = 245,
            shortcutAction = if (selectedPreset != null) MRNotificationManager.ACTION_SELECT_MODEL else null,
            modelName = selectedPreset
        )
        views.setOnClickPendingIntent(R.id.widget_orb_launch_icon, launchActiveIntent)
        views.setOnClickPendingIntent(R.id.widget_orb_model_text_area, launchActiveIntent)

        views.setOnClickPendingIntent(
            R.id.widget_orb_next_btn,
            createBroadcastIntent(context, MixedRealityWidgetActions.ACTION_NEXT_MODEL, 246)
        )
        views.setOnClickPendingIntent(
            R.id.widget_orb_ar_button,
            createLaunchIntent(context, 247, MRNotificationManager.ACTION_LAUNCH_AR, selectedPreset)
        )
        return views
    }

    private fun applySegmentedModeHighlight(
        views: RemoteViews,
        activeMode: ViewportMode,
        mrTabId: Int,
        arTabId: Int,
        objectTabId: Int
    ) {
        val tabStates = listOf(
            mrTabId to (activeMode == ViewportMode.MR),
            arTabId to (activeMode == ViewportMode.AR),
            objectTabId to (activeMode == ViewportMode.OBJECT)
        )
        tabStates.forEach { (tabId, isSelected) ->
            views.setInt(
                tabId,
                "setBackgroundResource",
                if (isSelected) R.drawable.widget_pill_selected_bg else 0
            )
            views.setTextColor(
                tabId,
                if (isSelected) COLOR_TEXT_SELECTED else COLOR_TEXT_UNSELECTED
            )
        }
    }

    private fun activePresetNameOrNull(snapshot: WidgetSnapshot): String? {
        return if (snapshot.customModelName.isNullOrBlank()) snapshot.selectedModel.name else null
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

    private fun createPresetSelectBroadcastIntent(
        context: Context,
        model: ModelItem,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MixedRealityStudioWidgetProvider::class.java).apply {
            action = MixedRealityWidgetActions.ACTION_SELECT_PRESET
            putExtra(MixedRealityWidgetActions.EXTRA_PRESET_NAME, model.name)
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
 * 4×2 Interactive Studio Home Screen Widget with Liquid Glass tabs, preset switcher & Object/AR/MR/Open dock.
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
            MixedRealityWidgetActions.ACTION_SELECT_PRESET -> {
                val presetName = intent.getStringExtra(MixedRealityWidgetActions.EXTRA_PRESET_NAME)
                val targetModel = WidgetStateStore.resolveModelByName(presetName)
                if (targetModel != null) {
                    val updated = WidgetStateStore.selectPreset(context, targetModel)
                    MixedRealityWidgetUpdater.refreshAll(context, updated)
                }
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

/**
 * 4×1 Floating Liquid Glass Capsule Bar mirroring the in-app MR | AR | Object segmented pill & REC orb.
 */
class MixedRealityCapsuleBarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetStateStore.load(context)
        val views = MixedRealityWidgetUpdater.buildCapsuleBarRemoteViews(context, snapshot)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }
}

/**
 * 4×3 Full Liquid Glass 3D Model Showcase & Cloud Deck with direct preset chips and spatial mode dock.
 */
class MixedRealityModelDeckWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetStateStore.load(context)
        val views = MixedRealityWidgetUpdater.buildModelDeckRemoteViews(context, snapshot)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }
}

/**
 * 2×1 Compact Liquid Glass Mini Pill with model switcher and glowing AR orb launch.
 */
class MixedRealityMiniOrbWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetStateStore.load(context)
        val views = MixedRealityWidgetUpdater.buildMiniOrbRemoteViews(context, snapshot)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }
}
