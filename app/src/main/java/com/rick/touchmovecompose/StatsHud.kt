package com.rick.touchmovecompose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Compact stats strip. Each row scrolls sideways so long metrics stay on
 * one line instead of wrapping off a narrow phone.
 */
@Composable
fun StatsHud(
    engine: TouchMoveEngine,
    telemetry: PerformanceTelemetry,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val scrim = MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f)
    Column(
        modifier
            .fillMaxWidth()
            .background(scrim)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        HudScrollRow(
            accent,
            "Cubes ${engine.cubeCount} (${engine.drawnCount} drawn)   " +
                "Draws ${engine.drawCounter}   Touches ${engine.touchCounter}",
        )
        HudScrollRow(
            accent,
            "FPS ${telemetry.fps.format1()}   " +
                "Frame ${telemetry.frameMs.format1()}ms   " +
                "Jank ${telemetry.jankCount}   " +
                "${telemetry.refreshHz.format0()}Hz",
        )
        HudScrollRow(
            accent,
            "CPU ${telemetry.cpuPercent.format0()}% of ${telemetry.cpuCores} cores   " +
                "UI ${telemetry.uiCpuPercent.format0()}%",
        )
        HudScrollRow(
            accent,
            "Heap ${telemetry.heapUsedMb.format0()}/${telemetry.heapMaxMb.format0()} MB   " +
                "Native ${telemetry.nativeHeapMb.format0()} MB",
        )
        HudScrollRow(
            accent,
            "PSS ${telemetry.pssMb.format0()} MB   " +
                "Thermal ${telemetry.thermalLabel}   " +
                telemetry.budgetLabel,
        )
    }
}

@Composable
private fun HudScrollRow(color: Color, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

private fun Float.format0(): String = String.format(Locale.US, "%.0f", this)

private fun Float.format1(): String = String.format(Locale.US, "%.1f", this)
