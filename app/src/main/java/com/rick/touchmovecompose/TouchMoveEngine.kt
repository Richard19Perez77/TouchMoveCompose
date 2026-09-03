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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Game state and physics from the original SquareSet, driven by Compose frames
 * instead of a SurfaceView thread.
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

    private val squares = ArrayList<MovingSquare>()

    private var createSquare = false
    private var clearSquares = false
    private var touchingScreen = false
    private var disperseSquares = false

    private var newX = 0f
    private var newY = 0f

    var drawCounter by mutableIntStateOf(0)
        private set
    var touchCounter by mutableIntStateOf(0)
        private set

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
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
        createSquare = false
        clearSquares = false
        disperseSquares = false
        touchingScreen = false
        drawCounter = 0
        touchCounter = 0
        squares.clear()
    }

    fun onDown(position: Offset) {
        recordTouch(position)
        clearSquares = true
        touchingScreen = true
        drawCounter = 0
        touchCounter = 0
    }

    fun onMove(position: Offset) {
        recordTouch(position)
        createSquare = true
    }

    fun onUp() {
        touchCounter++
        touchingScreen = false
        disperseSquares = true
    }

    fun updatePhysics() {
        screenColor = Color.White
        if (createSquare) {
            createSquare = false
            spawnSquare()
        } else if (clearSquares) {
            clearSquares = false
            disperseSquares = false
            squares.clear()
        } else if (disperseSquares) {
            squares.toTypedArray().forEach { it.updatePoint() }
        }
    }

    fun draw(scope: DrawScope) {
        drawCounter++
        val formattedDate = dateFormat.format(Date())
        val snapshot = squares.toList()

        scope.drawRect(color = screenColor, size = scope.size)

        val textSize = hudPaint.textSize
        scope.drawContext.canvas.nativeCanvas.apply {
            drawText("Date: $formattedDate", 20f, textSize + 10, hudPaint)
            drawText("Squares: ${snapshot.size}", 20f, textSize * 2 + 20, hudPaint)
            drawText("Draws: $drawCounter", 20f, textSize * 3 + 30, hudPaint)
            drawText("Touches: $touchCounter", 20f, textSize * 4 + 40, hudPaint)
        }

        snapshot.forEach { square ->
            scope.drawRect(
                color = Color.Blue,
                topLeft = Offset(square.left, square.top),
                size = Size(square.width, square.height),
                style = Stroke(width = Stroke.HairlineWidth)
            )
        }
    }

    private fun recordTouch(position: Offset) {
        newX = position.x
        newY = position.y
        touchCounter++
    }

    private fun spawnSquare() {
        val x1 = newX.toInt()
        val x2 = (screenW - newX).toInt()
        val y1 = newY.toInt()
        val y2 = (screenH - newY).toInt()
        var diff = min(x1, x2)
        diff = min(diff, y1)
        diff = min(diff, y2)

        val buffer = (diff * Math.random() * SQUARE_RATIO).toInt()
        val left = (newX - buffer).toInt()
        val top = (newY - buffer).toInt()
        val right = (newX + buffer).toInt()
        val bottom = (newY + buffer).toInt()
        if (touchingScreen) {
            squares.add(
                MovingSquare(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    bottom.toFloat()
                )
            )
        }
    }

    private inner class MovingSquare(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

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
        const val SQUARE_RATIO = 0.2
        private const val HUD_TEXT_SIZE = 35f
    }
}
