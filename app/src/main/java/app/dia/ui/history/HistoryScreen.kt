package app.dia.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.data.settings.AppSettings
import app.dia.ui.components.color
import app.dia.ui.components.fmtNum
import app.dia.ui.components.formatDay
import app.dia.ui.components.formatTime
import app.dia.ui.components.icon
import app.dia.ui.components.title
import java.time.Instant
import java.time.ZoneId

@Composable
fun HistoryScreen(
    settings: AppSettings,
    entries: List<LogEntry>,
    onDelete: (LogEntry) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nothing logged yet.\nTap + to log insulin, food or activity.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val byDay = entries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byDay.forEach { (day, dayEntries) ->
            item(key = "h-$day") {
                val insulin = dayEntries.filter { it.type == LogType.INSULIN }
                val rapid = insulin.filter { it.subtype != InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
                val long = insulin.filter { it.subtype == InsulinKind.LONG }.sumOf { it.amount ?: 0.0 }
                val carbs = dayEntries.filter { it.type == LogType.FOOD }.sumOf { it.amount ?: 0.0 }
                Text(formatDay(dayEntries.first().timestamp), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                Text(
                    "${fmtNum(rapid)} U ${settings.rapidInsulinName} · ${fmtNum(long)} U ${settings.longInsulinName} · ${fmtNum(carbs)} g carbs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dayEntries, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(e.icon(), null, tint = e.color()) },
                        headlineContent = { Text(e.title(settings)) },
                        supportingContent = if (e.note != null) {
                            { Text(e.note) }
                        } else null,
                        overlineContent = { Text(formatTime(e.timestamp)) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(e) }) { Icon(Icons.Outlined.Delete, "Delete") }
                        },
                    )
                }
            }
        }
        item {
            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Show older entries") }
        }
    }
}
