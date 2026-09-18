package com.visionaid.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.data.models.FallEvent

/**
 * Room database holding emergency contacts and fall event history.
 * Provided as a singleton by [com.visionaid.app.di.DatabaseModule].
 */
@Database(
    entities = [EmergencyContact::class, FallEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun fallLogDao(): FallLogDao
}
