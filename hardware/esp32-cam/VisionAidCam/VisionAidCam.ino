/*
 * ============================================================================
 *  VisionAid — ESP32-CAM cap camera module
 * ============================================================================
 *
 *  Reference Arduino firmware for the wearable cap (spec step 17).
 *
 *  Features:
 *   1. MJPEG streaming server on port 81  ->  http://<ip>:81/stream
 *      (multipart/x-mixed-replace, exactly what ESP32CamService.kt parses)
 *   2. Touch-sensor status on port 80      ->  http://<ip>/touch_status
 *      returns {"touched": true/false, "timestamp": <millis>}
 *   3. WiFi in Access-Point mode by default (SSID "VisionAid-CAM",
 *      password "visionaid123", camera at 192.168.4.1). Switch
 *      USE_AP_MODE to false + fill WIFI credentials for Station mode.
 *   4. Status LED: blinks while connecting, solid when a client is
 *      streaming (red onboard LED on GPIO33, active-low).
 *   5. Watchdog timer: auto-restarts the board if the streaming task hangs.
 *
 *  Hardware:
 *   - AI-Thinker ESP32-CAM module
 *   - Capacitive touch sensor wired to TOUCH_PIN (default GPIO15 / T3).
 *     NOTE: avoid GPIO4 (T0) on the AI-Thinker board — it also drives the
 *     flash LED and produces noisy touch readings.
 *
 *  IDE setup:
 *   - Arduino IDE with ESP32 board support (>= 2.0)
 *   - Board: "AI Thinker ESP32-CAM"
 *   - Partition scheme: "Huge APP (3MB No OTA/1MB SPIFFS)" recommended
 * ============================================================================
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include <ESPmDNS.h>
#include <esp_task_wdt.h>

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

// WiFi: AP mode (default) lets the phone connect to the cap directly.
// Set to false and fill in credentials to join an existing network.
#define USE_AP_MODE       true
const char* AP_SSID       = "VisionAid-CAM";
const char* AP_PASSWORD   = "visionaid123";   // min 8 chars

const char* WIFI_SSID     = "YourHomeWiFi";
const char* WIFI_PASSWORD = "YourWiFiPassword";

// MJPEG stream server (must match the app's ESP32 stream port setting).
#define STREAM_PORT       81
// Control server (touch status).
#define HTTP_PORT         80

// Camera frame settings. QVGA (320x240) matches the YOLO input size and
// keeps the stream light enough for 10+ FPS over WiFi.
#define FRAME_SIZE        FRAMESIZE_QVGA
#define JPEG_QUALITY      12          // 0-63, lower = better quality
#define FRAME_BUFFER_COUNT 2

// Capacitive touch sensor. T3 = GPIO15 (safe on AI-Thinker).
// A reading BELOW the threshold counts as "touched".
#define TOUCH_PIN         T3          // GPIO15
#define TOUCH_THRESHOLD   30
#define TOUCH_DEBOUNCE_MS 30

// LED (red status LED on the AI-Thinker board, active-low).
#define LED_PIN           33

// ---------------------------------------------------------------------------
// AI-Thinker ESP32-CAM pin map
// ---------------------------------------------------------------------------
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// ---------------------------------------------------------------------------
// Touch sensor state (read on the main loop, served over HTTP)
// ---------------------------------------------------------------------------
volatile bool touchTouched  = false;
volatile unsigned long touchLastChangeMs = 0;
unsigned long lastTouchReadMs = 0;

httpd_handle_t streamServer = nullptr;
httpd_handle_t controlServer = nullptr;

// ===========================================================================
// Camera + WiFi setup
// ===========================================================================

bool initCamera() {
  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk  = XCLK_GPIO_NUM;
  config.pin_pclk  = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href  = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn  = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size   = FRAME_SIZE;
  config.jpeg_quality = JPEG_QUALITY;
  config.fb_count     = FRAME_BUFFER_COUNT;
  config.fb_location  = CAMERA_FB_IN_DRAM;
  config.grab_mode    = CAMERA_GRAB_LATEST;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", err);
    return false;
  }

  // Slight exposure/saturation boost helps indoor detection.
  sensor_t* s = esp_camera_sensor_get();
  if (s != nullptr) {
    s->set_vflip(s, 0);        // flip if the cap mount is upside-down
    s->set_hmirror(s, 0);
  }
  return true;
}

void connectWiFi() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);  // LED on while connecting (active-low)

#if USE_AP_MODE
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  Serial.printf("AP ready. Connect to '%s' and stream from http://%s:%d/stream\n",
                AP_SSID, WiFi.softAPIP().toString().c_str(), STREAM_PORT);
#else
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
    // Blink while connecting.
    digitalWrite(LED_PIN, !digitalRead(LED_PIN));
  }
  Serial.printf("\nConnected. Stream from http://%s:%d/stream\n",
                WiFi.localIP().toString().c_str(), STREAM_PORT);
#endif

  // Friendly hostname (works with mDNS-capable setups).
  MDNS.begin("visionaid-cam");
}

// ===========================================================================
// MJPEG stream server (port 81) — matches ESP32CamService.kt's parser
// ===========================================================================

#define PART_BOUNDARY "123456789000000000000987654321"
static const char* STREAM_CONTENT_TYPE =
    "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* STREAM_PART =
    "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

static esp_err_t stream_handler(httpd_req_t* req) {
  char partHeader[64];
  httpd_resp_set_type(req, STREAM_CONTENT_TYPE);

  while (true) {
    // Grab the freshest JPEG frame.
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Camera frame capture failed");
      break;
    }
    size_t jpgLen = fb->len;
    uint8_t* jpgBuf = fb->buf;

    bool clientStillHere = true;
    if (httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY)) == ESP_OK) {
      size_t headerLen = snprintf(partHeader, sizeof(partHeader), STREAM_PART, jpgLen);
      if (httpd_resp_send_chunk(req, partHeader, headerLen) == ESP_OK) {
        clientStillHere = (httpd_resp_send_chunk(req, (const char*)jpgBuf, jpgLen) == ESP_OK);
      }
    } else {
      clientStillHere = false;
    }

    esp_camera_fb_return(fb);
    if (!clientStillHere) break;   // phone disconnected -> stop streaming

    // Feed the watchdog while actively streaming.
    esp_task_wdt_reset();
    // ~10 FPS cap; raise the inference pipeline's effective rate in the app.
    vTaskDelay(pdMS_TO_TICKS(100));
  }
  return ESP_OK;
}

void startStreamServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = STREAM_PORT;
  config.ctrl_port = STREAM_PORT + 1;   // avoid clashing with the control server
  config.stack_size = 8192;

  if (httpd_start(&streamServer, &config) == ESP_OK) {
    httpd_uri_t streamUri = {};
    streamUri.uri = "/stream";
    streamUri.method = HTTP_GET;
    streamUri.handler = stream_handler;
    streamUri.user_ctx = nullptr;
    httpd_register_uri_handler(streamServer, &streamUri);
    Serial.println("MJPEG stream server running on port 81");
  } else {
    Serial.println("Failed to start stream server");
  }
}

// ===========================================================================
// Control server (port 80): touch status + simple index page
// ===========================================================================

static esp_err_t touch_status_handler(httpd_req_t* req) {
  char json[80];
  snprintf(json, sizeof(json),
           "{\"touched\": %s, \"timestamp\": %lu}",
           touchTouched ? "true" : "false",
           (unsigned long)touchLastChangeMs);
  httpd_resp_set_type(req, "application/json");
  // CORS header so a browser-based test page can poll it too.
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, json, strlen(json));
}

static esp_err_t index_handler(httpd_req_t* req) {
  const char* page =
      "<html><body><h1>VisionAid CAM</h1>"
      "<p><a href='/stream'>MJPEG stream</a></p>"
      "<p><a href='/touch_status'>Touch status</a></p></body></html>";
  httpd_resp_set_type(req, "text/html");
  return httpd_resp_send(req, page, strlen(page));
}

void startControlServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = HTTP_PORT;
  config.ctrl_port = HTTP_PORT + 2;
  config.stack_size = 8192;

  if (httpd_start(&controlServer, &config) == ESP_OK) {
    httpd_uri_t touchUri = {};
    touchUri.uri = "/touch_status";
    touchUri.method = HTTP_GET;
    touchUri.handler = touch_status_handler;
    httpd_register_uri_handler(controlServer, &touchUri);

    httpd_uri_t indexUri = {};
    indexUri.uri = "/";
    indexUri.method = HTTP_GET;
    indexUri.handler = index_handler;
    httpd_register_uri_handler(controlServer, &indexUri);

    Serial.println("Control server running on port 80");
  } else {
    Serial.println("Failed to start control server");
  }
}

// ===========================================================================
// Touch sensor reading (debounced, edge-tracked)
// ===========================================================================

void readTouchSensor() {
  unsigned long now = millis();
  if (now - lastTouchReadMs < TOUCH_DEBOUNCE_MS) return;
  lastTouchReadMs = now;

  uint16_t value = touchRead(TOUCH_PIN);
  bool touched = (value < TOUCH_THRESHOLD);   // capacitance drops on touch

  if (touched != touchTouched) {
    touchTouched = touched;
    touchLastChangeMs = now;
    Serial.printf("Touch %s (raw=%u)\n", touched ? "DOWN" : "UP", value);
  }
}

// ===========================================================================
// Arduino lifecycle
// ===========================================================================

void setup() {
  Serial.begin(115200);
  Serial.println("\nVisionAid ESP32-CAM booting...");

  // Watchdog: 30 s budget; the stream task feeds it while healthy.
  esp_task_wdt_init(30, true);
  esp_task_wdt_add(nullptr);

  if (!initCamera()) {
    Serial.println("HALT: camera unavailable");
    while (true) { delay(1000); }   // WDT will reboot the board eventually
  }

  connectWiFi();
  startControlServer();
  startStreamServer();

  digitalWrite(LED_PIN, HIGH);   // LED off: idle + ready
  Serial.println("VisionAid camera module ready.");
}

void loop() {
  readTouchSensor();
  esp_task_wdt_reset();

  // LED reflects streaming state: on while a client is connected.
  // (WiFi station count is a good proxy in AP mode.)
#if USE_AP_MODE
  bool clientConnected = (WiFi.softAPgetStationNum() > 0);
  digitalWrite(LED_PIN, clientConnected ? LOW : HIGH);
#endif

  delay(100);
}
