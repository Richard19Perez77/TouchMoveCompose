package com.rick.touchmovecompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.rick.touchmovecompose.ui.theme.TouchMoveComposeTheme

/** Same curve as the original XML accelerate interpolator (t²). */
private val AccelerateEasing = Easing { fraction -> fraction * fraction }

/**
 * Toolbar, intro scale, overlay, and drawing canvas.
 *
 * The old SurfaceView thread is a [withFrameNanos] loop: each vsync ticks
 * [TouchMoveEngine.updatePhysics], then [frameNanos] invalidates the Canvas.
 *
 * With targetSdk 37, the window is edge-to-edge. Material3 [Scaffold] applies
 * system-bar insets as [innerPadding] so the TopAppBar and canvas sit below the
 * status bar / above the nav bar while the theme can still draw into those areas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchMoveScreen(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tapMessage = stringResource(R.string.tap_to_start)
    val pauseMessage = stringResource(R.string.message_text)
    val context = LocalContext.current
    val engine = remember(tapMessage) {
        TouchMoveEngine().also { it.restart(tapMessage) }
    }
    val telemetry = remember(context) { PerformanceTelemetry(context) }
    SideEffect {
        engine.applyPalette(
            background = colors.secondary,
            primary = colors.primary,
            accent = colors.tertiary,
        )
    }

    var introFinished by remember { mutableStateOf(false) }
    // Read inside Canvas so a vsync without other state still redraws.
    var frameNanos by remember { mutableLongStateOf(0L) }
    val introScale = remember { Animatable(0f) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(Unit) {
        introScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = AccelerateEasing)
        )
        introFinished = true
    }

    // Pause when the activity is not resumed; otherwise run the vsync loop.
    LaunchedEffect(isResumed, engine) {
        if (!isResumed) {
            engine.pauseFromLifecycle(pauseMessage)
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                telemetry.onFrame(nanos)
                if (engine.isRunning) {
                    engine.updatePhysics(telemetry)
                }
                frameNanos = nanos
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_touch_move)) },
                actions = {
                    TextButton(onClick = {
                        engine.restart(tapMessage)
                        telemetry.reset()
                    }) {
                        Text(stringResource(R.string.action_restart))
                    }
                }
            )
        }
    ) { innerPadding ->
        // System-bar insets from Scaffold (required once the app is edge-to-edge).
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .scale(introScale.value)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size -> engine.setSize(size.width, size.height) }
                    .pointerInput(introFinished, engine) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (!introFinished) return@awaitEachGesture
                            if (!engine.isRunning) {
                                // First tap leaves the blue overlay; the rest of
                                // this gesture still spawns and then disperses.
                                engine.resumeFromTap()
                                drag(down.id) { change ->
                                    engine.onMove(change.position)
                                    change.consume()
                                }
                                engine.onUp()
                            } else {
                                engine.onDown(down.position)
                                drag(down.id) { change ->
                                    engine.onMove(change.position)
                                    change.consume()
                                }
                                engine.onUp()
                            }
                        }
                    }
            ) {
                frameNanos
                engine.draw(this, telemetry)
            }

            if (engine.overlayVisible) {
                Text(
                    text = engine.overlayMessage,
                    color = colors.tertiary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(5.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TouchMoveScreenPreview() {
    TouchMoveComposeTheme {
        TouchMoveScreen()
    }
}
