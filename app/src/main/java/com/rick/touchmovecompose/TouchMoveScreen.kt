package com.rick.touchmovecompose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.rick.touchmovecompose.ui.theme.EmptySlotBorder
import com.rick.touchmovecompose.ui.theme.HudScrim
import com.rick.touchmovecompose.ui.theme.MikuLooks
import com.rick.touchmovecompose.ui.theme.TouchMoveComposeTheme
import kotlinx.coroutines.delay

/**
 * Toolbar, overlay, and drawing canvas.
 *
 * The old SurfaceView thread is a [withFrameNanos] loop: each vsync ticks
 * [TouchMoveEngine.updatePhysics], then [frameNanos] invalidates the Canvas.
 *
 * With targetSdk 37, the window is edge-to-edge. Material3 [Scaffold] applies
 * system-bar insets as [innerPadding] so the TopAppBar and canvas sit below the
 * status bar / above the nav bar while the theme can still draw into those areas.
 */
@Composable
fun TouchMoveScreen(modifier: Modifier = Modifier) {
    val tapMessage = stringResource(R.string.tap_to_start)
    val pauseMessage = stringResource(R.string.message_text)
    val context = LocalContext.current
    val engine = remember(tapMessage) {
        TouchMoveEngine().also { it.restart(tapMessage) }
    }
    val telemetry = remember(context) { PerformanceTelemetry(context) }
    val look = MikuLooks[engine.lookIndex]
    MaterialTheme(colorScheme = look.colorScheme()) {
        TouchMovePlayfield(
            modifier = modifier,
            engine = engine,
            telemetry = telemetry,
            tapMessage = tapMessage,
            pauseMessage = pauseMessage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TouchMovePlayfield(
    modifier: Modifier,
    engine: TouchMoveEngine,
    telemetry: PerformanceTelemetry,
    tapMessage: String,
    pauseMessage: String,
) {
    val colors = MaterialTheme.colorScheme

    var introFinished by remember { mutableStateOf(false) }
    // Read inside Canvas so a vsync without other state still redraws.
    var frameNanos by remember { mutableLongStateOf(0L) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(Unit) {
        delay(320)
        introFinished = true
    }

    val pulse = rememberInfiniteTransition(label = "overlayPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "overlayPulseScale"
    )

    // Pause when the activity is not resumed; otherwise run the vsync loop.
    LaunchedEffect(isResumed, engine) {
        if (!isResumed) {
            engine.pauseFromLifecycle(pauseMessage)
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                if (engine.statsEnabled) {
                    telemetry.onFrame(nanos, limitersEnabled = true)
                }
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
                        engine.toggleStats()
                        if (!engine.statsEnabled) {
                            telemetry.clearLimiters()
                        }
                    }) {
                        Text(
                            text = stringResource(R.string.action_stats),
                            fontWeight = if (engine.statsEnabled) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
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
                                // First tap leaves the idle overlay; the rest of
                                // this gesture still paints cubes.
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

            if (engine.statsEnabled) {
                frameNanos
                StatsHud(
                    engine = engine,
                    telemetry = telemetry,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            AnimatedVisibility(
                visible = engine.overlayVisible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(520, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(520, easing = FastOutSlowInEasing),
                        initialScale = 0.88f
                    ) +
                    slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 5 },
                exit = fadeOut(tween(180)) +
                    scaleOut(tween(180), targetScale = 1.08f) +
                    slideOutVertically(tween(180)) { -it / 8 }
            ) {
                Text(
                    text = engine.overlayMessage,
                    color = colors.tertiary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(HudScrim, RoundedCornerShape(10.dp))
                        .border(1.dp, EmptySlotBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
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
