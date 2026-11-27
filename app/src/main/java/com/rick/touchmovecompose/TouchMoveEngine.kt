package com.rick.touchmovecompose

import android.graphics.Point
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.rick.touchmovecompose.ui.theme.MikuLooks
import com.rick.touchmovecompose.ui.theme.MikuNight
import kotlin.math.min

/**
 * Cube set: isometric cubes, touch flags, and HUD, ticked from Compose vsync.
 *
 * Pointer handlers only set flags. Dragging paints cubes; each one walks a
 * random on-screen path as soon as it exists. One press is one set: wipe the
 * last list, pick a look, then paint until lift.
 */
class TouchMoveEngine {
    var isRunning by mutableStateOf(false)
        private set

    var overlayMessage by mutableStateOf("")
        private set

    val overlayVisible: Boolean
        get() = !isRunning && overlayMessage.isNotEmpty()

    private var screenColor: Color = MikuNight
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
    var cubeCount by mutableIntStateOf(0)
        private set
    var drawnCount by mutableIntStateOf(0)
        private set

    private val plot = PlotPoints()

    var lookIndex by mutableIntStateOf(0)
        private set

    var statsEnabled by mutableStateOf(false)
        private set

    private val pendingSpawns = ArrayList<Offset>()
    private var strokeArgb = MikuLooks[0].primary.toArgb()

    fun applyPalette(background: Color, primary: Color, accent: Color) {
        screenColor = background
        cubeBatch.setPrimaryColor(primary.toArgb())
    }

    fun setSize(width: Int, height: Int) {
        screenW = width
        screenH = height
    }

    fun resumeFromTap() {
        isRunning = true
        overlayMessage = ""
        touchingScreen = true
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
        cubeCount = 0
        drawnCount = 0
        cubes.clear()
        pendingSpawns.clear()
        lookIndex = 0
        applyCurrentLook()
    }

    fun toggleStats() {
        statsEnabled = !statsEnabled
        if (!statsEnabled) {
            pendingSpawns.clear()
        }
    }

    fun nextLook() {
        lookIndex = (lookIndex + 1) % MikuLooks.size
        applyCurrentLook()
    }

    private fun applyCurrentLook() {
        val look = MikuLooks[lookIndex]
        strokeArgb = look.primary.toArgb()
        applyPalette(look.background, look.primary, look.accent)
    }

    /** New press: wipe the last set, pick a look, start a fresh list. */
    fun onDown(position: Offset) {
        recordTouch(position)
        clearCubes = true
        createCube = false
        pendingSpawns.clear()
        touchingScreen = true
        nextLook()
        requestSpawn(position)
    }

    /** Request another cube at this point on the next physics tick. */
    fun onMove(position: Offset) {
        recordTouch(position)
        requestSpawn(position)
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
        if (statsEnabled) {
            pendingSpawns.clear()
            if (createCube) {
                createCube = false
                if (!telemetry.holdSpawn) {
                    spawnCube()
                }
            }
        } else {
            createCube = false
            for (i in pendingSpawns.indices) {
                val point = pendingSpawns[i]
                newX = point.x
                newY = point.y
                spawnCube()
            }
            pendingSpawns.clear()
        }
        for (i in cubes.indices) {
            cubes[i].updatePoint()
        }
    }

    fun draw(scope: DrawScope, telemetry: PerformanceTelemetry) {
        drawCounter++

        scope.drawRect(color = screenColor, size = scope.size)

        val minSize = if (statsEnabled && telemetry.skipSmallCubes) {
            PerformanceTelemetry.LOD_MIN_SIZE
        } else {
            1f
        }
        cubes.sortBy { it.top + it.left }
        cubeBatch.begin()
        for (i in cubes.indices) {
            val cube = cubes[i]
            if (cube.size >= minSize) {
                cubeBatch.addCube(cube.left, cube.top, cube.size, cube.primaryArgb)
            }
        }
        cubeBatch.draw(scope.drawContext.canvas.nativeCanvas)
        cubeCount = cubes.size
        drawnCount = cubeBatch.cubeCount
    }

    private fun requestSpawn(position: Offset) {
        if (statsEnabled) {
            createCube = true
        } else {
            pendingSpawns.add(position)
        }
    }

    private fun recordTouch(position: Offset) {
        newX = position.x
        newY = position.y
        touchCounter++
    }

    private fun spawnCube() {
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
                    bottom.toFloat(),
                    strokeArgb
                )
            )
        }
    }

    /** Isometric cube that walks a random on-screen line as soon as it exists. */
    private inner class MovingCube(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        val primaryArgb: Int
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
    }
}
