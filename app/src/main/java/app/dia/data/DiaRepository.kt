package app.dia.data

import app.dia.data.db.DiaDatabase
import app.dia.data.db.GlucoseReading
import app.dia.data.db.LogEntry
import app.dia.data.libre.LibreException
import app.dia.data.libre.LibreLinkUpClient
import app.dia.data.libre.LibreViewCsvParser
import app.dia.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex

class DiaRepository(
    private val db: DiaDatabase,
    val settings: SettingsRepository,
    private val libre: LibreLinkUpClient,
) {
    val glucose = db.glucoseDao()
    val logs = db.logDao()
    private val syncLock = Mutex()

    /** Logs in and returns the people this LibreLinkUp account follows. */
    suspend fun connectLibre(email: String, password: String): List<LibreLinkUpClient.Connection> {
        val s = settings.current()
        val session = libre.login(email, password, s.lluVersion)
        settings.saveSession(email, session)
        val connections = libre.connections(session, s.lluVersion)
        if (connections.isEmpty()) {
            throw LibreException(
                "Logged in, but nobody is sharing with this account yet. In the LibreLink app, " +
                    "go to Menu → Connected Apps → LibreLinkUp and invite this email."
            )
        }
        if (connections.size == 1) {
            settings.setPatient(connections[0].patientId, connections[0].name)
        }
        return connections
    }

    suspend fun choosePatient(c: LibreLinkUpClient.Connection) = settings.setPatient(c.patientId, c.name)

    /** Pulls the latest readings from LibreLinkUp. Returns number of readings received. */
    suspend fun syncLibre(): Result<Int> {
        syncLock.lock()
        try {
            return doSync()
        } finally {
            syncLock.unlock()
        }
    }

    private suspend fun doSync(): Result<Int> {
        val s = settings.current()
        val session = s.session
        val patient = s.patientId
        if (session == null || patient == null) return Result.failure(LibreException("Not connected"))
        if (session.expiresAt in 1 until System.currentTimeMillis()) {
            val msg = "Session expired. Please log in again in Settings."
            settings.setSyncResult(System.currentTimeMillis(), msg)
            return Result.failure(LibreException(msg))
        }
        return try {
            val readings = libre.graph(session, s.lluVersion, patient)
            glucose.upsert(readings.map {
                GlucoseReading(it.timestamp / 60_000L * 60_000L, it.mgdl, it.trend, GlucoseReading.SOURCE_LIBRE)
            })
            settings.setSyncResult(System.currentTimeMillis(), null)
            Result.success(readings.size)
        } catch (e: Exception) {
            settings.setSyncResult(System.currentTimeMillis(), e.message ?: "Sync failed")
            Result.failure(e)
        }
    }

    suspend fun addLog(entry: LogEntry): Long = logs.insert(entry)
    suspend fun deleteLog(entry: LogEntry) = logs.delete(entry)
    suspend fun restoreLog(entry: LogEntry) = logs.insert(entry)

    data class ImportSummary(val readings: Int, val entries: Int, val skipped: Int)

    suspend fun importLibreViewCsv(text: String): ImportSummary {
        val result = LibreViewCsvParser.parse(text)
        glucose.upsert(result.readings)
        val newEntries = result.entries.filter {
            logs.countMatching(it.timestamp, it.type, it.amount) == 0
        }
        logs.insertAll(newEntries)
        return ImportSummary(result.readings.size, newEntries.size, result.skippedRows)
    }

    suspend fun exportCsv(): String {
        val sb = StringBuilder("timestamp_iso,kind,type,value,unit,detail,duration_min,intensity,note\n")
        fun q(s: String?) = if (s == null) "" else "\"" + s.replace("\"", "\"\"") + "\""
        val rows = mutableListOf<Pair<Long, String>>()
        glucose.all().forEach {
            rows += it.timestamp to "glucose,${it.source},${it.mgdl},mg/dL,${it.trend ?: ""},,,"
        }
        logs.all().forEach {
            val unit = when (it.type) {
                app.dia.data.db.LogType.INSULIN -> "U"
                app.dia.data.db.LogType.FOOD -> "g carbs"
                app.dia.data.db.LogType.FINGERSTICK -> "mg/dL"
                app.dia.data.db.LogType.WEIGHT -> "kg"
                app.dia.data.db.LogType.KETONES -> "mmol/L"
                else -> ""
            }
            rows += it.timestamp to "log,${it.type.name.lowercase()},${it.amount ?: ""},$unit,${q(it.subtype)}," +
                "${it.durationMin ?: ""},${q(it.intensity)},${q(it.note)}"
        }
        rows.sortedBy { it.first }.forEach { (ts, rest) ->
            sb.append(java.time.Instant.ofEpochMilli(ts)).append(',').append(rest).append('\n')
        }
        return sb.toString()
    }

    suspend fun disconnectLibre() = settings.logout()
}
