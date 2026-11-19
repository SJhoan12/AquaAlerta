#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "wifi_config.h"

const int PIN_TRIG = 23;
const int PIN_ECHO = 18;

const char* WIFI_SSID = AQUAALERTA_WIFI_SSID;
const char* WIFI_PASSWORD = AQUAALERTA_WIFI_PASSWORD;
const char* SERVER_URL = AQUAALERTA_SERVER_URL;
const char* API_KEY = AQUAALERTA_SENSOR_API_KEY;
const char* TANQUE_ID = AQUAALERTA_TANQUE_ID;
const char* SENSOR_ID = "esp32-01";

const int MUESTRAS_POR_ENVIO = 12;
const unsigned long INTERVALO_ENVIO_MS = 30000;
const unsigned long INTERVALO_MUESTRA_MS = 120;

void conectarWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("Conectando a Wi-Fi");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 30) {
    delay(500);
    Serial.print(".");
    intentos++;
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Wi-Fi conectado. IP ESP32: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("No se pudo conectar al Wi-Fi.");
  }
}

float medirDistanciaCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(5);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(15);
  digitalWrite(PIN_TRIG, LOW);

  unsigned long duracionUs = pulseIn(PIN_ECHO, HIGH, 35000UL);
  if (duracionUs == 0) {
    return -1.0f;
  }

  float distanciaCm = (duracionUs * 0.0343f) / 2.0f;
  if (distanciaCm < 20.0f || distanciaCm > 450.0f) {
    return -1.0f;
  }
  return distanciaCm;
}

void enviarLecturas(const float* muestras, int cantidad) {
  if (WiFi.status() != WL_CONNECTED) {
    conectarWiFi();
    if (WiFi.status() != WL_CONNECTED) return;
  }

  String payload = "{\"tanqueId\":\"" + String(TANQUE_ID) + "\",\"sensorId\":\"" + String(SENSOR_ID) + "\",\"distanciasCm\":[";
  for (int i = 0; i < cantidad; i++) {
    payload += String(muestras[i], 1);
    if (i < cantidad - 1) payload += ",";
  }
  payload += "]}";

  Serial.print("Enviando: ");
  Serial.println(payload);

  HTTPClient http;
  WiFiClient clientHttp;
  WiFiClientSecure clientHttps;

  String urlStr = String(SERVER_URL);
  bool esHttps = urlStr.startsWith("https://");

  if (esHttps) {
    clientHttps.setCACert(AQUAALERTA_ROOT_CA);
    http.begin(clientHttps, SERVER_URL);
  } else {
    http.begin(clientHttp, SERVER_URL);
  }

  http.setTimeout(60000);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-API-Key", API_KEY);

  int codigoHttp = http.POST(payload);
  Serial.print(esHttps ? "Respuesta HTTPS: " : "Respuesta HTTP: ");
  Serial.println(codigoHttp);

  if (codigoHttp > 0) {
    Serial.println(http.getString());
  } else {
    Serial.print("Error conexion: ");
    Serial.println(http.errorToString(codigoHttp));
  }

  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  digitalWrite(PIN_TRIG, LOW);

  conectarWiFi();
}

void loop() {
  float muestras[MUESTRAS_POR_ENVIO];
  int validas = 0;

  for (int i = 0; i < MUESTRAS_POR_ENVIO; i++) {
    float cm = medirDistanciaCm();
    if (cm > 0) {
      muestras[validas++] = cm;
    }
    delay(INTERVALO_MUESTRA_MS);
  }

  Serial.print("Muestras validas: ");
  Serial.println(validas);

  if (validas >= 3) {
    enviarLecturas(muestras, validas);
  } else {
    Serial.println("No hubo suficientes muestras validas para enviar.");
  }

  delay(INTERVALO_ENVIO_MS);
}