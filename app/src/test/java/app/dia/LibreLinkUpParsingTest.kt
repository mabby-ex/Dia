package app.dia

import app.dia.data.libre.LibreLinkUpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LibreLinkUpParsingTest {

    @Test
    fun parsesFactoryTimestampAsUtc() {
        val ts = LibreLinkUpClient.parseFactoryTimestamp("11/10/2026 5:26:12 PM")
        assertEquals(Instant.parse("2026-11-10T17:26:12Z").toEpochMilli(), ts)
    }

    @Test
    fun rejectsGarbageTimestamp() {
        assertNull(LibreLinkUpClient.parseFactoryTimestamp("not a date"))
    }

    @Test
    fun parsesGraphResponse() {
        val json = Json.parseToJsonElement(
            """
            {"status":0,"data":{
              "connection":{"patientId":"p1","glucoseMeasurement":{
                 "FactoryTimestamp":"11/10/2026 6:00:00 AM","Timestamp":"11/10/2026 7:00:00 AM",
                 "ValueInMgPerDl":142,"TrendArrow":4,"Value":142}},
              "graphData":[
                {"FactoryTimestamp":"11/10/2026 5:45:00 AM","ValueInMgPerDl":130,"Value":130},
                {"FactoryTimestamp":"11/10/2026 5:50:00 AM","ValueInMgPerDl":135.4,"Value":135}
              ]}}
            """.trimIndent()
        ) as JsonObject
        val readings = LibreLinkUpClient.parseGraph(json)
        assertEquals(3, readings.size)
        assertEquals(130, readings[0].mgdl)
        assertEquals(135, readings[1].mgdl)
        assertEquals(142, readings[2].mgdl)
        assertEquals(4, readings[2].trend)
        assertEquals(Instant.parse("2026-11-10T06:00:00Z").toEpochMilli(), readings[2].timestamp)
    }

    @Test
    fun accountIdIsSha256Hex() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            LibreLinkUpClient.sha256("hello"),
        )
    }

    @Test
    fun regionUrl() {
        assertEquals("https://api-eu.libreview.io", LibreLinkUpClient.regionUrl("EU"))
    }
}
