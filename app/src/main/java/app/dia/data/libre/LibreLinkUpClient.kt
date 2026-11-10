package app.dia.data.libre

import app.dia.data.settings.LibreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class LibreException(message: String) : Exception(message)

/**
 * Minimal client for the (unofficial) LibreLinkUp follower API — the same API the
 * LibreLinkUp app uses. Your FreeStyle Libre 2 phone app (LibreLink) uploads readings to
 * LibreView, and a LibreLinkUp "follower" account can read them back from here.
 */
class LibreLinkUpClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    data class Connection(val patientId: String, val name: String)
    data class Reading(val timestamp: Long, val mgdl: Int, val trend: Int?)

    suspend fun login(
        email: String,
        password: String,
        version: String,
        baseUrl: String = DEFAULT_BASE_URL,
        redirects: Int = 0,
    ): LibreSession {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val json = request(baseUrl, "/llu/auth/login", version, session = null, postBody = body)
        val data = json.obj("data")

        if (data?.get("redirect")?.prim()?.booleanOrNull == true) {
            val region = data["region"]?.prim()?.content
                ?: throw LibreException("Server asked to redirect but gave no region")
            if (redirects > 2) throw LibreException("Too many region redirects")
            return login(email, password, version, regionUrl(region), redirects + 1)
        }

        when (json["status"]?.prim()?.intOrNull) {
            0 -> Unit
            2 -> throw LibreException("Wrong email or password")
            4 -> throw LibreException(
                "LibreView needs you to accept updated terms. Open the LibreLinkUp app, " +
                    "log in once and accept them, then try again."
            )
            else -> throw LibreException(errorMessage(json) ?: "Login failed")
        }

        val userId = data?.obj("user")?.get("id")?.prim()?.content
            ?: throw LibreException(errorMessage(json) ?: "Unexpected login response")
        val ticket = data.obj("authTicket") ?: throw LibreException("No auth ticket in response")
        val token = ticket["token"]?.prim()?.content ?: throw LibreException("No token in response")
        val expiresSec = ticket["expires"]?.prim()?.longOrNull ?: 0L
        return LibreSession(
            token = token,
            expiresAt = expiresSec * 1000L,
            accountId = sha256(userId),
            baseUrl = baseUrl,
        )
    }

    suspend fun connections(session: LibreSession, version: String): List<Connection> {
        val json = request(session.baseUrl, "/llu/connections", version, session)
        val arr = json["data"] as? JsonArray ?: throw LibreException(errorMessage(json) ?: "No connections")
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["patientId"]?.prim()?.content ?: return@mapNotNull null
            val name = listOfNotNull(o["firstName"]?.prim()?.content, o["lastName"]?.prim()?.content)
                .joinToString(" ").ifBlank { "Patient" }
            Connection(id, name)
        }
    }

    /** Roughly the last 12 hours of readings plus the latest measurement. */
    suspend fun graph(session: LibreSession, version: String, patientId: String): List<Reading> {
        val json = request(session.baseUrl, "/llu/connections/$patientId/graph", version, session)
        return parseGraph(json)
    }

    private suspend fun request(
        baseUrl: String,
        path: String,
        version: String,
        session: LibreSession?,
        postBody: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("product", "llu.android")
            .header("version", version)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
        if (session != null) {
            builder.header("Authorization", "Bearer ${session.token}")
            builder.header("Account-Id", session.accountId)
        }
        if (postBody != null) {
            builder.post(postBody.toRequestBody("application/json".toMediaType()))
        }
        try {
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when (resp.code) {
                    401 -> throw LibreException("Session expired. Please log in again in Settings.")
                    403 -> throw LibreException(
                        "LibreView rejected the request (403). Try updating the app version in Settings → Advanced."
                    )
                    429 -> throw LibreException("Too many requests. LibreView is rate limiting — try again later.")
                    430 -> throw LibreException("Account temporarily locked after too many attempts. Wait and retry.")
                }
                val parsed = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                if (parsed == null) {
                    throw LibreException("Unexpected response from LibreView (HTTP ${resp.code})")
                }
                if (!resp.isSuccessful && parsed["status"]?.prim()?.intOrNull != 2) {
                    throw LibreException(errorMessage(parsed) ?: "HTTP ${resp.code}")
                }
                parsed
            }
        } catch (e: LibreException) {
            throw e
        } catch (e: Exception) {
            throw LibreException("Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.libreview.io"
        const val DEFAULT_VERSION = "4.16.0"

        private val timestampFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

        fun regionUrl(region: String): String = "https://api-${region.lowercase(Locale.US)}.libreview.io"

        fun sha256(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }

        /** LibreLinkUp "FactoryTimestamp" is UTC in US format, e.g. "11/10/2026 5:26:12 AM". */
        fun parseFactoryTimestamp(value: String): Long? = runCatching {
            LocalDateTime.parse(value.trim().uppercase(Locale.US), timestampFormat)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()

        fun parseGraph(json: JsonObject): List<Reading> {
            val data = json.obj("data") ?: return emptyList()
            val readings = mutableListOf<Reading>()
            (data["graphData"] as? JsonArray)?.forEach { el ->
                parseMeasurement(el as? JsonObject)?.let(readings::add)
            }
            parseMeasurement(data.obj("connection")?.obj("glucoseMeasurement"))?.let(readings::add)
            return readings
        }

        private fun parseMeasurement(o: JsonObject?): Reading? {
            if (o == null) return null
            val ts = (o["FactoryTimestamp"] ?: o["Timestamp"])?.prim()?.content?.let(::parseFactoryTimestamp)
                ?: return null
            val mgdl = o["ValueInMgPerDl"]?.prim()?.doubleOrNull ?: return null
            val trend = o["TrendArrow"]?.prim()?.intOrNull
            return Reading(ts, Math.round(mgdl).toInt(), trend)
        }

        private fun errorMessage(json: JsonObject): String? =
            json.obj("error")?.get("message")?.prim()?.content
                ?: json.obj("data")?.get("message")?.prim()?.content
                ?: (json["message"] as? JsonPrimitive)?.content

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonElement.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
    }
}
