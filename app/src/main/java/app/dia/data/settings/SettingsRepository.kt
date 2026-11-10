package app.dia.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.dia.data.libre.LibreLinkUpClient
import app.dia.domain.GlucoseUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class LibreSession(
    val token: String,
    val expiresAt: Long,
    val accountId: String,
    val baseUrl: String,
)

data class AppSettings(
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val targetLow: Int = 70,
    val targetHigh: Int = 180,
    val rapidInsulinName: String = "Rapid",
    val longInsulinName: String = "Long",
    val lluEmail: String = "",
    val lluVersion: String = LibreLinkUpClient.DEFAULT_VERSION,
    val session: LibreSession? = null,
    val patientId: String? = null,
    val patientName: String? = null,
    val lastSync: Long = 0,
    val lastSyncError: String? = null,
    val autoSync: Boolean = true,
) {
    val isLibreConnected: Boolean get() = session != null && patientId != null
}

class SettingsRepository(private val context: Context) {

    private object K {
        val unit = stringPreferencesKey("unit")
        val low = intPreferencesKey("target_low")
        val high = intPreferencesKey("target_high")
        val rapidName = stringPreferencesKey("rapid_name")
        val longName = stringPreferencesKey("long_name")
        val email = stringPreferencesKey("llu_email")
        val version = stringPreferencesKey("llu_version")
        val token = stringPreferencesKey("llu_token")
        val expires = longPreferencesKey("llu_expires")
        val accountId = stringPreferencesKey("llu_account_id")
        val baseUrl = stringPreferencesKey("llu_base_url")
        val patientId = stringPreferencesKey("llu_patient_id")
        val patientName = stringPreferencesKey("llu_patient_name")
        val lastSync = longPreferencesKey("last_sync")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val autoSync = booleanPreferencesKey("auto_sync")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val token = this[K.token]
        val session = if (token.isNullOrBlank()) null else LibreSession(
            token = token,
            expiresAt = this[K.expires] ?: 0L,
            accountId = this[K.accountId] ?: "",
            baseUrl = this[K.baseUrl] ?: LibreLinkUpClient.DEFAULT_BASE_URL,
        )
        val d = AppSettings()
        return AppSettings(
            unit = runCatching { GlucoseUnit.valueOf(this[K.unit] ?: "") }.getOrDefault(d.unit),
            targetLow = this[K.low] ?: d.targetLow,
            targetHigh = this[K.high] ?: d.targetHigh,
            rapidInsulinName = this[K.rapidName] ?: d.rapidInsulinName,
            longInsulinName = this[K.longName] ?: d.longInsulinName,
            lluEmail = this[K.email] ?: "",
            lluVersion = this[K.version]?.takeIf { it.isNotBlank() } ?: d.lluVersion,
            session = session,
            patientId = this[K.patientId],
            patientName = this[K.patientName],
            lastSync = this[K.lastSync] ?: 0L,
            lastSyncError = this[K.lastSyncError],
            autoSync = this[K.autoSync] ?: true,
        )
    }

    suspend fun setUnit(unit: GlucoseUnit) = context.dataStore.edit { it[K.unit] = unit.name }

    suspend fun setTargets(low: Int, high: Int) = context.dataStore.edit {
        it[K.low] = low
        it[K.high] = high
    }

    suspend fun setInsulinNames(rapid: String, long: String) = context.dataStore.edit {
        it[K.rapidName] = rapid
        it[K.longName] = long
    }

    suspend fun setLluVersion(version: String) = context.dataStore.edit { it[K.version] = version.trim() }

    suspend fun setAutoSync(enabled: Boolean) = context.dataStore.edit { it[K.autoSync] = enabled }

    suspend fun saveSession(email: String, session: LibreSession) = context.dataStore.edit {
        it[K.email] = email
        it[K.token] = session.token
        it[K.expires] = session.expiresAt
        it[K.accountId] = session.accountId
        it[K.baseUrl] = session.baseUrl
    }

    suspend fun setPatient(id: String, name: String) = context.dataStore.edit {
        it[K.patientId] = id
        it[K.patientName] = name
    }

    suspend fun setSyncResult(time: Long, error: String?) = context.dataStore.edit {
        if (error == null) {
            it[K.lastSync] = time
            it.remove(K.lastSyncError)
        } else {
            it[K.lastSyncError] = error
        }
    }

    suspend fun logout() = context.dataStore.edit {
        it.remove(K.token)
        it.remove(K.expires)
        it.remove(K.accountId)
        it.remove(K.baseUrl)
        it.remove(K.patientId)
        it.remove(K.patientName)
        it.remove(K.lastSyncError)
    }
}
