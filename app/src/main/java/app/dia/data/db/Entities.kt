package app.dia.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One CGM reading. Timestamp is epoch millis, rounded to the minute so imports de-duplicate. */
@Entity(tableName = "glucose")
data class GlucoseReading(
    @PrimaryKey val timestamp: Long,
    val mgdl: Int,
    /** LibreLinkUp trend arrow: 1 falling fast … 3 stable … 5 rising fast. Null if unknown. */
    val trend: Int? = null,
    val source: String = SOURCE_LIBRE,
) {
    companion object {
        const val SOURCE_LIBRE = "librelinkup"
        const val SOURCE_CSV = "csv"
    }
}

enum class LogType { INSULIN, FOOD, ACTIVITY, FINGERSTICK, WEIGHT, KETONES, NOTE }

/**
 * A manual log entry. Field meaning depends on [type]:
 *  - INSULIN: amount = units, subtype = "rapid" | "long"
 *  - FOOD: amount = carbs (g), subtype = meal name
 *  - ACTIVITY: durationMin = minutes, subtype = activity, intensity in [intensity]
 *  - FINGERSTICK: amount = mg/dL
 *  - WEIGHT: amount = kg
 *  - KETONES: amount = mmol/L
 *  - NOTE: just [note]
 */
@Entity(tableName = "log_entries", indices = [Index("timestamp")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: LogType,
    val amount: Double? = null,
    val subtype: String? = null,
    val durationMin: Int? = null,
    val intensity: String? = null,
    val note: String? = null,
)

object InsulinKind {
    const val RAPID = "rapid"
    const val LONG = "long"
}
