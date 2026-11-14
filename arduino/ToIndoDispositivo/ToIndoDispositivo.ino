#include <WiFi.h>
#include <WebServer.h>

const char* WIFI_SSID = "SUA_REDE_WIFI";
const char* WIFI_SENHA = "SUA_SENHA_WIFI";
const char* TOKEN = "toindo-token";
const char* NOME = "ToIndo-ESP32";
const int PINO_RELE = 26;
const bool RELE_ATIVO_EM_HIGH = true;

WebServer servidor(80);
bool ligado = false;

void aplicarRele() {
  digitalWrite(PINO_RELE, (ligado == RELE_ATIVO_EM_HIGH) ? HIGH : LOW);
}

bool autorizado() {
  return servidor.header("X-Token") == TOKEN;
}

void responder(bool ok) {
  String corpo = String("{\"ok\":") + (ok ? "true" : "false") +
                 ",\"estado\":\"" + (ligado ? "LIGADO" : "DESLIGADO") +
                 "\",\"nome\":\"" + NOME + "\"}";
  servidor.send(ok ? 200 : 400, "application/json", corpo);
}

void aoPing() {
  if (!autorizado()) {
    servidor.send(401, "application/json", "{\"ok\":false}");
    return;
  }
  responder(true);
}

void aoComando() {
  if (!autorizado()) {
    servidor.send(401, "application/json", "{\"ok\":false}");
    return;
  }
  String corpo = servidor.arg("plain");
  if (corpo.indexOf("\"LIGAR\"") >= 0) {
    ligado = true;
    aplicarRele();
    responder(true);
  } else if (corpo.indexOf("\"DESLIGAR\"") >= 0) {
    ligado = false;
    aplicarRele();
    responder(true);
  } else if (corpo.indexOf("\"AJUSTAR\"") >= 0) {
    responder(true);
  } else {
    responder(false);
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PINO_RELE, OUTPUT);
  aplicarRele();

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_SENHA);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("IP do dispositivo: ");
  Serial.println(WiFi.localIP());

  const char* cabecalhos[] = {"X-Token"};
  servidor.collectHeaders(cabecalhos, 1);
  servidor.on("/ping", HTTP_GET, aoPing);
  servidor.on("/comando", HTTP_POST, aoComando);
  servidor.begin();
}

void loop() {
  servidor.handleClient();
}
