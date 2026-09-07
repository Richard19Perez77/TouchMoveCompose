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
import androidx.compose.ui.graphics.toArgb
import com.rick.touchmovecompose.ui.theme.MikuAccent
import com.rick.touchmovecompose.ui.theme.MikuNight
import kotlin.math.min

/**
 * Cube set: isometric cubes, touch flags, and HUD, ticked from Compose vsync.
 *
 * Pointer handlers only set flags. [updatePhysics] may clear, spawn, and
 * step every cube on the same tick so motion starts as soon as a cube exists.
 */
class TouchMoveEngine {
    var isRunning by mutableStateOf(false)
        private set

    var overlayMessage by mutableStateOf("")
        private set

    val overlayVisible: Boolean
        get() = !isRunning && overlayMessage.isNotEmpty()

    private var screenColor: Color = MikuNight
    private var hudScrim: Color = MikuNight.copy(alpha = 0.88f)
    private var screenW = 0
    private var screenH = 0

    private val cubes = ArrayList<MovingCube>()
    private val cubeBatch = CubeBatch()

    // Flags consumed by [updatePhysics] each vsync.
    private var createCube = false
    private var clearCubes = false
    private var touchingScreen = false

    private var newX = 0f
    private var newY = 0f

    var drawCounter by mutableIntStateOf(0)
        private set
    var touchCounter by mutableIntStateOf(0)
        private set

    private val plot = PlotPoints()
    private val hudPaint = android.graphics.Paint().apply {
        color = MikuAccent.toArgb()
        textSize = HUD_TEXT_SIZE
        isAntiAlias = true
    }

    fun applyPalette(background: Color, primary: Color, accent: Color) {
        screenColor = background
        hudScrim = background.copy(alpha = 0.88f)
        hudPaint.color = accent.toArgb()
        cubeBatch.setPrimaryColor(primary.toArgb())
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
        createCube = false
        clearCubes = false
        touchingScreen = false
        drawCounter = 0
        touchCounter = 0
        cubes.clear()
    }

    /** New stroke: wipe the board, then spawn at this point on the next tick. */
    fun onDown(position: Offset) {
        recordTouch(position)
        clearCubes = true
        createCube = true
        touchingScreen = true
        drawCounter = 0
        touchCounter = 0
    }

    /** Request another cube at this point on the next physics tick. */
    fun onMove(position: Offset) {
        recordTouch(position)
        createCube = true
    }

    /** Stop spawning; cubes already in flight keep walking their paths. */
    fun onUp() {
        touchCounter++
        touchingScreen = false
    }

    fun updatePhysics(telemetry: PerformanceTelemetry) {
        if (clearCubes) {
            clearCubes = false
            cubes.clear()
            telemetry.onSceneCleared()
        }
        if (createCube) {
            createCube = false
            if (!telemetry.holdSpawn) {
                spawnCube()
            }
        }
        for (i in cubes.indices) {
            cubes[i].updatePoint()
        }
    }

    fun draw(scope: DrawScope, telemetry: PerformanceTelemetry) {
        drawCounter++

        scope.drawRect(color = screenColor, size = scope.size)

        val minSize = if (telemetry.skipSmallCubes) {
            PerformanceTelemetry.LOD_MIN_SIZE
        } else {
            1f
        }
        // In-place painter's sort: lower cubes overlap higher ones.
        cubes.sortBy { it.top + it.left }
        cubeBatch.begin()
        for (i in cubes.indices) {
            val cube = cubes[i]
            if (cube.size >= minSize) {
                cubeBatch.addCube(cube.left, cube.top, cube.size)
            }
        }
        cubeBatch.draw(scope.drawContext.canvas.nativeCanvas)

        // Telemetry last so cubes never cover it.
        val textSize = hudPaint.textSize
        val lineGap = textSize + 8f
        val hudLines = 5
        val hudHeight = lineGap * hudLines + 10f
        scope.drawRect(
            color = hudScrim,
            topLeft = Offset.Zero,
            size = Size(scope.size.width, hudHeight)
        )
        var y = textSize + 6f
        scope.drawContext.canvas.nativeCanvas.apply {
            fun hudLine(text: String) {
                drawText(text, 20f, y, hudPaint)
                y += lineGap
            }
            hudLine(
                "Cubes: ${cubes.size} (${cubeBatch.cubeCount} drawn)  " +
                    "Draws: $drawCounter  Touches: $touchCounter"
            )
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
                    "Thermal: ${telemetry.thermalLabel}  " +
                    telemetry.budgetLabel
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

    /** Isometric cube that walks a random on-screen line as soon as it exists. */
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
        private const val HUD_TEXT_SIZE = 40f
    }
}

private fun Float.format0(): String = String.format(java.util.Locale.US, "%.0f", this)

private fun Float.format1(): String = String.format(java.util.Locale.US, "%.1f", this)
