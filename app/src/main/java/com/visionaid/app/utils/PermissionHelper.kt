package com.visionaid.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central permission catalogue used by the setup wizard (spec 11.3 step 1)
 * and the detection service start path.
 *
 * Each item carries a plain-language label + rationale so the setup screen
 * can explain (visually and via TTS) *why* the app needs each permission —
 * an accessibility requirement for this user group.
 */
object PermissionHelper {

    data class PermissionItem(
        val permission: String,
        val label: String,
        val rationale: String,
        val minSdk: Int = 1,
        val maxSdk: Int = Int.MAX_VALUE
    )

    /** Every runtime permission the app can request, with explanations. */
    val ALL_PERMISSIONS: List<PermissionItem> = listOf(
        PermissionItem(
            permission = Manifest.permission.CAMERA,
            label = "Camera",
            rationale = "Used for the phone-camera backup when the cap camera is unavailable."
        ),
        PermissionItem(
            permission = Manifest.permission.RECORD_AUDIO,
            label = "Microphone",
            rationale = "Needed for voice commands and talking to the AI assistant."
        ),
        PermissionItem(
            permission = Manifest.permission.SEND_SMS,
            label = "SMS",
            rationale = "Sends the emergency message with your location to your contacts after a fall."
        ),
        PermissionItem(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            label = "Location",
            rationale = "Adds a Google Maps link with your position to emergency alerts."
        ),
        PermissionItem(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            label = "Approximate location",
            rationale = "Backup location source for emergency alerts."
        ),
        PermissionItem(
            permission = Manifest.permission.BODY_SENSORS,
            label = "Body sensors",
            rationale = "Reserved for health sensors on some devices."
        ),
        PermissionItem(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            label = "Notifications",
            rationale = "Shows the permanent VisionAid service notification.",
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        PermissionItem(
            permission = Manifest.permission.BLUETOOTH_CONNECT,
            label = "Bluetooth",
            rationale = "Connects to your earphones so voice guidance is private.",
            minSdk = Build.VERSION_CODES.S
        ),
        PermissionItem(
            permission = Manifest.permission.BLUETOOTH_SCAN,
            label = "Bluetooth scanning",
            rationale = "Finds nearby audio devices when pairing.",
            minSdk = Build.VERSION_CODES.S
        )
    )

    /** Permissions relevant for the current OS version. */
    fun applicablePermissions(): List<PermissionItem> {
        val sdk = Build.VERSION.SDK_INT
        return ALL_PERMISSIONS.filter { sdk >= it.minSdk && sdk <= it.maxSdk }
    }

    /** Flat array for the permissions launcher. */
    fun runtimePermissions(): Array<String> =
        applicablePermissions().map { it.permission }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** True when every applicable permission has been granted. */
    fun allGranted(context: Context): Boolean =
        applicablePermissions().all { isGranted(context, it.permission) }

    /**
     * The critical set for the detection pipeline. The service starts even
     * when only these are granted (e.g. user declined Bluetooth).
     */
    fun criticalPermissionsGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.CAMERA) ||
            isGranted(context, Manifest.permission.RECORD_AUDIO)
}
