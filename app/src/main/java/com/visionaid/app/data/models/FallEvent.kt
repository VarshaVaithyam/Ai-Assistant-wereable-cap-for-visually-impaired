package com.visionaid.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A fall (or emergency) event, persisted in Room for history and auditing.
 *
 * @param timestamp    Event time (epoch millis).
 * @param latitude     GPS latitude when the event occurred (0.0 if unknown).
 * @param longitude    GPS longitude when the event occurred (0.0 if unknown).
 * @param address      Reverse-geocoded address (empty string if unknown).
 * @param wasConfirmed True when the user (or the protocol) confirmed the fall.
 * @param wasCancelled True when the user cancelled the alert ("I'm fine").
 * @param sensorData   JSON snapshot of sensor readings + alert type details.
 * @param alertSent    True when at least one emergency SMS was delivered.
 */
@Entity(tableName = "fall_events")
data class FallEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val wasConfirmed: Boolean,
    val wasCancelled: Boolean,
    val sensorData: String,
    val alertSent: Boolean
)
