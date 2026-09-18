package com.visionaid.app.domain.usecases

import com.visionaid.app.data.local.database.FallLogDao
import com.visionaid.app.data.local.preferences.AppPreferences
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.data.models.FallEvent
import com.visionaid.app.data.repository.ContactRepository
import com.visionaid.app.domain.models.AlertType
import com.visionaid.app.domain.models.LocationInfo
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.LocationManager
import com.visionaid.app.utils.SmsHelper
import org.json.JSONObject
import javax.inject.Inject

/**
 * Sends the emergency SMS to every contact and logs the event (spec 9.2/9.3
 * + 6.4 steps 4-6):
 *
 *  1. resolve the freshest GPS location
 *  2. format the message (name, alert type, maps link, time, battery)
 *  3. send to all contacts (with retries)
 *  4. persist a FallEvent row
 *
 * @return the logged event (null when even logging failed), so callers can
 *         report "sent to N contacts" accurately.
 */
class SendEmergencyAlertUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val locationManager: LocationManager,
    private val smsHelper: SmsHelper,
    private val fallLogDao: FallLogDao,
    private val preferences: AppPreferences
) {

    suspend operator fun invoke(alertType: AlertType): FallEvent? {
        val settings = preferences.current.value
        val contacts = contactRepository.getContacts()

        if (contacts.isEmpty()) {
            AppLogger.w(TAG, "No emergency contacts configured")
            return logEvent(alertType, null, 0, 0, "No contacts configured")
        }

        // 1. Location fix (may be null when GPS is off).
        val location: LocationInfo? = try {
            locationManager.getBestLocation()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Location lookup failed", t)
            null
        }

        // 2. Build the message.
        val message = smsHelper.formatEmergencyMessage(
            userName = settings.userName,
            alertType = alertType,
            location = location,
            batteryPercent = smsHelper.batteryPercent()
        )

        // 3. Send to every contact.
        var sentCount = 0
        contacts.forEach { contact ->
            val result = smsHelper.sendSms(contact.phoneNumber, message)
            if (result.success) sentCount++
        }
        AppLogger.i(TAG, "Emergency alert sent to $sentCount/${contacts.size} contacts")

        // 4. Persist.
        return logEvent(alertType, location, sentCount, contacts.size, contacts.joinToString { it.name })
    }

    /** Convenience: primary contact (used by "call guardian"). */
    suspend fun primaryContact(): EmergencyContact? = contactRepository.getPrimaryContact()

    private suspend fun logEvent(
        alertType: AlertType,
        location: LocationInfo?,
        sentCount: Int,
        totalContacts: Int,
        contactNames: String
    ): FallEvent? = try {
        val event = FallEvent(
            timestamp = System.currentTimeMillis(),
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            address = location?.address.orEmpty(),
            wasConfirmed = true,
            wasCancelled = false,
            sensorData = JSONObject().apply {
                put("alertType", alertType.name)
                put("sentCount", sentCount)
                put("totalContacts", totalContacts)
                put("contactNames", contactNames)
                put("locationAvailable", location != null)
            }.toString(),
            alertSent = sentCount > 0
        )
        val id = fallLogDao.insert(event)
        event.copy(id = id.toInt())
    } catch (t: Throwable) {
        AppLogger.e(TAG, "logEvent failed", t)
        null
    }

    private companion object {
        const val TAG = "SendAlert"
    }
}
