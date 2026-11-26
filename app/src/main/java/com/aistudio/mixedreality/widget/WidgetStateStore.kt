package com.aistudio.mixedreality.widget

import android.content.Context
import com.aistudio.mixedreality.ui.ModelItem
import com.aistudio.mixedreality.ui.ViewportMode

/**
 * Immutable snapshot of Mixed Reality state shared between the live app and home screen widgets.
 */
data class WidgetSnapshot(
    val selectedModel: ModelItem = ModelItem.VISION_HEADSET,
    val activeMode: ViewportMode = ViewportMode.OBJECT,
    val customModelName: String? = null,
    val customStatsSummary: String? = null,
    val isModelVisible: Boolean = true,
    val isRecording: Boolean = false
) {
    val displayTitle: String
        get() = when {
            !isModelVisible -> "Empty Scene"
            !customModelName.isNullOrBlank() -> customModelName
            else -> selectedModel.title
        }

    val displayMeta: String
        get() = when {
            isRecording -> "Recording Active • Tap to control"
            !isModelVisible -> "Tap Open 3D or cycle preset"
            !customModelName.isNullOrBlank() -> {
                customStatsSummary?.takeIf { it.isNotBlank() } ?: "Custom 3D Asset • Tap to inspect"
            }
            else -> {
                val index = ModelItem.entries.indexOf(selectedModel) + 1
                val total = ModelItem.entries.size
                "$index / $total • ${selectedModel.subtitle}"
            }
        }

    val modeBadgeLabel: String
        get() = when (activeMode) {
            ViewportMode.OBJECT -> "3D STUDIO"
            ViewportMode.AR -> "AR VIEW"
            ViewportMode.MR -> "MR STEREO"
        }

    val compactModeLabel: String
        get() = when (activeMode) {
            ViewportMode.OBJECT -> "3D"
            ViewportMode.AR -> "AR"
            ViewportMode.MR -> "MR"
        }
}

/**
 * Persists and cycles home-screen widget state using SharedPreferences so ◀ / ▶ buttons
 * work interactively outside the app and automatically sync when the user opens a 3D file.
 */
object WidgetStateStore {
    private const val PREFS_NAME = "mr_widget_prefs"
    private const val KEY_MODEL_NAME = "selected_model"
    private const val KEY_ACTIVE_MODE = "active_mode"
    private const val KEY_CUSTOM_NAME = "custom_model_name"
    private const val KEY_CUSTOM_STATS = "custom_stats_summary"
    private const val KEY_MODEL_VISIBLE = "is_model_visible"
    private const val KEY_RECORDING = "is_recording"

    fun load(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modelName = prefs.getString(KEY_MODEL_NAME, ModelItem.VISION_HEADSET.name)
        val modeName = prefs.getString(KEY_ACTIVE_MODE, ViewportMode.OBJECT.name)
        val selectedModel = ModelItem.entries.firstOrNull { it.name == modelName }
            ?: ModelItem.VISION_HEADSET
        val activeMode = ViewportMode.entries.firstOrNull { it.name == modeName }
            ?: ViewportMode.OBJECT

        return WidgetSnapshot(
            selectedModel = selectedModel,
            activeMode = activeMode,
            customModelName = prefs.getString(KEY_CUSTOM_NAME, null),
            customStatsSummary = prefs.getString(KEY_CUSTOM_STATS, null),
            isModelVisible = prefs.getBoolean(KEY_MODEL_VISIBLE, true),
            isRecording = prefs.getBoolean(KEY_RECORDING, false)
        )
    }

    fun saveFromApp(
        context: Context,
        selectedModel: ModelItem,
        activeMode: ViewportMode,
        customModelName: String?,
        customStatsSummary: String?,
        isModelVisible: Boolean,
        isRecording: Boolean
    ): WidgetSnapshot {
        val snapshot = WidgetSnapshot(
            selectedModel = selectedModel,
            activeMode = activeMode,
            customModelName = customModelName,
            customStatsSummary = customStatsSummary,
            isModelVisible = isModelVisible,
            isRecording = isRecording
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_NAME, snapshot.selectedModel.name)
            .putString(KEY_ACTIVE_MODE, snapshot.activeMode.name)
            .putString(KEY_CUSTOM_NAME, snapshot.customModelName)
            .putString(KEY_CUSTOM_STATS, snapshot.customStatsSummary)
            .putBoolean(KEY_MODEL_VISIBLE, snapshot.isModelVisible)
            .putBoolean(KEY_RECORDING, snapshot.isRecording)
            .apply()
        return snapshot
    }

    /**
     * Pure preset carousel cycling logic (directly unit-testable).
     */
    fun cycleModel(current: ModelItem, delta: Int): ModelItem {
        val entries = ModelItem.entries
        val currentIdx = entries.indexOf(current).coerceAtLeast(0)
        val nextIdx = ((currentIdx + delta) % entries.size + entries.size) % entries.size
        return entries[nextIdx]
    }

    fun resolveModelByName(name: String?): ModelItem? {
        if (name.isNullOrBlank()) return null
        return ModelItem.entries.firstOrNull { it.name == name }
    }

    fun selectPreset(context: Context, model: ModelItem): WidgetSnapshot {
        val current = load(context)
        return saveFromApp(
            context = context,
            selectedModel = model,
            activeMode = current.activeMode,
            customModelName = null,
            customStatsSummary = null,
            isModelVisible = true,
            isRecording = current.isRecording
        )
    }

    fun stepPreset(context: Context, delta: Int): WidgetSnapshot {
        val current = load(context)
        val nextModel = cycleModel(current.selectedModel, delta)
        return saveFromApp(
            context = context,
            selectedModel = nextModel,
            activeMode = current.activeMode,
            customModelName = null,
            customStatsSummary = null,
            isModelVisible = true,
            isRecording = current.isRecording
        )
    }
}
