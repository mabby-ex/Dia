package app.dia

import app.dia.data.db.GlucoseReading
import app.dia.domain.GlucoseSummary
import app.dia.domain.GlucoseUnit
import app.dia.domain.RangeBand
import app.dia.domain.bandOf
import app.dia.domain.hourlyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class GlucoseStatsTest {
    private fun r(i: Int, mg: Int) = GlucoseReading(i * 900_000L, mg)

    @Test
    fun bands() {
        assertEquals(RangeBand.VERY_LOW, bandOf(50, 70, 180))
        assertEquals(RangeBand.LOW, bandOf(65, 70, 180))
        assertEquals(RangeBand.IN_RANGE, bandOf(70, 70, 180))
        assertEquals(RangeBand.IN_RANGE, bandOf(180, 70, 180))
        assertEquals(RangeBand.HIGH, bandOf(181, 70, 180))
        assertEquals(RangeBand.VERY_HIGH, bandOf(251, 70, 180))
    }

    @Test
    fun summary() {
        val s = GlucoseSummary.of(listOf(r(0, 100), r(1, 150), r(2, 200), r(3, 50)), 70, 180)!!
        assertEquals(125.0, s.mean, 0.001)
        assertEquals(0.5, s.timeInRange, 0.001)
        assertEquals(0.25, s.bands[RangeBand.HIGH]!!, 0.001)
        assertEquals(0.25, s.bands[RangeBand.VERY_LOW]!!, 0.001)
        assertEquals(3.31 + 0.02392 * 125, s.gmi, 0.0001)
        assertEquals(50, s.min)
        assertEquals(200, s.max)
    }

    @Test
    fun emptySummaryIsNull() {
        assertNull(GlucoseSummary.of(emptyList(), 70, 180))
    }

    @Test
    fun hourly() {
        val p = hourlyProfile(listOf(r(0, 100), r(1, 120), r(2, 140), r(4, 200)), ZoneId.of("UTC"))
        assertEquals(2, p.size)
        assertEquals(0, p[0].hour)
        assertEquals(120, p[0].median)
        assertEquals(1, p[1].hour)
    }

    @Test
    fun unitFormatting() {
        assertEquals("180", GlucoseUnit.MGDL.format(180))
        assertEquals("10.0", GlucoseUnit.MMOL.format(180.18).replace(',', '.'))
    }
}
