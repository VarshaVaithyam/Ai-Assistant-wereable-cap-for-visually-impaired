package com.visionaid.app.data.repository

import com.visionaid.app.data.local.database.ContactDao
import com.visionaid.app.data.models.EmergencyContact
import com.visionaid.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository over the emergency contacts table (spec 9.3).
 * Validates the "minimum one contact" rule required for fall detection.
 */
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {

    /** Live list of contacts for the settings UI. */
    fun observeContacts(): Flow<List<EmergencyContact>> = contactDao.getAll()

    /** Snapshot used when sending alerts. */
    suspend fun getContacts(): List<EmergencyContact> = contactDao.getAllOnce()

    suspend fun getPrimaryContact(): EmergencyContact? = contactDao.getPrimary()

    /**
     * Adds or updates a contact. When [makePrimary] is set (or this is the
     * first contact) all other contacts lose the primary flag.
     */
    suspend fun saveContact(contact: EmergencyContact, makePrimary: Boolean = false): Long {
        val willBePrimary = makePrimary || contactDao.count() == 0
        return try {
            if (willBePrimary) {
                // Demote existing primary contacts.
                contactDao.getAllOnce().filter { it.isPrimary }.forEach {
                    contactDao.update(it.copy(isPrimary = false))
                }
            }
            contactDao.insert(
                if (willBePrimary) contact.copy(isPrimary = true) else contact
            )
        } catch (t: Throwable) {
            AppLogger.e(TAG, "saveContact failed", t)
            -1L
        }
    }

    suspend fun updateContact(contact: EmergencyContact) {
        try {
            contactDao.update(contact)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "updateContact failed", t)
        }
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        try {
            contactDao.delete(contact)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "deleteContact failed", t)
        }
    }

    suspend fun count(): Int = contactDao.count()

    private companion object {
        const val TAG = "ContactRepository"
    }
}
