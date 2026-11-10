package app.dia.domain

import app.dia.data.db.GlucoseReading
import java.util.Locale
import kotlin.math.sqrt

const val MMOL_FACTOR = 18.0182

enum class GlucoseUnit(val label: String) {
    MGDL("mg/dL"),
    MMOL("mmol/L");

    fun format(mgdl: Number): String = when (this) {
        MGDL -> mgdl.toDouble().let { Math.round(it).toString() }
        MMOL -> String.format(Locale.getDefault(), "%.1f", mgdl.toDouble() / MMOL_FACTOR)
    }

    fun formatWithUnit(mgdl: Number): String = "${format(mgdl)} $label"
}

enum class RangeBand { VERY_LOW, LOW, IN_RANGE, HIGH, VERY_HIGH }

fun bandOf(mgdl: Int, low: Int, high: Int): RangeBand = when {
    mgdl < 54 -> RangeBand.VERY_LOW
    mgdl < low -> RangeBand.LOW
    mgdl <= high -> RangeBand.IN_RANGE
    mgdl <= 250 -> RangeBand.HIGH
    else -> RangeBand.VERY_HIGH
}

fun trendArrow(trend: Int?): String = when (trend) {
    1 -> "↓"
    2 -> "↘"
    3 -> "→"
    4 -> "↗"
    5 -> "↑"
    else -> ""
}

fun trendLabel(trend: Int?): String = when (trend) {
    1 -> "Falling fast"
    2 -> "Falling"
    3 -> "Steady"
    4 -> "Rising"
    5 -> "Rising fast"
    else -> ""
}

data class GlucoseSummary(
    val count: Int,
    val mean: Double,
    val sd: Double,
    val min: Int,
    val max: Int,
    /** Fraction (0..1) of readings in each band. */
    val bands: Map<RangeBand, Double>,
) {
    /** Coefficient of variation, %. Under 36% is considered stable. */
    val cv: Double get() = if (mean > 0) sd / mean * 100 else 0.0

    /** Glucose Management Indicator — an estimated A1c, %. */
    val gmi: Double get() = 3.31 + 0.02392 * mean

    val timeInRange: Double get() = bands[RangeBand.IN_RANGE] ?: 0.0

    companion object {
        fun of(readings: List<GlucoseReading>, low: Int, high: Int): GlucoseSummary? {
            if (readings.isEmpty()) return null
            val values = readings.map { it.mgdl }
            val mean = values.average()
            val sd = if (values.size > 1) {
                sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
            } else 0.0
            val counts = values.groupingBy { bandOf(it, low, high) }.eachCount()
            val bands = RangeBand.entries.associateWith { (counts[it] ?: 0).toDouble() / values.size }
            return GlucoseSummary(values.size, mean, sd, values.min(), values.max(), bands)
        }
    }
}

/** Hourly percentiles across days — a lightweight "AGP" (ambulatory glucose profile). */
data class HourlyProfile(val hour: Int, val p10: Int, val p25: Int, val median: Int, val p75: Int, val p90: Int)

fun hourlyProfile(readings: List<GlucoseReading>, zone: java.time.ZoneId): List<HourlyProfile> {
    val byHour = readings.groupBy {
        java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
    }
    return (0..23).mapNotNull { h ->
        val sorted = byHour[h]?.map { it.mgdl }?.sorted() ?: return@mapNotNull null
        fun p(q: Double) = sorted[((sorted.size - 1) * q).toInt()]
        HourlyProfile(h, p(0.10), p(0.25), p(0.5), p(0.75), p(0.90))
    }
}
