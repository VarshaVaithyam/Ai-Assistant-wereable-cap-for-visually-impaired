package org.visionassist.vision_assist

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** Route local camera HTTP over the user's manually selected Wi-Fi, including
 * APs Android marks as having no internet. No SSID guessing or automatic join.
 * Binding is process-wide in this camera-only increment; release on pause.
 */
class MainActivity : FlutterActivity() {
    private val connectivity: ConnectivityManager
        get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
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
                        } else if (ConnectivityManager.getBoundNetworkForProcess() == wifi ||
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

    override fun onStop() {
        connectivity.bindProcessToNetwork(null)
        super.onStop()
    }
}
