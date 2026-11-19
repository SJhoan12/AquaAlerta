#include <HTTPClient.h>
#include <WiFi.h>
#include "wifi_config.h"

const char* ssid = AQUAALERTA_WIFI_SSID;
const char* password = AQUAALERTA_WIFI_PASSWORD;
const char* api_key = AQUAALERTA_SENSOR_API_KEY;
const char* SERVER_URL = AQUAALERTA_SERVER_URL;
const char* TANQUE_ID = "6af1f02f480fbb92b03a415c";
const char* SENSOR_ID = "esp32-01";

const int TRIG_PIN = 23;
const int ECHO_PIN = 18;
const int SAMPLE_COUNT = 12;
const unsigned long SEND_INTERVAL_MS = 30000;
const unsigned long ECHO_TIMEOUT_US = 30000;

unsigned long lastSendMs = 0;

bool connectToWifi() {
    if (WiFi.status() == WL_CONNECTED) {
        return true;
    }

    Serial.print("Conectando a Wi-Fi");
    WiFi.begin(ssid, password);
    unsigned long startedAt = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - startedAt < 20000) {
        delay(500);
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("No se pudo conectar al Wi-Fi.");
        return false;
    }

    Serial.print("Wi-Fi conectado. IP del ESP32: ");
    Serial.println(WiFi.localIP());
    return true;
}

bool readDistanceCm(float& distanceCm) {
    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(3);
    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);

    unsigned long duration = pulseIn(ECHO_PIN, HIGH, ECHO_TIMEOUT_US);
    if (duration == 0) {
        return false;
    }

    distanceCm = duration * 0.0343f / 2.0f;
    return distanceCm > 0 && distanceCm <= 400;
}

void sendReadings() {
    if (!connectToWifi()) {
        return;
    }

    float distances[SAMPLE_COUNT];
    int validCount = 0;

    for (int i = 0; i < SAMPLE_COUNT; i++) {
        float distanceCm;
        if (readDistanceCm(distanceCm)) {
            distances[validCount++] = distanceCm;
        } else {
            Serial.println("Muestra sin eco valido; no se agrega al envio.");
        }
        delay(80);
    }

    Serial.print("Muestras validas: ");
    Serial.println(validCount);

    if (validCount < 5) {
        Serial.println("Se necesitan al menos 5 muestras validas; no se envia la lectura.");
        return;
    }

    String payload = "{\"tanqueId\":\"";
    payload += TANQUE_ID;
    payload += "\",\"sensorId\":\"";
    payload += SENSOR_ID;
    payload += "\",\"distanciasCm\":[";

    for (int i = 0; i < validCount; i++) {
        if (i > 0) {
            payload += ",";
        }
        payload += String(distances[i], 1);
    }
    payload += "]}";

    Serial.print("Enviando: ");
    Serial.println(payload);

    WiFiClient wifiClient;
    HTTPClient http;
    if (!http.begin(wifiClient, SERVER_URL)) {
        Serial.println("No se pudo iniciar la solicitud HTTP.");
        return;
    }

    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", api_key);
    int statusCode = http.POST(payload);
    if (statusCode > 0) {
        Serial.print("Respuesta HTTP: ");
        Serial.println(statusCode);
        Serial.println(http.getString());
    } else {
        Serial.print("Error HTTP: ");
        Serial.println(http.errorToString(statusCode));
    }
    http.end();
}

void setup() {
    Serial.begin(115200);
    pinMode(TRIG_PIN, OUTPUT);
    pinMode(ECHO_PIN, INPUT);
    digitalWrite(TRIG_PIN, LOW);

    delay(1000);
    sendReadings();
    lastSendMs = millis();
}

void loop() {
    if (millis() - lastSendMs >= SEND_INTERVAL_MS) {
        sendReadings();
        lastSendMs = millis();
    }
    delay(100);
}
