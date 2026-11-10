package app.dia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.dia.data.db.GlucoseReading
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.domain.GlucoseUnit
import app.dia.domain.bandOf
import app.dia.ui.theme.DiaColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

/**
 * Line chart of glucose over [fromMs]..[toMs], with the target band shaded and
 * insulin / food / activity markers along the bottom.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    logs: List<LogEntry>,
    fromMs: Long,
    toMs: Long,
    low: Int,
    high: Int,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val bandColor = DiaColors.inRange.copy(alpha = 0.12f)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val visible = readings.filter { it.timestamp in fromMs..toMs }
    val visibleLogs = logs.filter { it.timestamp in fromMs..toMs }

    Box(modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val leftPad = 34.dp.toPx()
            val bottomPad = 36.dp.toPx()
            val topPad = 8.dp.toPx()
            val w = size.width - leftPad
            val h = size.height - bottomPad - topPad
            val yMin = 40f
            val yMax = max(300, (visible.maxOfOrNull { it.mgdl } ?: 0) + 20).toFloat()
            val span = (toMs - fromMs).coerceAtLeast(1).toFloat()

            fun x(ts: Long) = leftPad + (ts - fromMs) / span * w
            fun y(mg: Float) = topPad + h - (mg.coerceIn(yMin, yMax) - yMin) / (yMax - yMin) * h

            // Target band
            drawRect(bandColor, Offset(leftPad, y(high.toFloat())), Size(w, y(low.toFloat()) - y(high.toFloat())))

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10.dp.toPx()
                color = labelColor
            }
            // Horizontal grid lines + labels
            val gridValues = listOf(low, high, 250).filter { it < yMax }
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            for (g in gridValues) {
                drawLine(gridColor, Offset(leftPad, y(g.toFloat())), Offset(size.width, y(g.toFloat())), 1f, pathEffect = dash)
                drawContext.canvas.nativeCanvas.drawText(unit.format(g), 0f, y(g.toFloat()) + 4.dp.toPx(), paint)
            }

            // Hour ticks
            val hours = (toMs - fromMs) / 3_600_000L
            val step = when {
                hours <= 3 -> 1
                hours <= 6 -> 2
                hours <= 12 -> 3
                else -> 6
            }
            val zone = ZoneId.systemDefault()
            var t = Instant.ofEpochMilli(fromMs).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
            while (t.toInstant().toEpochMilli() < toMs) {
                if (t.hour % step == 0) {
                    val tx = x(t.toInstant().toEpochMilli())
                    drawLine(gridColor, Offset(tx, topPad), Offset(tx, topPad + h), 1f)
                    val label = "%02d:00".format(t.hour)
                    drawContext.canvas.nativeCanvas.drawText(label, tx - 12.dp.toPx(), size.height - 2.dp.toPx(), paint)
                }
                t = t.plusHours(1)
            }

            // Glucose line (break on gaps > 30 min)
            if (visible.size > 1) {
                val path = Path()
                var prev: GlucoseReading? = null
                for (r in visible) {
                    val px = x(r.timestamp)
                    val py = y(r.mgdl.toFloat())
                    if (prev == null || r.timestamp - prev.timestamp > 30 * 60_000L) path.moveTo(px, py) else path.lineTo(px, py)
                    prev = r
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
            }
            for (r in visible) {
                drawCircle(
                    DiaColors.band(bandOf(r.mgdl, low, high)),
                    radius = 2.6.dp.toPx(),
                    center = Offset(x(r.timestamp), y(r.mgdl.toFloat())),
                )
            }

            // Event markers along the bottom strip
            val markerY = topPad + h + 10.dp.toPx()
            for (e in visibleLogs) {
                val ex = x(e.timestamp)
                drawLine(e.color().copy(alpha = 0.35f), Offset(ex, topPad), Offset(ex, topPad + h), 1.5f)
                when (e.type) {
                    LogType.INSULIN, LogType.FOOD -> {
                        drawRoundRect(
                            e.color(),
                            topLeft = Offset(ex - 11.dp.toPx(), markerY - 7.dp.toPx()),
                            size = Size(22.dp.toPx(), 14.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        val txt = fmtNum(e.amount)
                        val p = android.graphics.Paint(paint).apply {
                            color = android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(txt, ex, markerY + 3.5.dp.toPx(), p)
                    }
                    else -> drawCircle(e.color(), 4.dp.toPx(), Offset(ex, markerY))
                }
            }
        }
    }
}
