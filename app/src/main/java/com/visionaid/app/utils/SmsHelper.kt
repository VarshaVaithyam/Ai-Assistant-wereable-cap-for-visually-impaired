package com.visionaid.app.utils

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.visionaid.app.domain.models.AlertType
import com.visionaid.app.domain.models.LocationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Sends emergency SMS messages (spec 9.2).
 *
 *  - formats the message (name, alert type, Google Maps link, time, battery)
 *  - uses SmsManager with multipart support for long messages
 *  - tracks send success via per-message PendingIntents
 *  - retries failed sends up to [MAX_RETRIES] times
 */
@Singleton
class SmsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Result of one send attempt. */
    data class SmsResult(val phoneNumber: String, val success: Boolean)

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Formats the emergency message (spec 9.2 / 6.4 step 5). */
    fun formatEmergencyMessage(
        userName: String,
        alertType: AlertType,
        location: LocationInfo?,
        batteryPercent: Int?
    ): String {
        val name = userName.ifBlank { "The VisionAid user" }
        val typeText = when (alertType) {
            AlertType.FALL_DETECTED -> "FALL ALERT"
            AlertType.MANUAL_SOS -> "EMERGENCY SOS"
            AlertType.NO_MOVEMENT -> "NO MOVEMENT ALERT"
        }
        val time = SimpleDateFormat("EEE, MMM d yyyy HH:mm", Locale.US).format(Date())
        return buildString {
            append("⚠️ $typeText: $name may need help.\n")
            if (location != null) {
                append("Location: ${location.mapsLink()}\n")
                location.address?.let { append("Address: $it\n") }
            } else {
                append("Location: unavailable\n")
            }
            append("Time: $time\n")
            batteryPercent?.let { append("Phone battery: $it%\n") }
            append("This alert was sent automatically by the VisionAid app. Please check on them immediately.")
        }
    }

    /** Current battery percentage (for the alert body). */
    fun batteryPercent(): Int? = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    /**
     * Sends one SMS, waiting up to [timeoutMs] for the delivery report.
     * Retries [MAX_RETRIES] times on failure.
     */
    suspend fun sendSms(
        phoneNumber: String,
        message: String,
        timeoutMs: Long = 10_000L
    ): SmsResult = withContext(Dispatchers.IO) {
        var lastResult = SmsResult(phoneNumber, false)
        repeat(MAX_RETRIES + 1) { attempt ->
            if (attempt > 0) {
                AppLogger.d(TAG, "Retrying SMS to $phoneNumber (attempt $attempt)")
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
            lastResult = sendOnce(phoneNumber, message, timeoutMs)
            if (lastResult.success) return@withContext lastResult
        }
        lastResult
    }

    private suspend fun sendOnce(
        phoneNumber: String,
        message: String,
        timeoutMs: Long
    ): SmsResult {
        if (!hasSmsPermission()) {
            AppLogger.w(TAG, "SEND_SMS permission not granted")
            return SmsResult(phoneNumber, false)
        }
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return SmsResult(phoneNumber, false)

        val action = "$SMS_SENT_ACTION.${requestCode.incrementAndGet()}"
        val sentIntent = PendingIntent.getBroadcast(
            context,
            requestCode.get(),
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Completable when the system broadcasts the send result.
        val sendResult = kotlinx.coroutines.CompletableDeferred<Int>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == action && !sendResult.isCompleted) {
                    sendResult.complete(resultCode)
                }
            }
        }

        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        return try {
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) { sentIntents.add(sentIntent) }
                smsManager.sendMultipartTextMessage(
                    phoneNumber, null, parts, sentIntents, null
                )
            }

            // Wait for the broadcast; treat any result code as "attempted",
            // RESULT_OK specifically as success.
            val resultCode = withTimeoutOrNull(timeoutMs) { pendingResult?.await() }
            val success = resultCode == android.app.Activity.RESULT_OK
            if (!success) {
                AppLogger.w(TAG, "SMS to $phoneNumber result code: $resultCode")
            }
            SmsResult(phoneNumber, success)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "SMS send to $phoneNumber failed", t)
            SmsResult(phoneNumber, false)
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "SmsHelper"
        const val SMS_SENT_ACTION = "com.visionaid.app.SMS_SENT"
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 3_000L
        val requestCode = java.util.concurrent.atomic.AtomicInteger(1000)
    }
}
