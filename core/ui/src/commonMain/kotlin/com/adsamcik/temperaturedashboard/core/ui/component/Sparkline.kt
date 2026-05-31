package com.adsamcik.temperaturedashboard.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal stepped-line sparkline rendered directly with Canvas — no third-party
 * dependency, perfect for the dashboard card preview where chart-grade detail
 * isn't required.
 *
 * Each (x, y) is plotted in normalised coordinates derived from [values].
 * Empty input renders a horizontal baseline so layout stays stable.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier.fillMaxWidth().height(48.dp),
    color: Color = Color.Unspecified,
    lineWidth: Dp = 2.dp,
) {
    val effectiveColor = if (color == Color.Unspecified) {
        androidx.compose.material3.MaterialTheme.colorScheme.primary
    } else {
        color
    }
    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (values.size < 2) {
                drawHorizontalGuide(effectiveColor)
                return@Canvas
            }
            val min = values.min()
            val max = values.max()
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val stepX = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - ((v - min) / range * size.height).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = effectiveColor,
                style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHorizontalGuide(color: Color) {
    val y = size.height / 2f
    drawLine(
        color = color.copy(alpha = 0.3f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )
}
