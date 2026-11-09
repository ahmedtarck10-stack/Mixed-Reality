package com.aistudio.mixedreality.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.aistudio.mixedreality.R
import kotlinx.coroutines.delay

/**
 * Animated Splash Screen shown immediately on app launch.
 * Displays the Vision headset icon and "DEVELOPED BY AHMED TARCK" branding
 * on pure black background with gentle zoom/fade before transitioning to the UI.
 * Tapping anywhere allows the user to immediately proceed without waiting.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.94f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2300L)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("splash_screen_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_screen_art),
            contentDescription = "Developed by Ahmed Tarek",
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .testTag("splash_screen_image"),
            contentScale = ContentScale.Fit
        )
    }
}
