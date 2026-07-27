package com.vamshi.field

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.vamshi.field.ui.navigation.ALearningNavGraph
import com.vamshi.field.ui.navigation.AdaptiveNavigationWrapper
import com.vamshi.field.ui.theme.FieldTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate started")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "super.onCreate finished")
        enableEdgeToEdge()
        setContent {
            Log.d("MainActivity", "setContent started")
            FieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Log.d("MainActivity", "Surface composed")
                    val navController = rememberNavController()
                    AdaptiveNavigationWrapper(navController = navController) { modifier ->
                        ALearningNavGraph(
                            navController = navController,
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
}
