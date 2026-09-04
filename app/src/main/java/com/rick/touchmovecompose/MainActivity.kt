package com.rick.touchmovecompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.rick.touchmovecompose.ui.theme.TouchMoveComposeTheme

/** Hosts [TouchMoveScreen]; there is no XML layout or SurfaceView. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 37 (and 35+) already draws behind system bars on Android 15+.
        // enableEdgeToEdge() mirrors that on older devices (minSdk 24) and sets
        // transparent/scrimmed bars; Scaffold.innerPadding then insets content.
        enableEdgeToEdge()
        setContent {
            TouchMoveComposeTheme {
                TouchMoveScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
