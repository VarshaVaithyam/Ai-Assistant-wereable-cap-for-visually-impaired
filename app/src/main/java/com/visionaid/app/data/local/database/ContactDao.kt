package com.visionaid.app.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.visionaid.app.data.models.EmergencyContact
import kotlinx.coroutines.flow.Flow

/**
 * CRUD access to the emergency contacts table.
 */
@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)

    /** Live list of all contacts ordered by primary first, then name. */
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    fun getAll(): Flow<List<EmergencyContact>>

    /** One-shot snapshot (used when sending alerts). */
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    suspend fun getAllOnce(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimary(): EmergencyContact?

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun count(): Int
}
