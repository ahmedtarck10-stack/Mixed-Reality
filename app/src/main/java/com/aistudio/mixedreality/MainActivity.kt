package com.aistudio.mixedreality

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aistudio.mixedreality.ui.MRMainScreen
import com.aistudio.mixedreality.ui.theme.MixedRealityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixedRealityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MRMainScreen()
                }
            }
        }
    }
}
