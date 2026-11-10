package app.dia.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogType
import app.dia.data.settings.AppSettings
import app.dia.domain.GlucoseSummary
import app.dia.domain.RangeBand
import app.dia.domain.hourlyProfile
import app.dia.ui.StatsData
import app.dia.ui.components.fmtNum
import app.dia.ui.home.Stat
import app.dia.ui.theme.DiaColors
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max

@Composable
fun StatsScreen(
    settings: AppSettings,
    stats: StatsData?,
    onPeriod: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val unit = settings.unit
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 90).forEach { d ->
                    FilterChip(selected = stats?.days == d, onClick = { onPeriod(d) }, label = { Text("$d days") })
                }
            }
        }
        if (stats == null) return@LazyColumn
        val summary = GlucoseSummary.of(stats.readings, settings.targetLow, settings.targetHigh)

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Glucose", style = MaterialTheme.typography.titleMedium)
                    if (summary == null) {
                        Text("No CGM readings in this period.")
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Average", unit.format(summary.mean))
                            Stat("GMI (est. A1c)", String.format(Locale.getDefault(), "%.1f%%", summary.gmi))
                            Stat("Variability", String.format(Locale.getDefault(), "%.0f%% CV", summary.cv))
                        }
                        // Readings arrive every ~15 min from LibreLinkUp history (96/day), or 5 min from live data.
                        val coverage = (summary.count / (stats.days * 96.0)).coerceAtMost(1.0)
                        Text(
                            "${summary.count} readings · range ${unit.format(summary.min)}–${unit.format(summary.max)} ${unit.label}" +
                                " · ~${Math.round(coverage * 100)}% coverage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (summary != null) {
            item { TimeInRangeCard(summary, settings) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Daily pattern", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Median by hour of day, with the middle 50% and 80% of readings shaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        HourlyChart(stats, settings)
                    }
                }
            }
        }

        item {
            val days = stats.days.toDouble()
            val rapid = stats.logs.filter { it.type == LogType.INSULIN && it.subtype != InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
            val long = stats.logs.filter { it.type == LogType.INSULIN && it.subtype == InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
            val carbs = stats.logs.filter { it.type == LogType.FOOD }.sumOf { it.amount ?: 0.0 }
            val activity = stats.logs.filter { it.type == LogType.ACTIVITY }
            val loggedDays = stats.logs.map { it.timestamp / 86_400_000L }.distinct().size.coerceAtLeast(1)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Daily averages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Averaged over $loggedDays day(s) with at least one log entry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(settings.rapidInsulinName, "${fmtNum(round1(rapid / loggedDays))} U")
                        Stat(settings.longInsulinName, "${fmtNum(round1(long / loggedDays))} U")
                        Stat("Total", "${fmtNum(round1((rapid + long) / loggedDays))} U")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Carbs", "${Math.round(carbs / loggedDays)} g")
                        Stat("Activity", "${Math.round(activity.sumOf { it.durationMin ?: 0 } / loggedDays.toDouble())} min")
                        Stat("Workouts", fmtNum(round1(activity.size / days * 7)) + "/wk")
                    }
                }
            }
        }
        item {
            Text(
                "Dia is a personal logbook, not a medical device. Don't make treatment decisions from it alone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun round1(v: Double) = Math.round(v * 10) / 10.0

@Composable
private fun TimeInRangeCard(summary: GlucoseSummary, settings: AppSettings) {
    val unit = settings.unit
    val rows = listOf(
        Triple(RangeBand.VERY_HIGH, "Very high", "> ${unit.format(250)}"),
        Triple(RangeBand.HIGH, "High", "${unit.format(settings.targetHigh + 1)}–${unit.format(250)}"),
        Triple(RangeBand.IN_RANGE, "In range", "${unit.format(settings.targetLow)}–${unit.format(settings.targetHigh)}"),
        Triple(RangeBand.LOW, "Low", "${unit.format(54)}–${unit.format(settings.targetLow - 1)}"),
        Triple(RangeBand.VERY_LOW, "Very low", "< ${unit.format(54)}"),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Time in ranges", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stacked vertical bar
                Column(Modifier.width(28.dp).height(180.dp).clip(RoundedCornerShape(6.dp))) {
                    rows.forEach { (band, _, _) ->
                        val f = (summary.bands[band] ?: 0.0).toFloat()
                        if (f > 0f) {
                            Box(Modifier.fillMaxWidth().weight(f).background(DiaColors.band(band)))
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (band, label, range) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(DiaColors.band(band)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${Math.round((summary.bands[band] ?: 0.0) * 100)}%",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(44.dp),
                            )
                            Text("$label ($range)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Common goals: >70% in range, <4% low, <1% very low.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourlyChart(stats: StatsData, settings: AppSettings) {
    val profile = hourlyProfile(stats.readings, ZoneId.systemDefault())
    if (profile.size < 2) {
        Text("Not enough data yet.")
        return
    }
    val outer = DiaColors.inRange.copy(alpha = 0.12f)
    val inner = DiaColors.inRange.copy(alpha = 0.28f)
    val medianColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val yMin = 40f
            val yMax = max(300, profile.maxOf { it.p90 } + 20).toFloat()
            fun x(h: Int) = h / 23f * size.width
            fun y(v: Int) = size.height - (v.toFloat().coerceIn(yMin, yMax) - yMin) / (yMax - yMin) * size.height

            fun band(lo: (app.dia.domain.HourlyProfile) -> Int, hi: (app.dia.domain.HourlyProfile) -> Int): Path {
                val p = Path()
                profile.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.hour), y(hi(pt))) else p.lineTo(x(pt.hour), y(hi(pt))) }
                profile.reversed().forEach { p.lineTo(x(it.hour), y(lo(it))) }
                p.close()
                return p
            }
            drawLine(targetColor, Offset(0f, y(settings.targetHigh)), Offset(size.width, y(settings.targetHigh)), 1f)
            drawLine(targetColor, Offset(0f, y(settings.targetLow)), Offset(size.width, y(settings.targetLow)), 1f)
            drawPath(band({ it.p10 }, { it.p90 }), outer)
            drawPath(band({ it.p25 }, { it.p75 }), inner)
            val m = Path()
            profile.forEachIndexed { i, pt -> if (i == 0) m.moveTo(x(pt.hour), y(pt.median)) else m.lineTo(x(pt.hour), y(pt.median)) }
            drawPath(m, medianColor, style = Stroke(width = 2.5.dp.toPx()))
            drawRect(targetColor, Offset.Zero, Size(size.width, size.height), style = Stroke(1f), alpha = 0.3f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}
