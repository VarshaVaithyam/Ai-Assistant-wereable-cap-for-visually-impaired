package org.visionassist.vision_assist

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Handler
import android.os.Looper
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Route local camera HTTP over the user's manually selected Wi-Fi, including
 * APs Android marks as having no internet. No SSID guessing or automatic join.
 * Binding is process-wide in this camera-only increment; release on pause.
 */
class MainActivity : FlutterActivity() {
    private val internetWorkers = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectivity: ConnectivityManager
        get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger,
            "org.visionassist/internet").setMethodCallHandler { call, result ->
            if (call.method != "get") {
                result.notImplemented()
            } else {
                val address = call.argument<String>("url")
                internetWorkers.execute {
                    var connection: HttpsURLConnection? = null
                    try {
                        val url = URL(address ?: "")
                        require(url.protocol == "https" && url.userInfo == null &&
                            (url.port == -1 || url.port == 443) &&
                            url.host in setOf("api.geoapify.com", "maps.geoapify.com"))
                        // Never depend on the process's camera Wi-Fi binding.
                        val networks = connectivity.allNetworks
                        val vpn = networks.firstOrNull { candidate ->
                            connectivity.getNetworkCapabilities(candidate)?.hasTransport(
                                NetworkCapabilities.TRANSPORT_VPN) == true
                        }
                        val eligible = if (vpn != null) listOf(vpn) else networks.toList()
                        val network = eligible.firstOrNull { candidate ->
                            connectivity.getNetworkCapabilities(candidate)?.let {
                                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            } == true
                        } ?: throw IllegalStateException("No validated internet network")
                        connection = network.openConnection(url) as HttpsURLConnection
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        connection.setRequestProperty("User-Agent", "VisionAssistPrototype/0.2")
                        val status = connection.responseCode
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val output = ByteArrayOutputStream()
                        stream?.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 2 * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                        }
                        val response = mapOf("status" to status, "body" to output.toByteArray(),
                            "contentType" to (connection.contentType ?: ""))
                        mainHandler.post { result.success(response) }
                    } catch (_: Exception) {
                        // Do not log the address: it contains the user's API key.
                        mainHandler.post { result.error("INTERNET_UNAVAILABLE",
                            "Map request failed. Check internet, key restrictions, and VPN settings.", null) }
                    } finally { connection?.disconnect() }
                }
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger,
            "org.visionassist/camera_network").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "bindWifi" -> {
                        val wifi = connectivity.allNetworks.firstOrNull { network ->
                            connectivity.getNetworkCapabilities(network)?.let {
                                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                            } == true
                        }
                        if (wifi == null) {
                            result.error("NO_WIFI", "Join the cap Wi-Fi in Android settings.", null)
                        } else if (connectivity.boundNetworkForProcess == wifi ||
                            connectivity.bindProcessToNetwork(wifi)) {
                            result.success(null)
                        } else {
                            result.error("BIND_FAILED", "Could not use the Wi-Fi network.", null)
                        }
                    }
                    "release" -> {
                        connectivity.bindProcessToNetwork(null)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            } catch (error: SecurityException) {
                result.error("NETWORK_PERMISSION", error.message, null)
            }
        }
    }

    override fun onDestroy() {
        internetWorkers.shutdownNow()
        super.onDestroy()
    }

    override fun onStop() {
        connectivity.bindProcessToNetwork(null)
        super.onStop()
    }
}
