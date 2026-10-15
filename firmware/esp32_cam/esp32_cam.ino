/* VisionAssist camera transport only. No AI inference runs on this board.
 * Target: AI Thinker ESP32-CAM / OV2640, Arduino-ESP32 3.3.2 (ESP-IDF 5.x).
 * Uses ESP-IDF async HTTP handlers so /status and /capture remain available
 * on port 80 while /stream is connected. One stream client at a time.
 */
#include <WiFi.h>
#include "esp_camera.h"
#include "esp_http_server.h"
#include "esp_timer.h"
#include "freertos/semphr.h"
#include "camera_pins.h"

// Change these before flashing. WPA2 requires at least eight characters.
static const char *AP_SSID = "VisionAssist-Cap";
static const char *AP_PASSWORD = "VisionCap-ChangeMe";
static bool cameraReady = false;
static esp_err_t cameraError = ESP_FAIL;
static SemaphoreHandle_t cameraMutex;
static SemaphoreHandle_t streamSlot;
static httpd_handle_t server = nullptr;

static esp_err_t statusHandler(httpd_req_t *req) {
  char json[320];
  snprintf(json, sizeof(json),
    "{\"device\":\"visionassist-esp32-cam\",\"firmware\":\"0.1.0\","
    "\"camera_ready\":%s,\"camera_error\":%d,\"uptime_ms\":%llu,"
    "\"clients\":%u,\"free_heap\":%u,\"psram\":%s}",
    cameraReady ? "true" : "false", (int)cameraError,
    (unsigned long long)(esp_timer_get_time() / 1000),
    (unsigned)WiFi.softAPgetStationNum(), (unsigned)ESP.getFreeHeap(),
    psramFound() ? "true" : "false");
  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, json, HTTPD_RESP_USE_STRLEN);
}
static esp_err_t unavailable(httpd_req_t *req, const char *message) {
  httpd_resp_set_status(req, "503 Service Unavailable");
  return httpd_resp_send(req, message, HTTPD_RESP_USE_STRLEN);
}
static esp_err_t captureHandler(httpd_req_t *req) {
  if (!cameraReady) return unavailable(req, "Camera initialization failed");
  if (xSemaphoreTake(cameraMutex, pdMS_TO_TICKS(2000)) != pdTRUE)
    return unavailable(req, "Camera busy; try again");
  camera_fb_t *fb = esp_camera_fb_get();
  esp_err_t result;
  if (!fb) {
    result = unavailable(req, "Capture failed");
  } else {
    httpd_resp_set_type(req, "image/jpeg");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
    result = httpd_resp_send(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
  }
  xSemaphoreGive(cameraMutex);
  return result;
}
static void streamTask(void *argument) {
  httpd_req_t *req = (httpd_req_t *)argument;
  httpd_resp_set_type(req, "multipart/x-mixed-replace; boundary=visionassistframe");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  while (cameraReady) {
    if (xSemaphoreTake(cameraMutex, pdMS_TO_TICKS(2000)) != pdTRUE) break;
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) { xSemaphoreGive(cameraMutex); break; }
    char header[150];
    const int length = snprintf(header, sizeof(header),
      "\r\n--visionassistframe\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n",
      (unsigned)fb->len);
    esp_err_t result = httpd_resp_send_chunk(req, header, length);
    if (result == ESP_OK)
      result = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
    esp_camera_fb_return(fb);
    xSemaphoreGive(cameraMutex);
    if (result != ESP_OK) break;
    vTaskDelay(pdMS_TO_TICKS(120)); // ~8fps maximum; save power/airtime.
  }
  httpd_resp_send_chunk(req, nullptr, 0);
  httpd_req_async_handler_complete(req);
  xSemaphoreGive(streamSlot);
  vTaskDelete(nullptr);
}
static esp_err_t streamHandler(httpd_req_t *req) {
  if (!cameraReady) return unavailable(req, "Camera initialization failed");
  if (xSemaphoreTake(streamSlot, 0) != pdTRUE)
    return unavailable(req, "Only one stream client is supported");
  httpd_req_t *asyncReq = nullptr;
  esp_err_t result = httpd_req_async_handler_begin(req, &asyncReq);
  if (result != ESP_OK) { xSemaphoreGive(streamSlot); return result; }
  if (xTaskCreate(streamTask, "mjpeg", 6144, asyncReq, 2, nullptr) != pdPASS) {
    unavailable(asyncReq, "Stream task allocation failed");
    httpd_req_async_handler_complete(asyncReq);
    xSemaphoreGive(streamSlot);
  }
  return ESP_OK;
}
static esp_err_t initializeCamera() {
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0;
  c.ledc_timer = LEDC_TIMER_0;
  c.pin_d0 = Y2_GPIO_NUM; c.pin_d1 = Y3_GPIO_NUM;
  c.pin_d2 = Y4_GPIO_NUM; c.pin_d3 = Y5_GPIO_NUM;
  c.pin_d4 = Y6_GPIO_NUM; c.pin_d5 = Y7_GPIO_NUM;
  c.pin_d6 = Y8_GPIO_NUM; c.pin_d7 = Y9_GPIO_NUM;
  c.pin_xclk = XCLK_GPIO_NUM; c.pin_pclk = PCLK_GPIO_NUM;
  c.pin_vsync = VSYNC_GPIO_NUM; c.pin_href = HREF_GPIO_NUM;
  c.pin_sccb_sda = SIOD_GPIO_NUM; c.pin_sccb_scl = SIOC_GPIO_NUM;
  c.pin_pwdn = PWDN_GPIO_NUM; c.pin_reset = RESET_GPIO_NUM;
  c.xclk_freq_hz = 20000000;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size = FRAMESIZE_VGA;
  c.jpeg_quality = 12;
  c.fb_count = psramFound() ? 2 : 1;
  c.fb_location = psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  c.grab_mode = CAMERA_GRAB_LATEST;
  if (!psramFound()) c.frame_size = FRAMESIZE_QVGA;
  return esp_camera_init(&c);
}
void setup() {
  Serial.begin(115200);
  cameraMutex = xSemaphoreCreateMutex();
  streamSlot = xSemaphoreCreateBinary();
  if (!cameraMutex || !streamSlot) {
    Serial.println("Semaphore allocation failed; halting");
    return;
  }
  xSemaphoreGive(streamSlot);
  cameraError = initializeCamera();
  cameraReady = cameraError == ESP_OK;
  Serial.printf("Camera initialization: 0x%x\n", cameraError);
  WiFi.mode(WIFI_AP);
  WiFi.setSleep(false);
  IPAddress ip(192, 168, 4, 1), mask(255, 255, 255, 0);
  if (!WiFi.softAPConfig(ip, ip, mask) || !WiFi.softAP(AP_SSID, AP_PASSWORD, 1, false, 4)) {
    Serial.println("AP setup failed");
    return;
  }
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 80;
  config.max_open_sockets = 7;
  config.lru_purge_enable = false;
  config.send_wait_timeout = 2;
  config.recv_wait_timeout = 3;
  if (httpd_start(&server, &config) != ESP_OK) {
    Serial.println("HTTP server failed");
    return;
  }
  const httpd_uri_t endpoints[] = {
    { .uri = "/status", .method = HTTP_GET, .handler = statusHandler, .user_ctx = nullptr },
    { .uri = "/capture", .method = HTTP_GET, .handler = captureHandler, .user_ctx = nullptr },
    { .uri = "/stream", .method = HTTP_GET, .handler = streamHandler, .user_ctx = nullptr }
  };
  for (const auto &endpoint : endpoints) {
    if (httpd_register_uri_handler(server, &endpoint) != ESP_OK)
      Serial.printf("Endpoint registration failed: %s\n", endpoint.uri);
  }
  Serial.print("Camera AP: "); Serial.println(WiFi.softAPIP());
}
void loop() { delay(1000); }
