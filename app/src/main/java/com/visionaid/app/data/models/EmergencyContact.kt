package com.visionaid.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An emergency contact that receives SMS alerts when a fall is detected
 * or the user triggers SOS.
 *
 * @param name         Display name.
 * @param phoneNumber  Phone number in dialable form (E.164 recommended).
 * @param relationship Free text, e.g. "daughter", "caregiver", "doctor".
 * @param isPrimary    The primary contact is called by the "call guardian"
 *                     voice command.
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isPrimary: Boolean
)
