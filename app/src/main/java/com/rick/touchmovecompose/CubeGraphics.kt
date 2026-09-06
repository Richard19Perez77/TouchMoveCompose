package com.rick.touchmovecompose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Isometric cube drawn in 2D Canvas: three visible faces plus edge strokes.
 *
 * This is not a 3D scene (no Filament / OpenGL). Depth is a 2D offset so each
 * spawned touch still costs a handful of paths, which we can later drop to
 * wireframe or skip on weaker devices.
 */
internal fun DrawScope.drawIsometricCube(
    left: Float,
    top: Float,
    size: Float,
) {
    if (size <= 1f) return

    val depth = size * DEPTH_RATIO
    val dx = depth * ISO_X
    val dy = depth * ISO_Y

    val frontTopLeft = Offset(left, top)
    val frontTopRight = Offset(left + size, top)
    val frontBottomRight = Offset(left + size, top + size)
    val frontBottomLeft = Offset(left, top + size)

    val topBackLeft = Offset(frontTopLeft.x + dx, frontTopLeft.y - dy)
    val topBackRight = Offset(frontTopRight.x + dx, frontTopRight.y - dy)
    val sideBackBottom = Offset(frontBottomRight.x + dx, frontBottomRight.y - dy)

    val topFace = Path().apply {
        moveTo(frontTopLeft.x, frontTopLeft.y)
        lineTo(frontTopRight.x, frontTopRight.y)
        lineTo(topBackRight.x, topBackRight.y)
        lineTo(topBackLeft.x, topBackLeft.y)
        close()
    }
    val rightFace = Path().apply {
        moveTo(frontTopRight.x, frontTopRight.y)
        lineTo(frontBottomRight.x, frontBottomRight.y)
        lineTo(sideBackBottom.x, sideBackBottom.y)
        lineTo(topBackRight.x, topBackRight.y)
        close()
    }
    val frontFace = Path().apply {
        moveTo(frontTopLeft.x, frontTopLeft.y)
        lineTo(frontTopRight.x, frontTopRight.y)
        lineTo(frontBottomRight.x, frontBottomRight.y)
        lineTo(frontBottomLeft.x, frontBottomLeft.y)
        close()
    }

    drawPath(topFace, CubePalette.top, style = Fill)
    drawPath(rightFace, CubePalette.right, style = Fill)
    drawPath(frontFace, CubePalette.front, style = Fill)

    drawPath(topFace, CubePalette.edge, style = Stroke(width = EDGE_STROKE))
    drawPath(rightFace, CubePalette.edge, style = Stroke(width = EDGE_STROKE))
    drawPath(frontFace, CubePalette.edge, style = Stroke(width = EDGE_STROKE))
}

private object CubePalette {
    val front = Color(0xFF1E88E5)
    val top = Color(0xFF64B5F6)
    val right = Color(0xFF1565C0)
    val edge = Color(0xFF0D47A1)
}

private const val DEPTH_RATIO = 0.45f
private const val ISO_X = 0.72f
private const val ISO_Y = 0.42f
private const val EDGE_STROKE = 1.5f
