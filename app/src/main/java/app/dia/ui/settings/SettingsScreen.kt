@file:OptIn(ExperimentalMaterial3Api::class)

package app.dia.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.dia.data.libre.LibreLinkUpClient
import app.dia.data.settings.AppSettings
import app.dia.domain.GlucoseUnit
import app.dia.ui.components.formatAgo
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    now: Long,
    loggingIn: Boolean,
    loginError: String?,
    connections: List<LibreLinkUpClient.Connection>,
    syncing: Boolean,
    onLogin: (String, String) -> Unit,
    onChoosePatient: (LibreLinkUpClient.Connection) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onUnit: (GlucoseUnit) -> Unit,
    onTargets: (Int, Int) -> Unit,
    onInsulinNames: (String, String) -> Unit,
    onLluVersion: (String) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibreCard(settings, now, loggingIn, loginError, connections, syncing, onLogin, onChoosePatient, onSync, onDisconnect, onAutoSync)
        DisplayCard(settings, onUnit, onTargets)
        InsulinCard(settings, onInsulinNames)
        DataCard(onImport, onExport)
        AdvancedCard(settings, onLluVersion)
        Text(
            "Dia is a personal logbook, not a medical device. It is not affiliated with Abbott. " +
                "LibreLinkUp data is fetched with the same unofficial API the LibreLinkUp app uses, which may change " +
                "without notice. Always confirm readings with your sensor or meter before treating.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LibreCard(
    settings: AppSettings,
    now: Long,
    loggingIn: Boolean,
    loginError: String?,
    connections: List<LibreLinkUpClient.Connection>,
    syncing: Boolean,
    onLogin: (String, String) -> Unit,
    onChoosePatient: (LibreLinkUpClient.Connection) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
) {
    Section("FreeStyle Libre 2 · LibreLinkUp") {
        if (settings.isLibreConnected) {
            Text("Connected to ${settings.patientName ?: "patient"} as ${settings.lluEmail}")
            Text(
                if (settings.lastSync > 0) "Last sync ${formatAgo(settings.lastSync, now)}" else "Not synced yet",
                style = MaterialTheme.typography.bodySmall,
            )
            settings.lastSyncError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync every 15 min in background", Modifier.weight(1f))
                Switch(checked = settings.autoSync, onCheckedChange = onAutoSync)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSync, enabled = !syncing) { Text(if (syncing) "Syncing…" else "Sync now") }
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
            return@Section
        }

        if (connections.isNotEmpty()) {
            Text("This account follows several people. Whose readings should Dia show?")
            connections.forEach { c ->
                OutlinedButton(onClick = { onChoosePatient(c) }, modifier = Modifier.fillMaxWidth()) { Text(c.name) }
            }
            return@Section
        }

        Text(
            "How to connect:\n" +
                "1. Install LibreLinkUp and create an account (a different email from your LibreLink account works best).\n" +
                "2. In your LibreLink app: Menu → Connected Apps → LibreLinkUp → Add connection, and invite that email.\n" +
                "3. Accept the invite in LibreLinkUp.\n" +
                "4. Log in below with the LibreLinkUp email and password.",
            style = MaterialTheme.typography.bodyMedium,
        )
        var email by rememberSaveable { mutableStateOf(settings.lluEmail) }
        var password by remember { mutableStateOf("") }
        var show by remember { mutableStateOf(false) }
        LaunchedEffect(settings.lluEmail) { if (email.isBlank()) email = settings.lluEmail }
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("LibreLinkUp email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Your password is only sent to LibreView to get a session token; it is not stored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onLogin(email, password) },
            enabled = !loggingIn && email.isNotBlank() && password.isNotBlank(),
        ) {
            if (loggingIn) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Log in")
        }
    }
}

@Composable
private fun DisplayCard(settings: AppSettings, onUnit: (GlucoseUnit) -> Unit, onTargets: (Int, Int) -> Unit) {
    Section("Display") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GlucoseUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = settings.unit == u,
                    onClick = { onUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, GlucoseUnit.entries.size),
                ) { Text(u.label) }
            }
        }
        var range by remember(settings.targetLow, settings.targetHigh) {
            mutableStateOf(settings.targetLow.toFloat()..settings.targetHigh.toFloat())
        }
        Text(
            "Target range: ${settings.unit.format(range.start.roundToInt())}–${settings.unit.format(range.endInclusive.roundToInt())} ${settings.unit.label}",
        )
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            onValueChangeFinished = { onTargets(range.start.roundToInt(), range.endInclusive.roundToInt()) },
            valueRange = 54f..300f,
        )
        Text(
            "The standard range is 70–180 mg/dL (3.9–10.0 mmol/L).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsulinCard(settings: AppSettings, onInsulinNames: (String, String) -> Unit) {
    Section("Insulin names") {
        var rapid by remember(settings.rapidInsulinName) { mutableStateOf(settings.rapidInsulinName) }
        var long by remember(settings.longInsulinName) { mutableStateOf(settings.longInsulinName) }
        OutlinedTextField(rapid, { rapid = it }, label = { Text("Rapid-acting (e.g. Novorapid, Humalog)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(long, { long = it }, label = { Text("Long-acting (e.g. Lantus, Tresiba)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(
            onClick = { onInsulinNames(rapid.trim().ifEmpty { "Rapid" }, long.trim().ifEmpty { "Long" }) },
            enabled = rapid != settings.rapidInsulinName || long != settings.longInsulinName,
        ) { Text("Save names") }
    }
}

@Composable
private fun DataCard(onImport: (android.net.Uri) -> Unit, onExport: (android.net.Uri) -> Unit) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(onExport) }
    Section("Your data") {
        Text(
            "Import a CSV from libreview.com (Glucose History → Download glucose data) to backfill months of readings, " +
                "plus any insulin/carbs you logged in LibreLink.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) }) {
                Text("Import LibreView CSV")
            }
            OutlinedButton(onClick = { exporter.launch("dia-export-${LocalDate.now()}.csv") }) { Text("Export CSV") }
        }
    }
}

@Composable
private fun AdvancedCard(settings: AppSettings, onLluVersion: (String) -> Unit) {
    Section("Advanced") {
        var version by remember(settings.lluVersion) { mutableStateOf(settings.lluVersion) }
        Text(
            "LibreView sometimes rejects requests from old app versions. If login fails with a version error, " +
                "enter the current LibreLinkUp version number from the Play Store.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(version, { version = it }, label = { Text("LibreLinkUp version") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { onLluVersion(version) }, enabled = version != settings.lluVersion) { Text("Save") }
        }
    }
}
