package com.visionaid.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.visionaid.app.di.ApplicationScope
import com.visionaid.app.domain.models.LocationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location manager built on FusedLocationProviderClient (spec 9.1).
 *
 *  - requests updates every 10 s while active
 *  - exposes the freshest location as a StateFlow
 *  - reverse-geocodes coordinates to a human-readable address
 *  - keeps a small history ("where have I been" feature)
 */
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    private val fusedClient = FusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<LocationInfo?>(null)
    val currentLocation: StateFlow<LocationInfo?> = _currentLocation.asStateFlow()

    private val _locationUpdates = MutableSharedFlow<LocationInfo>(extraBufferCapacity = 16)
    val locationUpdates: SharedFlow<LocationInfo> = _locationUpdates.asSharedFlow()

    /** Rolling history for the "where have I been" feature. */
    private val history = ArrayDeque<LocationInfo>()

    private var requestingUpdates = false

    /** Starts periodic updates (every 10 s, spec 9.1). Idempotent. */
    @SuppressLint("MissingPermission") // callers must hold location permission
    fun startUpdates() {
        if (requestingUpdates) return
        requestingUpdates = true
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            AppLogger.i(TAG, "Location updates started")
        } catch (t: Throwable) {
            AppLogger.e(TAG, "requestLocationUpdates failed", t)
            requestingUpdates = false
        }
    }

    fun stopUpdates() {
        if (!requestingUpdates) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "removeLocationUpdates failed: ${t.message}")
        }
        requestingUpdates = false
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { push(LocationInfo.fromLocation(it)) }
        }
    }

    private fun push(info: LocationInfo) {
        _currentLocation.value = info
        _locationUpdates.tryEmit(info)
        history.addLast(info)
        while (history.size > HISTORY_LIMIT) history.removeFirst()
        // Reverse-geocode off the hot path.
        externalScope.launch {
            val address = reverseGeocode(info.latitude, info.longitude)
            if (address != null) {
                _currentLocation.value = info.copy(address = address)
            }
        }
    }

    /**
     * Best-effort fresh location for emergency alerts:
     * uses the cached one if it is < 30 s old, otherwise fetches a new fix
     * with a timeout.
     */
    suspend fun getBestLocation(timeoutMs: Long = 10_000L): LocationInfo? {
        _currentLocation.value?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < 30_000L) return cached
        }
        return fetchCurrentLocation(timeoutMs)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(timeoutMs: Long = 10_000L): LocationInfo? = try {
        withTimeoutOrNull(timeoutMs) {
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null
            ).await()
            location?.let {
                val info = LocationInfo.fromLocation(it)
                // Enrich with address.
                info.copy(address = reverseGeocode(info.latitude, info.longitude))
            }
        }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "fetchCurrentLocation failed", t)
        null
    }

    /** Reverse geocode with Geocoder (offline caches may still resolve). */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                val addresses = Geocoder(context).getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.let { address ->
                    listOfNotNull(
                        address.thoroughfare,
                        address.locality,
                        address.adminArea
                    ).joinToString(", ").ifBlank { null }
                }
            } catch (t: Throwable) {
                AppLogger.d(TAG, "Reverse geocode failed: ${t.message}")
                null
            }
        }

    /** "Where have I been" support — recent locations, oldest first. */
    fun locationHistory(): List<LocationInfo> = history.toList()

    fun describeCurrentLocation(): String {
        val loc = _currentLocation.value ?: return "Location is not available yet."
        val address = loc.address
        return if (!address.isNullOrBlank()) {
            "You are near $address."
        } else {
            "Your coordinates are ${ "%.4f".format(loc.latitude) }, ${"%.4f".format(loc.longitude)}."
        }
    }

    private companion object {
        const val TAG = "LocationManager"
        const val UPDATE_INTERVAL_MS = 10_000L
        const val HISTORY_LIMIT = 50
    }
}
