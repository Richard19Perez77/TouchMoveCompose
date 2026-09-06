package com.rick.touchmovecompose

import android.graphics.Point
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.min

/**
 * Cube set: isometric cubes, touch flags, and HUD, ticked from Compose vsync.
 *
 * Pointer handlers only set flags. [updatePhysics] applies one of spawn, clear,
 * or disperse per frame (in that priority), matching the original if-else.
 */
class TouchMoveEngine {
    var isRunning by mutableStateOf(false)
        private set

    var overlayMessage by mutableStateOf("")
        private set

    val overlayVisible: Boolean
        get() = !isRunning && overlayMessage.isNotEmpty()

    private var screenColor: Color = Color.Blue
    private var screenW = 0
    private var screenH = 0

    private val cubes = ArrayList<MovingCube>()

    // One-shot / sticky flags consumed by [updatePhysics] (spawn > clear > fly).
    private var createCube = false
    private var clearCubes = false
    private var touchingScreen = false
    private var disperseCubes = false

    private var newX = 0f
    private var newY = 0f

    var drawCounter by mutableIntStateOf(0)
        private set
    var touchCounter by mutableIntStateOf(0)
        private set

    private val plot = PlotPoints()
    private val hudPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        textSize = HUD_TEXT_SIZE
        isAntiAlias = true
    }

    fun setSize(width: Int, height: Int) {
        screenW = width
        screenH = height
    }

    fun resumeFromTap() {
        isRunning = true
        overlayMessage = ""
    }

    fun pauseFromLifecycle(message: String) {
        isRunning = false
        overlayMessage = message
    }

    fun restart(tapMessage: String) {
        isRunning = false
        overlayMessage = tapMessage
        screenColor = Color.Blue
        createCube = false
        clearCubes = false
        disperseCubes = false
        touchingScreen = false
        drawCounter = 0
        touchCounter = 0
        cubes.clear()
    }

    /** New stroke: wipe the board on the next tick that is not a spawn. */
    fun onDown(position: Offset) {
        recordTouch(position)
        clearCubes = true
        touchingScreen = true
        drawCounter = 0
        touchCounter = 0
    }

    /** Request one cube at this point on the next physics tick. */
    fun onMove(position: Offset) {
        recordTouch(position)
        createCube = true
    }

    /** Stop spawning; remaining cubes walk their [PlotPoints] paths. */
    fun onUp() {
        touchCounter++
        touchingScreen = false
        disperseCubes = true
    }

    fun updatePhysics() {
        screenColor = Color.White
        if (createCube) {
            // Drag wins: spawn even if clearCubes is still set from onDown.
            createCube = false
            spawnCube()
        } else if (clearCubes) {
            clearCubes = false
            disperseCubes = false
            cubes.clear()
        } else if (disperseCubes) {
            cubes.toTypedArray().forEach { it.updatePoint() }
        }
    }

    fun draw(scope: DrawScope, telemetry: PerformanceTelemetry) {
        drawCounter++
        val snapshot = cubes.toList()

        scope.drawRect(color = screenColor, size = scope.size)

        // Painter's algorithm: lower cubes overlap higher ones.
        snapshot
            .sortedBy { it.top + it.left }
            .forEach { cube ->
                scope.drawIsometricCube(cube.left, cube.top, cube.size)
            }

        // Telemetry last so cubes never cover it.
        val textSize = hudPaint.textSize
        val lineGap = textSize + 8f
        val hudLines = 5
        val hudHeight = lineGap * hudLines + 10f
        scope.drawRect(
            color = Color.White.copy(alpha = 0.88f),
            topLeft = Offset.Zero,
            size = Size(scope.size.width, hudHeight)
        )
        var y = textSize + 6f
        scope.drawContext.canvas.nativeCanvas.apply {
            fun hudLine(text: String) {
                drawText(text, 20f, y, hudPaint)
                y += lineGap
            }
            hudLine("Cubes: ${snapshot.size}  Draws: $drawCounter  Touches: $touchCounter")
            hudLine(
                "FPS: ${telemetry.fps.format1()}  " +
                    "Frame: ${telemetry.frameMs.format1()}ms  " +
                    "Jank: ${telemetry.jankCount}  " +
                    "${telemetry.refreshHz.format0()}Hz"
            )
            hudLine(
                "CPU: ${telemetry.cpuPercent.format0()}% of " +
                    "${telemetry.cpuCores} cores  " +
                    "UI: ${telemetry.uiCpuPercent.format0()}%"
            )
            hudLine(
                "Heap: ${telemetry.heapUsedMb.format0()}/" +
                    "${telemetry.heapMaxMb.format0()} MB  " +
                    "Native: ${telemetry.nativeHeapMb.format0()} MB"
            )
            hudLine(
                "PSS: ${telemetry.pssMb.format0()} MB  " +
                    "Thermal: ${telemetry.thermalLabel}"
            )
        }
    }

    private fun recordTouch(position: Offset) {
        newX = position.x
        newY = position.y
        touchCounter++
    }

    private fun spawnCube() {
        // Half-size is a random fraction of the shortest distance to an edge.
        val x1 = newX.toInt()
        val x2 = (screenW - newX).toInt()
        val y1 = newY.toInt()
        val y2 = (screenH - newY).toInt()
        var diff = min(x1, x2)
        diff = min(diff, y1)
        diff = min(diff, y2)

        val buffer = (diff * Math.random() * CUBE_RATIO).toInt()
        val left = (newX - buffer).toInt()
        val top = (newY - buffer).toInt()
        val right = (newX + buffer).toInt()
        val bottom = (newY + buffer).toInt()
        if (touchingScreen) {
            cubes.add(
                MovingCube(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    bottom.toFloat()
                )
            )
        }
    }

    /** Isometric cube that, after lift, steps along a random on-screen line. */
    private inner class MovingCube(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float
    ) {
        val size: Float get() = min(right - left, bottom - top)

        private val pointLine: Array<Point?>
        private var currentPoint = 0

        init {
            val origin = Point(left.toInt(), top.toInt())
            val destination = Point(
                (screenW.toFloat() * Math.random()).toInt(),
                (screenH.toFloat() * Math.random()).toInt()
            )
            pointLine = plot.plotLine(origin, destination)
        }

        fun updatePoint() {
            currentPoint++
            if (currentPoint < pointLine.size) {
                val next = pointLine[currentPoint] ?: return
                if (next.x > 0 && next.y > 0) {
                    val width = right - left
                    val height = bottom - top
                    left = next.x.toFloat()
                    top = next.y.toFloat()
                    right = next.x + width
                    bottom = next.y + height
                }
            }
        }
    }

    companion object {
        /** Spawn half-size vs. shortest edge distance (0 = tiny, 1 = to the edge). */
        const val CUBE_RATIO = 0.2
        private const val HUD_TEXT_SIZE = 34f
    }
}

private fun Float.format0(): String = String.format(java.util.Locale.US, "%.0f", this)

private fun Float.format1(): String = String.format(java.util.Locale.US, "%.1f", this)
