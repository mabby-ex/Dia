package app.dia.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dia.DiaApp
import app.dia.data.db.GlucoseReading
import app.dia.data.db.LogEntry
import app.dia.data.libre.LibreLinkUpClient
import app.dia.data.settings.AppSettings
import app.dia.domain.GlucoseUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

data class StatsData(
    val days: Int,
    val readings: List<GlucoseReading>,
    val logs: List<LogEntry>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as DiaApp).repository

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Ticks every 30s so "x min ago" labels and time windows stay fresh. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val settings: StateFlow<AppSettings> = repo.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Window start, refreshed every 10 minutes so the 24h window slides. */
    private val windowStart = now.map { (it - DAY) / (10 * 60_000L) * (10 * 60_000L) }

    val glucose24h: StateFlow<List<GlucoseReading>> = windowStart
        .flatMapLatest { repo.glucose.observeSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latest: StateFlow<List<GlucoseReading>> = repo.glucose.observeLatestTwo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs24h: StateFlow<List<LogEntry>> = windowStart
        .flatMapLatest { repo.logs.observeSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayLogs: StateFlow<List<LogEntry>> = now
        .map { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .flatMapLatest { repo.logs.observeSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val historyDays = MutableStateFlow(30)
    val history: StateFlow<List<LogEntry>> = historyDays
        .flatMapLatest { repo.logs.observeSince(System.currentTimeMillis() - it * DAY) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadMoreHistory() {
        historyDays.value = historyDays.value + 60
    }

    private val statsDays = MutableStateFlow(14)
    private val _stats = MutableStateFlow<StatsData?>(null)
    val stats: StateFlow<StatsData?> = _stats.asStateFlow()

    init {
        syncNow(quiet = true)
        viewModelScope.launch {
            // Recompute stats when the period changes or new data lands.
            combine(statsDays, latest, todayLogs) { d, _, _ -> d }.collect { d -> loadStats(d) }
        }
    }

    fun setStatsDays(days: Int) {
        statsDays.value = days
    }

    private suspend fun loadStats(days: Int) {
        val to = System.currentTimeMillis()
        val from = to - days * DAY
        val readings = repo.glucose.between(from, to + 1)
        val logs = repo.logs.between(from, to + 1)
        _stats.value = StatsData(days, readings, logs)
    }

    fun syncNow(quiet: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            if (!repo.settings.current().isLibreConnected) {
                if (!quiet) _messages.tryEmit("Connect LibreLinkUp in Settings first")
                return@launch
            }
            _syncing.value = true
            val r = repo.syncLibre()
            _syncing.value = false
            if (!quiet) {
                _messages.tryEmit(
                    r.fold({ "Synced $it readings" }, { it.message ?: "Sync failed" })
                )
            }
        }
    }

    fun addLog(entry: LogEntry) = viewModelScope.launch {
        repo.addLog(entry)
        _messages.tryEmit("Logged")
    }

    private var lastDeleted: LogEntry? = null

    fun deleteLog(entry: LogEntry) = viewModelScope.launch {
        repo.deleteLog(entry)
        lastDeleted = entry
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeleted?.let { repo.restoreLog(it) }
        lastDeleted = null
    }

    // --- LibreLinkUp account ---

    private val _connections = MutableStateFlow<List<LibreLinkUpClient.Connection>>(emptyList())
    val connections: StateFlow<List<LibreLinkUpClient.Connection>> = _connections.asStateFlow()

    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun login(email: String, password: String) = viewModelScope.launch {
        _loggingIn.value = true
        _loginError.value = null
        try {
            val list = repo.connectLibre(email, password)
            _connections.value = if (list.size > 1) list else emptyList()
            if (list.size == 1) {
                _messages.tryEmit("Connected to ${list[0].name}")
                syncNow()
            }
        } catch (e: Exception) {
            _loginError.value = e.message ?: "Login failed"
        } finally {
            _loggingIn.value = false
        }
    }

    fun choosePatient(c: LibreLinkUpClient.Connection) = viewModelScope.launch {
        repo.choosePatient(c)
        _connections.value = emptyList()
        syncNow()
    }

    fun disconnect() = viewModelScope.launch {
        repo.disconnectLibre()
        _messages.tryEmit("Disconnected from LibreLinkUp")
    }

    // --- Preferences ---

    fun setUnit(u: GlucoseUnit) = viewModelScope.launch { repo.settings.setUnit(u) }
    fun setTargets(low: Int, high: Int) = viewModelScope.launch { repo.settings.setTargets(low, high) }
    fun setInsulinNames(r: String, l: String) = viewModelScope.launch { repo.settings.setInsulinNames(r, l) }
    fun setLluVersion(v: String) = viewModelScope.launch { repo.settings.setLluVersion(v) }
    fun setAutoSync(b: Boolean) = viewModelScope.launch { repo.settings.setAutoSync(b) }

    // --- Import / export ---

    fun importCsv(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
            } ?: throw IllegalArgumentException("Couldn't open file")
            val s = repo.importLibreViewCsv(text)
            _messages.tryEmit("Imported ${s.readings} readings and ${s.entries} log entries")
            loadStats(statsDays.value)
        } catch (e: Exception) {
            _messages.tryEmit("Import failed: ${e.message}")
        }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        try {
            val csv = repo.exportCsv()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray())
                } ?: throw IllegalArgumentException("Couldn't write file")
            }
            _messages.tryEmit("Exported")
        } catch (e: Exception) {
            _messages.tryEmit("Export failed: ${e.message}")
        }
    }
}
