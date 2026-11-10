package app.dia.data.libre

import app.dia.data.db.GlucoseReading
import app.dia.data.db.InsulinKind
import app.dia.data.db.LogEntry
import app.dia.data.db.LogType
import app.dia.domain.MMOL_FACTOR
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses the "Download glucose data" CSV export from LibreView (libreview.com) or the
 * LibreLink app. Column headers and date format vary by region, so this is deliberately lenient.
 */
object LibreViewCsvParser {

    data class Result(
        val readings: List<GlucoseReading>,
        val entries: List<LogEntry>,
        val skippedRows: Int,
    )

    private val datePatterns = listOf(
        "MM-dd-yyyy hh:mm a", "dd-MM-yyyy HH:mm", "MM-dd-yyyy HH:mm",
        "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm", "MM/dd/yyyy hh:mm a",
        "MM/dd/yyyy HH:mm", "dd.MM.yyyy HH:mm", "yyyy/MM/dd HH:mm",
        "MM-dd-yyyy h:mm a", "MM/dd/yyyy h:mm a",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst {
            val l = it.lowercase(Locale.US)
            l.contains("timestamp") && (l.contains("record type") || l.contains("glucose"))
        }
        if (headerIndex < 0) throw IllegalArgumentException("This doesn't look like a LibreView CSV export")

        val headerLine = lines[headerIndex]
        val delimiter = listOf(',', ';', '\t').maxBy { d -> headerLine.count { it == d } }
        val header = splitCsv(headerLine, delimiter).map { it.trim().lowercase(Locale.US) }

        fun col(vararg needles: String): Int =
            header.indexOfFirst { h -> needles.all { h.contains(it) } }

        val tsCol = col("timestamp")
        val typeCol = col("record type")
        val historicCol = col("historic glucose")
        val scanCol = col("scan glucose")
        val rapidCol = col("rapid-acting insulin", "units")
        val longCol = col("long-acting insulin", "units")
        val carbsCol = col("carbohydrates", "grams")
        val notesCol = col("notes")
        val stripCol = col("strip glucose")
        val ketoneCol = col("ketone")
        val historicMmol = historicCol >= 0 && header[historicCol].contains("mmol")
        val scanMmol = scanCol >= 0 && header[scanCol].contains("mmol")
        val stripMmol = stripCol >= 0 && header[stripCol].contains("mmol")
        if (tsCol < 0) throw IllegalArgumentException("No timestamp column found")

        val rows = lines.drop(headerIndex + 1).map { splitCsv(it, delimiter) }
        val formatter = pickFormatter(rows.mapNotNull { it.getOrNull(tsCol)?.trim() }.take(500))
            ?: throw IllegalArgumentException("Couldn't understand the date format in this file")

        val readings = LinkedHashMap<Long, GlucoseReading>()
        val entries = mutableListOf<LogEntry>()
        var skipped = 0

        fun num(row: List<String>, c: Int): Double? =
            if (c < 0) null else row.getOrNull(c)?.trim()?.replace(',', '.')?.toDoubleOrNull()

        for (row in rows) {
            val ts = row.getOrNull(tsCol)?.trim()?.let { parseDate(it, formatter, zone) }
            if (ts == null) {
                skipped++
                continue
            }
            val minute = ts / 60_000L * 60_000L
            val type = num(row, typeCol)?.toInt()

            val historic = num(row, historicCol)?.let { if (historicMmol) it * MMOL_FACTOR else it }
            val scan = num(row, scanCol)?.let { if (scanMmol) it * MMOL_FACTOR else it }
            val glucose = when (type) {
                0 -> historic
                1 -> scan
                null -> historic ?: scan
                else -> null
            }
            if (glucose != null && glucose > 0) {
                readings[minute] = GlucoseReading(minute, Math.round(glucose).toInt(), null, GlucoseReading.SOURCE_CSV)
            }

            num(row, rapidCol)?.takeIf { it > 0 }?.let {
                entries += LogEntry(timestamp = minute, type = LogType.INSULIN, amount = it, subtype = InsulinKind.RAPID)
            }
            num(row, longCol)?.takeIf { it > 0 }?.let {
                entries += LogEntry(timestamp = minute, type = LogType.INSULIN, amount = it, subtype = InsulinKind.LONG)
            }
            num(row, carbsCol)?.takeIf { it > 0 }?.let {
                entries += LogEntry(timestamp = minute, type = LogType.FOOD, amount = it)
            }
            num(row, stripCol)?.takeIf { it > 0 }?.let {
                val mg = if (stripMmol) it * MMOL_FACTOR else it
                entries += LogEntry(timestamp = minute, type = LogType.FINGERSTICK, amount = Math.round(mg).toDouble())
            }
            num(row, ketoneCol)?.takeIf { it > 0 }?.let {
                entries += LogEntry(timestamp = minute, type = LogType.KETONES, amount = it)
            }
            if (notesCol >= 0) {
                row.getOrNull(notesCol)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    entries += LogEntry(timestamp = minute, type = LogType.NOTE, note = it)
                }
            }
        }
        return Result(readings.values.toList(), entries, skipped)
    }

    /** Picks the date format that parses the most sample values (handles dd-MM vs MM-dd ambiguity). */
    private fun pickFormatter(samples: List<String>): DateTimeFormatter? {
        if (samples.isEmpty()) return null
        return datePatterns
            .map { f -> f to samples.count { parseDate(it, f, ZoneId.of("UTC")) != null } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun parseDate(value: String, f: DateTimeFormatter, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(value.uppercase(Locale.US), f).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** Quote-aware single-line CSV split. */
    fun splitCsv(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    out += sb.toString(); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
