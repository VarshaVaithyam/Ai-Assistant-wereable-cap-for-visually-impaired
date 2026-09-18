package com.visionaid.app.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.visionaid.app.data.models.FallEvent
import kotlinx.coroutines.flow.Flow

/**
 * Access to the fall/emergency event history.
 */
@Dao
interface FallLogDao {

    @Insert
    suspend fun insert(event: FallEvent): Long

    /** Live list, newest first — drives the Emergency screen history. */
    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FallEvent>>

    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<FallEvent>

    @Query(
        "SELECT * FROM fall_events WHERE timestamp BETWEEN :startMillis AND :endMillis " +
            "ORDER BY timestamp DESC"
    )
    suspend fun getByDateRange(startMillis: Long, endMillis: Long): List<FallEvent>
}
