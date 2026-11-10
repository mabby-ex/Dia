package app.dia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GlucoseReading::class, LogEntry::class], version = 1, exportSchema = false)
abstract class DiaDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
    abstract fun logDao(): LogDao

    companion object {
        fun create(context: Context): DiaDatabase =
            Room.databaseBuilder(context, DiaDatabase::class.java, "dia.db").build()
    }
}
