@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dia.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.data.settings.AppSettings
import app.dia.domain.GlucoseUnit
import app.dia.domain.MMOL_FACTOR
import app.dia.ui.components.fmtNum

enum class LogTab(val label: String) { INSULIN("Insulin"), FOOD("Food"), ACTIVITY("Activity"), OTHER("Other") }

private val timeOffsets = listOf(0 to "Now", 15 to "15m ago", 30 to "30m ago", 60 to "1h ago", 120 to "2h ago")

@Composable
fun QuickLogSheet(
    initialTab: LogTab,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (LogEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(initialTab) }
    var offsetMin by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }

    // Insulin
    var insulinKind by remember { mutableStateOf(InsulinKind.RAPID) }
    var units by remember { mutableStateOf("") }
    // Food
    var carbs by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf<String?>(null) }
    // Activity
    var activity by remember { mutableStateOf("Walk") }
    var minutes by remember { mutableStateOf("30") }
    var intensity by remember { mutableStateOf("Moderate") }
    // Other
    var otherType by remember { mutableStateOf(LogType.FINGERSTICK) }
    var otherValue by remember { mutableStateOf("") }

    fun parse(s: String) = s.replace(',', '.').toDoubleOrNull()

    val entry: LogEntry? = run {
        val ts = System.currentTimeMillis() - offsetMin * 60_000L
        val n = note.trim().ifEmpty { null }
        when (tab) {
            LogTab.INSULIN -> parse(units)?.takeIf { it > 0 }?.let {
                LogEntry(timestamp = ts, type = LogType.INSULIN, amount = it, subtype = insulinKind, note = n)
            }
            LogTab.FOOD -> parse(carbs)?.takeIf { it >= 0 }?.let {
                LogEntry(timestamp = ts, type = LogType.FOOD, amount = it, subtype = meal, note = n)
            }
            LogTab.ACTIVITY -> minutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                LogEntry(
                    timestamp = ts, type = LogType.ACTIVITY, subtype = activity,
                    durationMin = it, intensity = intensity, note = n,
                )
            }
            LogTab.OTHER -> if (otherType == LogType.NOTE) {
                n?.let { LogEntry(timestamp = ts, type = LogType.NOTE, note = it) }
            } else {
                parse(otherValue)?.takeIf { it > 0 }?.let { v ->
                    val stored = if (otherType == LogType.FINGERSTICK && settings.unit == GlucoseUnit.MMOL) {
                        Math.round(v * MMOL_FACTOR).toDouble()
                    } else v
                    LogEntry(timestamp = ts, type = otherType, amount = stored, note = n)
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                LogTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    LogTab.INSULIN -> {
                        Segmented(
                            options = listOf(InsulinKind.RAPID to settings.rapidInsulinName, InsulinKind.LONG to settings.longInsulinName),
                            selected = insulinKind,
                            onSelect = { insulinKind = it },
                        )
                        Stepper(
                            value = units,
                            onValueChange = { units = it },
                            label = "Units",
                            step = if (insulinKind == InsulinKind.RAPID) 0.5 else 1.0,
                        )
                        QuickChips(
                            values = if (insulinKind == InsulinKind.RAPID) listOf(1, 2, 3, 4, 5, 6, 8, 10) else listOf(8, 10, 12, 14, 16, 18, 20, 24),
                            suffix = " U",
                            selected = parse(units),
                            onPick = { units = it.toString() },
                        )
                    }
                    LogTab.FOOD -> {
                        Stepper(value = carbs, onValueChange = { carbs = it }, label = "Carbs (g)", step = 5.0)
                        QuickChips(listOf(10, 15, 20, 30, 45, 60, 75, 90), " g", parse(carbs)) { carbs = it.toString() }
                        ChipRow(listOf("Breakfast", "Lunch", "Dinner", "Snack", "Hypo treat"), meal) {
                            meal = if (meal == it) null else it
                        }
                    }
                    LogTab.ACTIVITY -> {
                        ChipRow(listOf("Walk", "Run", "Bike", "Gym", "Swim", "Sport", "Yoga", "Other"), activity) { activity = it }
                        Stepper(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) }, label = "Minutes", step = 5.0)
                        QuickChips(listOf(10, 15, 20, 30, 45, 60, 90), " min", parse(minutes)) { minutes = it.toString() }
                        Segmented(
                            options = listOf("Light" to "Light", "Moderate" to "Moderate", "Hard" to "Hard"),
                            selected = intensity,
                            onSelect = { intensity = it },
                        )
                    }
                    LogTab.OTHER -> {
                        val kinds = listOf(
                            LogType.FINGERSTICK to "Fingerstick",
                            LogType.WEIGHT to "Weight",
                            LogType.KETONES to "Ketones",
                            LogType.NOTE to "Note",
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            kinds.forEach { (k, label) ->
                                FilterChip(selected = otherType == k, onClick = { otherType = k }, label = { Text(label) })
                            }
                        }
                        if (otherType != LogType.NOTE) {
                            val label = when (otherType) {
                                LogType.FINGERSTICK -> "Blood glucose (${settings.unit.label})"
                                LogType.WEIGHT -> "Weight (kg)"
                                else -> "Ketones (mmol/L)"
                            }
                            OutlinedTextField(
                                value = otherValue,
                                onValueChange = { otherValue = it },
                                label = { Text(label) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (tab == LogTab.OTHER && otherType == LogType.NOTE) "Note" else "Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (tab == LogTab.OTHER && otherType == LogType.NOTE) 3 else 1,
                )

                Text("When", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeOffsets.forEach { (m, label) ->
                        FilterChip(selected = offsetMin == m, onClick = { offsetMin = m }, label = { Text(label) })
                    }
                }

                Button(
                    onClick = { entry?.let { onSave(it); onDismiss() } },
                    enabled = entry != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Save ${tab.label.lowercase()}", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = selected == key,
                onClick = { onSelect(key) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun Stepper(value: String, onValueChange: (String) -> Unit, label: String, step: Double) {
    fun bump(d: Double) {
        val v = (value.replace(',', '.').toDoubleOrNull() ?: 0.0) + d
        onValueChange(fmtNum(v.coerceAtLeast(0.0)))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { bump(-step) }) { Icon(Icons.Filled.Remove, "Less") }
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(onClick = { bump(step) }) { Icon(Icons.Filled.Add, "More") }
    }
}

@Composable
private fun QuickChips(values: List<Int>, suffix: String, selected: Double?, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            FilterChip(selected = selected == v.toDouble(), onClick = { onPick(v) }, label = { Text("$v$suffix") })
        }
    }
}

@Composable
private fun ChipRow(values: List<String>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            FilterChip(selected = selected == v, onClick = { onPick(v) }, label = { Text(v) })
        }
    }
}
