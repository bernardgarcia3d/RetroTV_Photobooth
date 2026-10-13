package com.eventphotobooth.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eventphotobooth.app.ui.EventPhotoboothApp
import com.eventphotobooth.app.ui.BoothLightColors
import com.eventphotobooth.app.ui.BoothShapes
import com.eventphotobooth.app.ui.BoothTypography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(
                colorScheme = BoothLightColors,
                typography = BoothTypography,
                shapes = BoothShapes,
            ) {
                EventPhotoboothApp()
            }
        }
    }
}
