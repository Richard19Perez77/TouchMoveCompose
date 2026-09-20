package com.rick.touchmovecompose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.rick.touchmovecompose.ui.theme.MikuAccent
import com.rick.touchmovecompose.ui.theme.MikuNight
import com.rick.touchmovecompose.ui.theme.MikuTeal
import com.rick.touchmovecompose.ui.theme.MikuTealDark
import com.rick.touchmovecompose.ui.theme.MikuTealLight
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cube set: isometric cubes, touch flags, and HUD, ticked from Compose vsync.
 *
 * Cubes drift toward a growing core at the screen center and merge into it.
 * A tap recolors the cube, splits it into four, and pushes those pieces apart
 * before they join the core again.
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
    private val collisionGrid = HashMap<Int, ArrayList<MovingCube>>()

    private var splitAtTouch = false
    private var newX = 0f
    private var newY = 0f
    private var spawnColorIndex = 0

    var drawCounter by mutableIntStateOf(0)
        private set
    var touchCounter by mutableIntStateOf(0)
        private set
    var cubeCount by mutableIntStateOf(0)
        private set
    var drawnCount by mutableIntStateOf(0)
        private set

    fun applyPalette(background: Color, primary: Color, accent: Color) {
        screenColor = background
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
        splitAtTouch = false
        spawnColorIndex = 0
        drawCounter = 0
        touchCounter = 0
        cubeCount = 0
        drawnCount = 0
        cubes.clear()
        collisionGrid.clear()
    }

    /** Split (or seed) at this point on the next tick. Drag does not spawn. */
    fun onDown(position: Offset) {
        recordTouch(position)
        splitAtTouch = true
    }

    fun onMove(position: Offset) {
        recordTouch(position)
    }

    fun onUp() {
        touchCounter++
    }

    fun updatePhysics() {
        if (splitAtTouch) {
            splitAtTouch = false
            handleTouch()
        }
        for (i in cubes.indices) {
            cubes[i].step()
        }
        mergeIntoCore()
        resolveCollisions()
    }

    fun draw(scope: DrawScope) {
        drawCounter++

        scope.drawRect(color = screenColor, size = scope.size)

        cubes.sortBy { it.top + it.left }
        cubeBatch.begin()
        for (i in cubes.indices) {
            val cube = cubes[i]
            cubeBatch.addCube(cube.left, cube.top, cube.size, cube.primaryArgb)
        }
        cubeBatch.draw(scope.drawContext.canvas.nativeCanvas)
        cubeCount = cubes.size
        drawnCount = cubeBatch.cubeCount
    }

    private fun recordTouch(position: Offset) {
        newX = position.x
        newY = position.y
        touchCounter++
    }

    private fun handleTouch() {
        val hit = cubeNear(newX, newY)
        if (hit == null) {
            seedCube(newX, newY)
            return
        }
        hit.recolor()
        if (hit.size < MIN_SPLIT) {
            hit.nudgeFromCenter()
            return
        }
        val pieces = hit.splitIntoFour()
        cubes.remove(hit)
        cubes.addAll(pieces)
    }

    private fun cubeNear(x: Float, y: Float): MovingCube? {
        var best: MovingCube? = null
        var bestDist = SPLIT_HIT_SLOP
        for (i in cubes.indices) {
            val cube = cubes[i]
            val dist = cube.distanceTo(x, y)
            if (dist < bestDist) {
                bestDist = dist
                best = cube
            }
        }
        return best
    }

    private fun seedCube(x: Float, y: Float) {
        val x1 = x.toInt()
        val x2 = (screenW - x).toInt()
        val y1 = y.toInt()
        val y2 = (screenH - y).toInt()
        var diff = min(x1, x2)
        diff = min(diff, y1)
        diff = min(diff, y2)
        val buffer = (diff * CUBE_RATIO).toInt().coerceAtLeast(24)
        cubes.add(
            MovingCube(
                (x - buffer).toFloat(),
                (y - buffer).toFloat(),
                (x + buffer).toFloat(),
                (y + buffer).toFloat(),
                CUBE_PALETTE[spawnColorIndex],
                0f,
                0f
            )
        )
        spawnColorIndex = (spawnColorIndex + 1) % CUBE_PALETTE.size
    }

    private fun mergeIntoCore() {
        if (cubes.isEmpty()) return
        var core = cubes.firstOrNull { it.isCore }
        if (core == null) {
            val ready = cubes.minByOrNull { cube ->
                val dx = cube.centerX - screenW * 0.5f
                val dy = cube.centerY - screenH * 0.5f
                dx * dx + dy * dy
            } ?: return
            if (ready.absorbImmune > 0) return
            val dx = ready.centerX - screenW * 0.5f
            val dy = ready.centerY - screenH * 0.5f
            if (sqrt(dx * dx + dy * dy) > SETTLE_DIST) return
            ready.becomeCore()
            core = ready
        }

        var i = cubes.size - 1
        while (i >= 0) {
            val cube = cubes[i]
            if (cube !== core && cube.absorbImmune <= 0 && cube.reaches(core)) {
                core.absorb(cube)
                cubes.removeAt(i)
            }
            i--
        }
        core.pinToCenter()
    }

    private fun resolveCollisions() {
        if (cubes.size < 2 || screenW <= 0) return

        for (bucket in collisionGrid.values) {
            bucket.clear()
        }
        val cell = COLLISION_CELL
        for (i in cubes.indices) {
            val cube = cubes[i]
            if (cube.isCore) continue
            val cx = (cube.centerX / cell).toInt()
            val cy = (cube.centerY / cell).toInt()
            val key = cx * 73856093 xor cy * 19349663
            collisionGrid.getOrPut(key) { ArrayList() }.add(cube)
        }

        for (i in cubes.indices) {
            val a = cubes[i]
            if (a.isCore) continue
            val cx = (a.centerX / cell).toInt()
            val cy = (a.centerY / cell).toInt()
            var ny = cy - 1
            while (ny <= cy + 1) {
                var nx = cx - 1
                while (nx <= cx + 1) {
                    val key = nx * 73856093 xor ny * 19349663
                    val bucket = collisionGrid[key]
                    if (bucket != null) {
                        for (j in bucket.indices) {
                            val b = bucket[j]
                            if (System.identityHashCode(a) < System.identityHashCode(b)) {
                                bounceIfOverlapping(a, b)
                            }
                        }
                    }
                    nx++
                }
                ny++
            }
        }
    }

    private fun bounceIfOverlapping(a: MovingCube, b: MovingCube) {
        if (a.coolDown > 0 || b.coolDown > 0) return
        if (!a.overlaps(b)) return

        a.vx = (a.vx + b.vx) * 0.25f
        a.vy = (a.vy + b.vy) * 0.25f
        b.vx = a.vx
        b.vy = a.vy
        a.coolDown = COLLISION_COOLDOWN
        b.coolDown = COLLISION_COOLDOWN
        a.separateFrom(b)
    }

    /** Cube that seeks the core, or the core itself sitting at screen center. */
    private inner class MovingCube(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var primaryArgb: Int,
        var vx: Float,
        var vy: Float
    ) {
        val size: Float get() = min(width, height)
        val centerX: Float get() = left + width * 0.5f
        val centerY: Float get() = top + height * 0.5f

        var coolDown = 0
        var absorbImmune = 0
        var isCore = false
        private var colorIndex = 0
        private var width: Float
        private var height: Float

        init {
            width = (right - left).coerceAtLeast(2f)
            height = (bottom - top).coerceAtLeast(2f)
            colorIndex = CUBE_PALETTE.indexOf(primaryArgb).coerceAtLeast(0)
        }

        fun overlaps(other: MovingCube): Boolean {
            return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
        }

        fun reaches(core: MovingCube): Boolean {
            val dx = centerX - core.centerX
            val dy = centerY - core.centerY
            val reach = (size + core.size) * 0.5f
            return dx * dx + dy * dy <= reach * reach
        }

        fun distanceTo(x: Float, y: Float): Float {
            val dx = x.coerceIn(left, right) - x
            val dy = y.coerceIn(top, bottom) - y
            return sqrt(dx * dx + dy * dy)
        }

        fun recolor() {
            colorIndex = (colorIndex + 1) % CUBE_PALETTE.size
            primaryArgb = CUBE_PALETTE[colorIndex]
        }

        fun becomeCore() {
            isCore = true
            vx = 0f
            vy = 0f
            absorbImmune = 0
            pinToCenter()
        }

        fun pinToCenter() {
            left = screenW * 0.5f - width * 0.5f
            top = screenH * 0.5f - height * 0.5f
            syncBounds()
        }

        fun absorb(other: MovingCube) {
            val area = width * height + other.width * other.height
            val maxSize = min(screenW, screenH) * MAX_CORE_RATIO
            val newSize = sqrt(area).coerceAtMost(maxSize)
            width = newSize
            height = newSize
            pinToCenter()
            recolor()
        }

        fun nudgeFromCenter() {
            isCore = false
            absorbImmune = ABSORB_IMMUNE
            val tx = centerX - screenW * 0.5f
            val ty = centerY - screenH * 0.5f
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1f)
            vx = tx / len * SPLIT_SPEED
            vy = ty / len * SPLIT_SPEED
        }

        fun splitIntoFour(): List<MovingCube> {
            isCore = false
            val half = (size * 0.5f).coerceAtLeast(MIN_SPLIT * 0.5f)
            val gap = half * SPLIT_GAP
            val cx = centerX
            val cy = centerY
            val dirs = arrayOf(
                -1f to -1f,
                1f to -1f,
                -1f to 1f,
                1f to 1f
            )
            return dirs.mapIndexed { index, (dx, dy) ->
                val childCx = cx + dx * gap
                val childCy = cy + dy * gap
                val color = CUBE_PALETTE[(colorIndex + index) % CUBE_PALETTE.size]
                MovingCube(
                    childCx - half * 0.5f,
                    childCy - half * 0.5f,
                    childCx + half * 0.5f,
                    childCy + half * 0.5f,
                    color,
                    dx * SPLIT_SPEED,
                    dy * SPLIT_SPEED
                ).also { it.absorbImmune = ABSORB_IMMUNE }
            }
        }

        fun separateFrom(other: MovingCube) {
            val dx = centerX - other.centerX
            val dy = centerY - other.centerY
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val overlapX = (width + other.width) * 0.5f - abs(dx)
            val overlapY = (height + other.height) * 0.5f - abs(dy)
            if (overlapX <= 0f || overlapY <= 0f) return
            val push = min(overlapX, overlapY) * 0.51f
            val nx = dx / dist
            val ny = dy / dist
            left += nx * push
            top += ny * push
            other.left -= nx * push
            other.top -= ny * push
            syncBounds()
            other.syncBounds()
        }

        fun step() {
            if (coolDown > 0) coolDown--
            if (absorbImmune > 0) absorbImmune--
            if (isCore) {
                pinToCenter()
                return
            }
            seekCenter()
            left += vx
            top += vy
            syncBounds()
            bounceWalls()
        }

        private fun seekCenter() {
            val tx = screenW * 0.5f - centerX
            val ty = screenH * 0.5f - centerY
            val dist = sqrt(tx * tx + ty * ty)
            if (dist > 0.001f) {
                vx += tx / dist * SEEK_ACCEL
                vy += ty / dist * SEEK_ACCEL
            }
            vx *= GATHER_FRICTION
            vy *= GATHER_FRICTION
            val speed = sqrt(vx * vx + vy * vy)
            if (speed > GATHER_MAX) {
                vx = vx / speed * GATHER_MAX
                vy = vy / speed * GATHER_MAX
            }
        }

        private fun bounceWalls() {
            if (left < 0f) {
                left = 0f
            } else if (right > screenW) {
                left = screenW - width
            }
            if (top < 0f) {
                top = 0f
            } else if (bottom > screenH) {
                top = screenH - height
            }
            syncBounds()
        }

        private fun syncBounds() {
            right = left + width
            bottom = top + height
        }
    }

    companion object {
        const val CUBE_RATIO = 0.12
        private const val SEEK_ACCEL = 0.28f
        private const val GATHER_FRICTION = 0.90f
        private const val GATHER_MAX = 8f
        private const val SETTLE_DIST = 56f
        private const val SPLIT_SPEED = 4.2f
        private const val SPLIT_GAP = 0.85f
        private const val SPLIT_HIT_SLOP = 48f
        private const val MIN_SPLIT = 18f
        private const val MAX_CORE_RATIO = 0.62f
        private const val ABSORB_IMMUNE = 36
        private const val COLLISION_CELL = 96
        private const val COLLISION_COOLDOWN = 8
        private val CUBE_PALETTE = intArrayOf(
            MikuTeal.toArgb(),
            MikuAccent.toArgb(),
            MikuTealLight.toArgb(),
            MikuTealDark.toArgb(),
            0xFFFFD54F.toInt(),
        )
    }
}
