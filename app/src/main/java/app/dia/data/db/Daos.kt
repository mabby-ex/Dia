package app.dia.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(readings: List<GlucoseReading>)

    @Query("SELECT * FROM glucose WHERE timestamp >= :from ORDER BY timestamp")
    fun observeSince(from: Long): Flow<List<GlucoseReading>>

    @Query("SELECT * FROM glucose WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun between(from: Long, to: Long): List<GlucoseReading>

    @Query("SELECT * FROM glucose ORDER BY timestamp DESC LIMIT 2")
    fun observeLatestTwo(): Flow<List<GlucoseReading>>

    @Query("SELECT * FROM glucose ORDER BY timestamp")
    suspend fun all(): List<GlucoseReading>

    @Query("SELECT COUNT(*) FROM glucose")
    suspend fun count(): Int

    @Query("DELETE FROM glucose")
    suspend fun clear()
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntry>)

    @Delete
    suspend fun delete(entry: LogEntry)

    @Query("SELECT * FROM log_entries WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun observeSince(from: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun between(from: Long, to: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries ORDER BY timestamp")
    suspend fun all(): List<LogEntry>

    @Query("SELECT COUNT(*) FROM log_entries WHERE timestamp = :timestamp AND type = :type AND amount IS :amount")
    suspend fun countMatching(timestamp: Long, type: LogType, amount: Double?): Int
}
