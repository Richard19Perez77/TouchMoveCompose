package com.rick.touchmovecompose

import android.graphics.Canvas
import android.graphics.Paint

/**
 * One GPU/CPU mesh for every visible cube: triangle vertices + line edges.
 *
 * Game engines stay at 60fps with thousands of cubes because they submit *one*
 * (or a few) batched draws, not one draw per face. This is that idea on a
 * 2D Canvas: reused arrays, two native calls per frame.
 */
internal class CubeBatch {
    private var verts = FloatArray(INITIAL_FLOATS)
    private var colors = IntArray(INITIAL_FLOATS / 2)
    private var lines = FloatArray(INITIAL_FLOATS)
    private var vertFloats = 0
    private var colorCount = 0
    private var lineFloats = 0
    var cubeCount = 0
        private set

    private var colorFront = 0xFF39C5BB.toInt()
    private var colorTop = 0xFF7AEEE6.toInt()
    private var colorRight = 0xFF137A74.toInt()
    private var colorEdge = 0xFF0B4F4B.toInt()

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        // Hardware Canvas ignores vertex colors unless a shader is set.
        val white = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        white.eraseColor(android.graphics.Color.WHITE)
        shader = android.graphics.BitmapShader(
            white,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    private val edgePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = EDGE_STROKE
        color = colorEdge
        isAntiAlias = true
    }

    fun setPrimaryColor(primaryArgb: Int) {
        colorFront = primaryArgb
        colorTop = mixTowardWhite(primaryArgb, 0.40f)
        colorRight = mixTowardBlack(primaryArgb, 0.28f)
        colorEdge = mixTowardBlack(primaryArgb, 0.48f)
        edgePaint.color = colorEdge
    }

    fun begin() {
        vertFloats = 0
        colorCount = 0
        lineFloats = 0
        cubeCount = 0
    }

    fun addCube(left: Float, top: Float, size: Float) {
        if (size <= 1f) return

        val depth = size * DEPTH_RATIO
        val dx = depth * ISO_X
        val dy = depth * ISO_Y

        val ftlX = left
        val ftlY = top
        val ftrX = left + size
        val ftrY = top
        val fbrX = left + size
        val fbrY = top + size
        val fblX = left
        val fblY = top + size
        val tblX = ftlX + dx
        val tblY = ftlY - dy
        val tbrX = ftrX + dx
        val tbrY = ftrY - dy
        val sbbX = fbrX + dx
        val sbbY = fbrY - dy

        ensureFaces(FLOATS_PER_CUBE, VERTICES_PER_CUBE)
        ensureLines(LINE_FLOATS_PER_CUBE)

        addQuad(ftlX, ftlY, ftrX, ftrY, tbrX, tbrY, tblX, tblY, colorTop)
        addQuad(ftrX, ftrY, fbrX, fbrY, sbbX, sbbY, tbrX, tbrY, colorRight)
        addQuad(ftlX, ftlY, ftrX, ftrY, fbrX, fbrY, fblX, fblY, colorFront)

        addLine(ftlX, ftlY, ftrX, ftrY)
        addLine(ftrX, ftrY, fbrX, fbrY)
        addLine(fbrX, fbrY, fblX, fblY)
        addLine(fblX, fblY, ftlX, ftlY)
        addLine(ftlX, ftlY, tblX, tblY)
        addLine(ftrX, ftrY, tbrX, tbrY)
        addLine(fbrX, fbrY, sbbX, sbbY)
        addLine(tblX, tblY, tbrX, tbrY)
        addLine(tbrX, tbrY, sbbX, sbbY)

        cubeCount++
    }

    fun draw(canvas: Canvas) {
        if (vertFloats >= 6) {
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES,
                vertFloats,
                verts,
                0,
                null,
                0,
                colors,
                0,
                null,
                0,
                0,
                fillPaint
            )
        }
        if (lineFloats >= 4) {
            canvas.drawLines(lines, 0, lineFloats, edgePaint)
        }
    }

    private fun addQuad(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        color: Int,
    ) {
        addTri(x0, y0, x1, y1, x2, y2, color)
        addTri(x0, y0, x2, y2, x3, y3, color)
    }

    private fun addTri(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        color: Int,
    ) {
        verts[vertFloats++] = x0
        verts[vertFloats++] = y0
        verts[vertFloats++] = x1
        verts[vertFloats++] = y1
        verts[vertFloats++] = x2
        verts[vertFloats++] = y2
        colors[colorCount++] = color
        colors[colorCount++] = color
        colors[colorCount++] = color
    }

    private fun addLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        lines[lineFloats++] = x0
        lines[lineFloats++] = y0
        lines[lineFloats++] = x1
        lines[lineFloats++] = y1
    }

    private fun ensureFaces(extraFloats: Int, extraColors: Int) {
        if (vertFloats + extraFloats > verts.size) {
            verts = verts.copyOf(grow(verts.size, vertFloats + extraFloats))
        }
        if (colorCount + extraColors > colors.size) {
            colors = colors.copyOf(grow(colors.size, colorCount + extraColors))
        }
    }

    private fun ensureLines(extraFloats: Int) {
        if (lineFloats + extraFloats > lines.size) {
            lines = lines.copyOf(grow(lines.size, lineFloats + extraFloats))
        }
    }

    private fun grow(current: Int, needed: Int): Int {
        var cap = current.coerceAtLeast(1)
        while (cap < needed) cap *= 2
        return cap
    }

    companion object {
        private const val INITIAL_FLOATS = 36 * 64
        private const val VERTICES_PER_CUBE = 18
        private const val FLOATS_PER_CUBE = VERTICES_PER_CUBE * 2
        private const val LINE_FLOATS_PER_CUBE = 9 * 4
        private const val DEPTH_RATIO = 0.45f
        private const val ISO_X = 0.72f
        private const val ISO_Y = 0.42f
        private const val EDGE_STROKE = 1.5f
    }
}

private fun mixTowardWhite(color: Int, amount: Float): Int {
    val a = android.graphics.Color.alpha(color)
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    return android.graphics.Color.argb(
        a,
        r + ((255 - r) * amount).toInt(),
        g + ((255 - g) * amount).toInt(),
        b + ((255 - b) * amount).toInt(),
    )
}

private fun mixTowardBlack(color: Int, amount: Float): Int {
    val a = android.graphics.Color.alpha(color)
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    val keep = 1f - amount
    return android.graphics.Color.argb(
        a,
        (r * keep).toInt(),
        (g * keep).toInt(),
        (b * keep).toInt(),
    )
}
