package com.aistudio.mixedreality

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixedRealityTheme {
                val systemDensity = LocalDensity.current
                // Enforce constant 1.0f fontScale so accessibility font sizes do not break layout
                val fixedDensity = remember(systemDensity.density) {
                    Density(
                        density = systemDensity.density,
                        fontScale = 1.0f
                    )
                }

                // Enforce strictly LTR layout direction so Arabic/RTL language settings never flip the UI
                CompositionLocalProvider(
                    LocalDensity provides fixedDensity,
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        var showSplash by remember { mutableStateOf(true) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            MRMainScreen()

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
