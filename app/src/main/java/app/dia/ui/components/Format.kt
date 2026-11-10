package app.dia.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.data.settings.AppSettings
import app.dia.ui.theme.DiaColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

fun formatTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)

fun formatDay(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dayFmt)

fun formatAgo(ts: Long, now: Long): String {
    val min = ((now - ts) / 60_000L).coerceAtLeast(0)
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ${min % 60} min ago"
        else -> "${min / (24 * 60)} days ago"
    }
}

fun fmtNum(v: Double?): String = when {
    v == null -> ""
    v == Math.floor(v) -> v.toLong().toString()
    else -> String.format(Locale.getDefault(), "%.1f", v)
}

fun LogEntry.icon(): ImageVector = when (type) {
    LogType.INSULIN -> Icons.Filled.Vaccines
    LogType.FOOD -> Icons.Filled.Restaurant
    LogType.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
    LogType.FINGERSTICK -> Icons.Filled.Bloodtype
    LogType.WEIGHT -> Icons.Filled.MonitorWeight
    LogType.KETONES -> Icons.Filled.Science
    LogType.NOTE -> Icons.AutoMirrored.Filled.Notes
}

fun LogEntry.color(): Color = when (type) {
    LogType.INSULIN -> if (subtype == InsulinKind.LONG) DiaColors.longInsulin else DiaColors.insulin
    LogType.FOOD -> DiaColors.food
    LogType.ACTIVITY -> DiaColors.activity
    else -> DiaColors.other
}

fun LogEntry.title(s: AppSettings): String = when (type) {
    LogType.INSULIN -> {
        val name = if (subtype == InsulinKind.LONG) s.longInsulinName else s.rapidInsulinName
        "${fmtNum(amount)} U $name"
    }
    LogType.FOOD -> buildString {
        append("${fmtNum(amount)} g carbs")
        if (!subtype.isNullOrBlank()) append(" · $subtype")
    }
    LogType.ACTIVITY -> buildString {
        append(subtype ?: "Activity")
        if (durationMin != null) append(" · $durationMin min")
        if (!intensity.isNullOrBlank()) append(" · $intensity")
    }
    LogType.FINGERSTICK -> "Fingerstick ${amount?.let { s.unit.formatWithUnit(it) } ?: ""}"
    LogType.WEIGHT -> "Weight ${fmtNum(amount)} kg"
    LogType.KETONES -> "Ketones ${fmtNum(amount)} mmol/L"
    LogType.NOTE -> "Note"
}
