@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dia.data.db.GlucoseReading
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.data.settings.AppSettings
import app.dia.domain.GlucoseSummary
import app.dia.domain.bandOf
import app.dia.domain.trendArrow
import app.dia.domain.trendLabel
import app.dia.ui.components.GlucoseChart
import app.dia.ui.components.color
import app.dia.ui.components.fmtNum
import app.dia.ui.components.formatAgo
import app.dia.ui.components.formatTime
import app.dia.ui.components.icon
import app.dia.ui.components.title
import app.dia.ui.log.LogTab
import app.dia.ui.theme.DiaColors
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    settings: AppSettings,
    now: Long,
    latest: List<GlucoseReading>,
    glucose24h: List<GlucoseReading>,
    logs24h: List<LogEntry>,
    todayLogs: List<LogEntry>,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onQuickLog: (LogTab) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    var hours by rememberSaveable { mutableIntStateOf(6) }
    PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = Modifier.padding(contentPadding)) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CurrentCard(settings, now, latest, onOpenSettings) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 6, 12, 24).forEach { h ->
                                FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("${h}h") })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GlucoseChart(
                            readings = glucose24h,
                            logs = logs24h,
                            fromMs = now - hours * 3_600_000L,
                            toMs = now,
                            low = settings.targetLow,
                            high = settings.targetHigh,
                            unit = settings.unit,
                        )
                    }
                }
            }
            item { QuickLogRow(onQuickLog) }
            item { TodayCard(settings, todayLogs, glucose24h) }
            if (todayLogs.isNotEmpty()) {
                item {
                    Text("Today's log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                }
                items(todayLogs.take(8), key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = { Icon(e.icon(), null, tint = e.color()) },
                            headlineContent = { Text(e.title(settings)) },
                            supportingContent = if (e.note != null) {
                                { Text(e.note, maxLines = 2) }
                            } else null,
                            trailingContent = { Text(formatTime(e.timestamp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentCard(settings: AppSettings, now: Long, latest: List<GlucoseReading>, onOpenSettings: () -> Unit) {
    val current = latest.firstOrNull()
    Card(Modifier.fillMaxWidth()) {
        if (current == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No glucose data yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Connect your LibreLinkUp account to pull readings from your FreeStyle Libre 2, " +
                        "or import a LibreView CSV export.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenSettings) { Text("Connect LibreLinkUp") }
            }
            return@Card
        }
        val stale = now - current.timestamp > 20 * 60_000L
        val color = if (stale) MaterialTheme.colorScheme.outline
        else DiaColors.band(bandOf(current.mgdl, settings.targetLow, settings.targetHigh))
        val previous = latest.getOrNull(1)?.takeIf { current.timestamp - it.timestamp <= 20 * 60_000L }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        settings.unit.format(current.mgdl),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        lineHeight = 64.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(trendArrow(current.trend), fontSize = 44.sp, color = color)
                }
                Text(settings.unit.label, style = MaterialTheme.typography.titleSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatAgo(current.timestamp, now), style = MaterialTheme.typography.bodyLarge,
                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (current.trend != null) Text(trendLabel(current.trend), style = MaterialTheme.typography.bodyMedium)
                if (previous != null) {
                    val d = current.mgdl - previous.mgdl
                    val sign = if (d >= 0) "+" else "−"
                    Text("$sign${settings.unit.format(kotlin.math.abs(d))} since last", style = MaterialTheme.typography.bodySmall)
                }
                settings.lastSyncError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun QuickLogRow(onQuickLog: (LogTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickButton("Insulin", Icons.Filled.Vaccines, DiaColors.insulin, Modifier.weight(1f)) { onQuickLog(LogTab.INSULIN) }
        QuickButton("Food", Icons.Filled.Restaurant, DiaColors.food, Modifier.weight(1f)) { onQuickLog(LogTab.FOOD) }
        QuickButton("Activity", Icons.AutoMirrored.Filled.DirectionsRun, DiaColors.activity, Modifier.weight(1f)) { onQuickLog(LogTab.ACTIVITY) }
        QuickButton("Other", Icons.Filled.MoreHoriz, DiaColors.other, Modifier.weight(1f)) { onQuickLog(LogTab.OTHER) }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        contentPadding = PaddingValues(4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TodayCard(settings: AppSettings, todayLogs: List<LogEntry>, glucose24h: List<GlucoseReading>) {
    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val rapid = todayLogs.filter { it.type == LogType.INSULIN && it.subtype != InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
    val long = todayLogs.filter { it.type == LogType.INSULIN && it.subtype == InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
    val carbs = todayLogs.filter { it.type == LogType.FOOD }.sumOf { it.amount ?: 0.0 }
    val active = todayLogs.filter { it.type == LogType.ACTIVITY }.sumOf { it.durationMin ?: 0 }
    val summary = GlucoseSummary.of(glucose24h.filter { it.timestamp >= startOfDay }, settings.targetLow, settings.targetHigh)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Time in range", summary?.let { "${Math.round(it.timeInRange * 100)}%" } ?: "–")
                Stat("Average", summary?.let { settings.unit.format(it.mean) } ?: "–")
                Stat(settings.rapidInsulinName, "${fmtNum(rapid)} U")
                Stat(settings.longInsulinName, "${fmtNum(long)} U")
                Stat("Carbs", "${fmtNum(carbs)} g")
                Stat("Active", "$active min")
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
