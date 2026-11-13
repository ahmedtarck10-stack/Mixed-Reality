package com.aistudio.mixedreality

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.aistudio.mixedreality.ui.MRMainScreen
import com.aistudio.mixedreality.ui.SplashScreen
import com.aistudio.mixedreality.ui.theme.MixedRealityTheme

class MainActivity : ComponentActivity() {
    private var incomingModelUri by mutableStateOf<Uri?>(null)
    private var shortcutAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enforce 30Hz display refresh rate strictly to eliminate sensor timestamp jitter and drifting
        enforce30HzRefreshRate()

        incomingModelUri = extractModelUri(intent)
        shortcutAction = intent?.getStringExtra("shortcut_action")
        com.aistudio.mixedreality.util.MRNotificationManager.ensureChannels(this)

        setContent {
            MixedRealityTheme {
                val systemDensity = LocalDensity.current
                val fixedDensity = remember(systemDensity.density, systemDensity.fontScale) {
                    Density(
                        density = systemDensity.density,
                        fontScale = systemDensity.fontScale
                    )
                }

                CompositionLocalProvider(
                    LocalDensity provides fixedDensity,
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        var showSplash by remember { mutableStateOf(incomingModelUri == null && shortcutAction == null) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            MRMainScreen(
                                incomingModelUri = incomingModelUri,
                                onModelHandled = { incomingModelUri = null },
                                shortcutAction = shortcutAction,
                                onShortcutHandled = { shortcutAction = null }
                            )

                            if (showSplash) {
                                AnimatedVisibility(
                                    visible = showSplash,
                                    enter = fadeIn(tween(300)),
                                    exit = fadeOut(tween(500))
                                ) {
                                    SplashScreen(
                                        onFinished = { showSplash = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractModelUri(intent)?.let { uri ->
            incomingModelUri = uri
        }
        intent.getStringExtra("shortcut_action")?.let { action ->
            shortcutAction = action
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            com.aistudio.mixedreality.util.MRNotificationManager.cancelSessionNotification(this)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        enforce30HzRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enforce30HzRefreshRate()
        }
    }

    private fun enforce30HzRefreshRate() {
        // Enforce display refresh rate preference strictly to 30.0f Hz to eliminate sensor jitter
        try {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 30.0f
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val currentDisplay = display
                val modes = currentDisplay?.supportedModes.orEmpty()
                // Pick 30Hz native mode if available on variable refresh rate / LTPO panels.
                // Strictly prioritize 30Hz without forcing 60Hz.
                val mode30 = modes.firstOrNull { kotlin.math.abs(it.refreshRate - 30.0f) < 1.0f }
                if (mode30 != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = mode30.modeId
                        preferredRefreshRate = 30.0f
                    }
                }
            }
        } catch (_: Throwable) {
            // Gracefully ignore on platforms where refresh rate override is restricted
        }
    }

    private fun extractModelUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.data
            }
            else -> intent.data
        }
    }
}
