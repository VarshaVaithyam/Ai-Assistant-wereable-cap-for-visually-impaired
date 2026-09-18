package com.visionaid.app.data.remote.api

import com.visionaid.app.di.IoDispatcher
import com.visionaid.app.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal one-shot HTTP helper built on OkHttp.
 *
 * Used for:
 *  - polling the ESP32 touch sensor endpoint (/touch_status),
 *  - the setup wizard's "test connection" check.
 *
 * Long-lived streaming (MJPEG) is handled separately by
 * [com.visionaid.app.data.remote.esp32.ESP32CamService].
 */
@Singleton
class ApiClient @Inject constructor(
    private val baseClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Performs a GET request and returns the body as a [JSONObject].
     *
     * @param timeoutMs Per-call timeout. A short-lived derived client shares
     *                  OkHttp's connection pool, so this is cheap.
     * @return parsed JSON, or null when the request failed / body was invalid.
     */
    suspend fun getJson(url: String, timeoutMs: Long = 2_000L): JSONObject? =
        withContext(ioDispatcher) {
            try {
                val client = baseClient.newBuilder()
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    JSONObject(body)
                }
            } catch (e: IOException) {
                null // network failures are expected while polling
            } catch (t: Throwable) {
                AppLogger.w(TAG, "getJson failed for $url: ${t.message}")
                null
            }
        }

    /** Simple reachability probe. */
    suspend fun isReachable(url: String, timeoutMs: Long = 3_000L): Boolean =
        getJson(url, timeoutMs) != null

    private companion object {
        const val TAG = "ApiClient"
    }
}
