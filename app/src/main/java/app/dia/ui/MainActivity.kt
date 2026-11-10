@file:OptIn(ExperimentalMaterial3Api::class)

package app.dia.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.dia.ui.history.HistoryScreen
import app.dia.ui.home.HomeScreen
import app.dia.ui.log.LogTab
import app.dia.ui.log.QuickLogSheet
import app.dia.ui.settings.SettingsScreen
import app.dia.ui.stats.StatsScreen
import app.dia.ui.theme.DiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DiaTheme { DiaRoot() } }
    }
}

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    HISTORY("history", "Log", Icons.AutoMirrored.Filled.List),
    STATS("stats", "Stats", Icons.Filled.Insights),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
private fun DiaRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Dest.HOME.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var sheetTab by remember { mutableStateOf<LogTab?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    fun go(d: Dest) = nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Dest.entries.firstOrNull { it.route == route }?.let { if (it == Dest.HOME) "Dia" else it.label } ?: "Dia") },
                actions = {
                    if (settings.isLibreConnected) {
                        IconButton(onClick = { vm.syncNow() }, enabled = !syncing) { Icon(Icons.Filled.Refresh, "Sync") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = route == d.route,
                        onClick = { go(d) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (route == Dest.HOME.route || route == Dest.HISTORY.route) {
                FloatingActionButton(onClick = { sheetTab = LogTab.INSULIN }) { Icon(Icons.Filled.Add, "Log") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Dest.HOME.route) {
            composable(Dest.HOME.route) {
                val latest by vm.latest.collectAsStateWithLifecycle()
                val glucose by vm.glucose24h.collectAsStateWithLifecycle()
                val logs by vm.logs24h.collectAsStateWithLifecycle()
                val today by vm.todayLogs.collectAsStateWithLifecycle()
                HomeScreen(
                    settings = settings, now = now, latest = latest, glucose24h = glucose, logs24h = logs,
                    todayLogs = today, syncing = syncing, onRefresh = { vm.syncNow() },
                    onQuickLog = { sheetTab = it }, onOpenSettings = { go(Dest.SETTINGS) }, contentPadding = padding,
                )
            }
            composable(Dest.HISTORY.route) {
                val entries by vm.history.collectAsStateWithLifecycle()
                HistoryScreen(
                    settings = settings,
                    entries = entries,
                    onDelete = { e ->
                        vm.deleteLog(e)
                        scope.launch {
                            val r = snackbar.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
                        }
                    },
                    onLoadMore = vm::loadMoreHistory,
                    contentPadding = padding,
                )
            }
            composable(Dest.STATS.route) {
                val stats by vm.stats.collectAsStateWithLifecycle()
                StatsScreen(settings, stats, vm::setStatsDays, padding)
            }
            composable(Dest.SETTINGS.route) {
                val loggingIn by vm.loggingIn.collectAsStateWithLifecycle()
                val loginError by vm.loginError.collectAsStateWithLifecycle()
                val connections by vm.connections.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings, now = now, loggingIn = loggingIn, loginError = loginError,
                    connections = connections, syncing = syncing,
                    onLogin = { e, p -> vm.login(e, p) },
                    onChoosePatient = { vm.choosePatient(it) },
                    onSync = { vm.syncNow() },
                    onDisconnect = { vm.disconnect() },
                    onAutoSync = { vm.setAutoSync(it) },
                    onUnit = { vm.setUnit(it) },
                    onTargets = { l, h -> vm.setTargets(l, h) },
                    onInsulinNames = { r, l -> vm.setInsulinNames(r, l) },
                    onLluVersion = { vm.setLluVersion(it) },
                    onImport = { vm.importCsv(it) },
                    onExport = { vm.exportCsv(it) },
                    contentPadding = padding,
                )
            }
        }
    }

    sheetTab?.let { tab ->
        QuickLogSheet(
            initialTab = tab,
            settings = settings,
            onDismiss = { sheetTab = null },
            onSave = { vm.addLog(it) },
        )
    }
}
