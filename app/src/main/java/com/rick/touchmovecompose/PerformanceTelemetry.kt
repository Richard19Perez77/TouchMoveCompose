package com.rick.touchmovecompose

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager

/**
 * Live app stats sampled from the Compose vsync loop.
 *
 * FPS and frame time update every frame. CPU, heap, and thermal refresh on a
 * short timer. PSS is slower because [Debug.getMemoryInfo] can hitch.
 *
 * Process CPU is percent of *all* cores (100% ≈ the whole chip is busy).
 * UI CPU is percent of *one* core — that is the thread that draws frames,
 * so FPS usually holds at the display Hz until this line nears 100%.
 */
class PerformanceTelemetry(private val context: Context) {
    var fps: Float = 0f
        private set
    var frameMs: Float = 0f
        private set
    var jankCount: Int = 0
        private set
    var cpuPercent: Float = 0f
        private set
    var uiCpuPercent: Float = 0f
        private set
    var cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private set
    var heapUsedMb: Float = 0f
        private set
    var heapMaxMb: Float = 0f
        private set
    var nativeHeapMb: Float = 0f
        private set
    var pssMb: Float = 0f
        private set
    var thermalLabel: String = "—"
        private set
    var refreshHz: Float = readRefreshRate(context)
        private set
    var holdSpawn: Boolean = false
        private set
    var skipSmallCubes: Boolean = false
        private set

    val budgetLabel: String
        get() = when {
            skipSmallCubes -> "LOD"
            holdSpawn -> "Hold spawn"
            else -> "Budget ok"
        }

    private var lastFrameNanos = 0L
    private var framesInWindow = 0
    private var windowStartMs = 0L
    private var lastCpuMs = 0L
    private var lastUiCpuNs = 0L
    private var lastCpuWallMs = 0L
    private var lastPssMs = 0L

    fun onFrame(frameNanos: Long) {
        if (lastFrameNanos != 0L) {
            val dtNs = frameNanos - lastFrameNanos
            if (dtNs > 0L) {
                frameMs = dtNs / 1_000_000f
                val budgetNs = 1_000_000_000f / refreshHz.coerceAtLeast(1f)
                if (dtNs > budgetNs * JANK_MULTIPLIER) {
                    jankCount++
                }
            }
        } else {
            windowStartMs = SystemClock.elapsedRealtime()
            lastCpuMs = Process.getElapsedCpuTime()
            lastUiCpuNs = Debug.threadCpuTimeNanos()
            lastCpuWallMs = windowStartMs
        }
        lastFrameNanos = frameNanos
        framesInWindow++

        val nowMs = SystemClock.elapsedRealtime()
        val windowMs = nowMs - windowStartMs
        if (windowMs >= SAMPLE_MS) {
            fps = framesInWindow * 1000f / windowMs
            framesInWindow = 0
            windowStartMs = nowMs
            sampleCpu(nowMs)
            sampleHeap()
            sampleThermal()
            if (nowMs - lastPssMs >= PSS_SAMPLE_MS) {
                lastPssMs = nowMs
                samplePss()
            }
            updateBudget()
        }
    }

    fun reset() {
        fps = 0f
        frameMs = 0f
        jankCount = 0
        lastFrameNanos = 0L
        framesInWindow = 0
        windowStartMs = 0L
        lastCpuMs = 0L
        lastUiCpuNs = 0L
        lastCpuWallMs = 0L
        lastPssMs = 0L
        holdSpawn = false
        skipSmallCubes = false
        refreshHz = readRefreshRate(context)
    }

    private fun updateBudget() {
        if (fps <= 0f) {
            holdSpawn = false
            skipSmallCubes = false
            return
        }
        if (fps < SPAWN_HOLD_FPS) holdSpawn = true
        else if (fps >= SPAWN_RESUME_FPS) holdSpawn = false
        skipSmallCubes = fps < TARGET_FPS
    }

    private fun sampleCpu(nowMs: Long) {
        val cpuMs = Process.getElapsedCpuTime()
        val uiCpuNs = Debug.threadCpuTimeNanos()
        val wallMs = nowMs - lastCpuWallMs
        if (wallMs > 0L) {
            val cores = cpuCores.toFloat()
            // Sum of thread CPU vs wall time, then divide by cores so 100% is
            // "the whole device", not "one core".
            cpuPercent = (cpuMs - lastCpuMs) * 100f / (wallMs * cores)
            if (uiCpuNs >= 0L && lastUiCpuNs >= 0L) {
                val uiDeltaMs = (uiCpuNs - lastUiCpuNs) / 1_000_000f
                uiCpuPercent = uiDeltaMs * 100f / wallMs
            }
        }
        lastCpuMs = cpuMs
        lastUiCpuNs = uiCpuNs
        lastCpuWallMs = nowMs
    }

    private fun sampleHeap() {
        val runtime = Runtime.getRuntime()
        heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
        heapMaxMb = runtime.maxMemory() / BYTES_PER_MB
        nativeHeapMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB
    }

    private fun samplePss() {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        pssMb = info.totalPss / 1024f
    }

    private fun sampleThermal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            thermalLabel = "n/a"
            return
        }
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        thermalLabel = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
    }

    private fun readRefreshRate(context: Context): Float {
        val hz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .refreshRate
        }
        return if (hz != null && hz > 1f) hz else 60f
    }

    companion object {
        private const val SAMPLE_MS = 500L
        private const val PSS_SAMPLE_MS = 2000L
        private const val JANK_MULTIPLIER = 1.5f
        private const val BYTES_PER_MB = 1024f * 1024f
        const val TARGET_FPS = 55f
        const val LOD_MIN_SIZE = 18f
        private const val SPAWN_HOLD_FPS = 58f
        private const val SPAWN_RESUME_FPS = 62f
    }
}
