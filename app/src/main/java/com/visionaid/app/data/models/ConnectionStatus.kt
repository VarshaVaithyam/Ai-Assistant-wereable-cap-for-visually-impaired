package com.visionaid.app.data.models

/** Lifecycle of the connection to the ESP32-CAM stream. */
enum class ConnectionStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}
