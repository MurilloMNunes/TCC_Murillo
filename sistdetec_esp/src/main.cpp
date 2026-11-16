#include <Arduino.h>
#include <FirebaseESP32.h>
#include <WiFi.h>
#include <Wire.h>
#include <MPU6050.h> 
#include <TinyGPS++.h>

// Configurações do WiFi
WiFiClient espClient;

// Configurações do GPS
const uint8_t RXPin = 4, TXPin = 2;
TinyGPSPlus gps;
const int pino_gps = 2;
HardwareSerial gpsSerial(pino_gps); 

// Configurações e variáveis do MPU6050 e dos dados de aceleração
MPU6050 mpu; 
float xFiltrado = 0, yFiltrado = 0, zFiltrado = 0;

// Dados que serão lidos pelo GPS e enviados ao DB
struct DadosGPS
{
    String latitude;
    String longitude;

    uint8_t dia;
    uint8_t mes;
    uint16_t ano;

    uint8_t hora;
    uint8_t minuto;
    uint8_t segundo;
};

DadosGPS leituradadosGPS;

// Configurações do Firebase
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Firebase API Setup
#define API_KEY ""
#define FIREBASE_HOST ""
#define FIREBASE_AUTH ""
#define FIREBASE_PROJECT_ID ""
#define USER_EMAIL ""
#define USER_PASSWORD ""

// Variáveis para medir a passagem do tempo no loop principal
unsigned long ultimoLoop = 0, ultimaLeituraGPS = 0, ultimostatusWiFi = 0, ultimoenvioFB = 0;

// Protótipos das funções
void configurarWifi();
void reconectarWifi();
void inicializarGPS();
bool verificarGPS(HardwareSerial &portaGPS);
void inicializarSensor();
void configurarFirebase();
void lerDadosSensores();
void lerDadosGPS();
void enviarDadosFirebase(float);

void setup(){

  inicializarGPS();          // Inicializa a conexão com o módulo GPS

  inicializarSensor();       // Inicializa o MPU6050

  configurarWifi();          // Inicializa a conexão WiFi

  configurarFirebase();      // Inicializa a conexão com o Firebase
}

void loop() {

  const uint16_t delay_loop = 10;

  //Loop para fazer o GPS enviar os dados corretamente
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  unsigned long loopAtual  = millis();

  // Estrutura para realizar a execução do código apenas a cada 10 ms
  if(loopAtual - ultimoLoop > delay_loop)
  { 
    lerDadosSensores();

    if (!Firebase.ready()) 
    {
        Serial.println("Firebase não está pronto. Tentando reestabelecer conexão...");
        configurarFirebase(); // Força a renovação do token/sessão
        delay(500);
    }

    const uint16_t intervalo_WiFi = 30000;

     // Confere o status da conexão WiFi a cada 30 segundos 
    if((loopAtual - ultimostatusWiFi) >= intervalo_WiFi)
    {
      if(WiFi.status() != WL_CONNECTED)     // Reconecta o WiFi se necessário
        reconectarWifi();

      ultimostatusWiFi = loopAtual;
    }

    const uint16_t intervalo_gps = 60000;

    // Obtém a posição do veículo a cada 1 minuto 
    if((loopAtual - ultimaLeituraGPS) >= intervalo_gps)
    {
      lerDadosGPS();
      ultimaLeituraGPS = loopAtual;
    }
    ultimoLoop = loopAtual;
  }
}

void configurarWifi() {
  
  int tempo_max = 10000, delay_conexao = 500;
  char net_ssid[] = "", net_password[] = "";

  //Configura conexão à rede WiFi
  Serial.print("Conectando a ");
  Serial.println(net_ssid);
  if(WiFi.status() == WL_CONNECTED) 
      return;

  WiFi.begin(net_ssid,net_password);
  Serial.print("Conectando a ");
  Serial.println(net_ssid);

  unsigned long inicio = millis();

  while (WiFi.status() != WL_CONNECTED && millis() - inicio < tempo_max)
  {
    Serial.println(".");
    delay(delay_conexao);
  }

  if(WiFi.status() == WL_CONNECTED)
  {
    Serial.println(" ");
    Serial.println("WiFi conectado");
    Serial.println("Endereço IP: ");
    Serial.println(WiFi.localIP());
  }
  else 
  {
    Serial.println(" ");
    Serial.println("Não foi possível conectar ao WiFi");
  }
}

void reconectarWifi() {

  configurarWifi();

  configurarFirebase();
}

void inicializarGPS() {
  const uint16_t GPSBaud = 9600, BaudRate = 9600;

  Serial.begin(BaudRate);      // Inicializa a comunicação serial

  gpsSerial.begin(GPSBaud,SERIAL_8N1,RXPin,TXPin);       // Inicializa a comunicação com o módulo GPS

  if(verificarGPS(gpsSerial))
  {
    Serial.println("Módulo GPS conectado com sucesso");
  }
  else
  {
    Serial.println("Módulo GPS não conectado");
  }
}

bool verificarGPS(HardwareSerial &portaGPS) {

  unsigned long tempoMax = millis();

  while (millis() - tempoMax < 3000) {
    
    if (portaGPS.available() > 0) 
    {
      char c = portaGPS.read();
      
      // Detecta se o GPS está enviando dados (o protocolo GPS NMEA sempre começa linhas com o caractere '$')
      if (c == '$') 
      {
        return true;    // Dado foi enviado pelo módulo
      }
    }
  }
  return false;   // Caso nenhum dado tenha sido enviado
}

void inicializarSensor() {

  Wire.begin();            // Inicializa o barramento I2C (pinos padrão SDA=21, SCL=22 no ESP32)

  mpu.initialize();

  // Verifica se o sensor está conectado
  if (mpu.testConnection()) {
    Serial.println("MPU6050 conectado com sucesso!");
  } else {
    Serial.println("Falha na conexão com o MPU6050");
  }
}

void configurarFirebase() {

  config.api_key = API_KEY;

  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  config.database_url = FIREBASE_HOST;

  if(WiFi.status() == WL_CONNECTED)
  {
    Firebase.begin(&config, &auth);
  }
  else
  {
    Serial.println("Não foi possível conectar ao Firebase pois não há conexão com a Internet");
  }
}

void lerDadosSensores() {

  float aceleracao_g, acelX, acelY, acelZ, limiar_impacto = 0.7, fator_de_conversao = 16384.0;
  const float alpha = 0.15; // Constante de suavização do filtro
  int max_vezes_acima_limiar = 5;
  static int contadorImpacto = 0;
  int16_t ax, ay, az;       // Dados do acelerômetro 

  mpu.getAcceleration(&ax, &ay, &az); // Lê os dados de aceleração do acelerômetro

  acelX = ax/fator_de_conversao;
  acelY = ay/fator_de_conversao;
  acelZ = az/fator_de_conversao;

  xFiltrado = (alpha * acelX) + ((1.0 - alpha) * xFiltrado);
  yFiltrado = (alpha * acelY) + ((1.0 - alpha) * yFiltrado);
  zFiltrado = (alpha * acelZ) + ((1.0 - alpha) * zFiltrado);

  aceleracao_g = sqrt(sq(xFiltrado) + sq(yFiltrado) + sq(zFiltrado)); // Calcula o módulo do vetor aceleração resultante

  // Subtrai a gravidade
  float g_liquido = abs(aceleracao_g - 1.0);

  // Compara o valor filtrado com o máximo valor lido
  if(g_liquido > limiar_impacto ) 
  {
   contadorImpacto++;
  }
  else 
  {
    // Se o sinal voltar ao normal, decai o contador gradualmente para evitar falsos positivos acumulados
    if (contadorImpacto > 0) 
     contadorImpacto--;
  }

  // Se ultrapassar o limiar de amostras consecutivas com impacto
  if(contadorImpacto >= max_vezes_acima_limiar) 
  {
    Serial.println("Enviando dados");
    
    if (WiFi.status() == WL_CONNECTED) {
       enviarDadosFirebase(g_liquido);
    } 
    else 
    {
       Serial.println("Erro: WiFi desconectado, impossível enviar.");
    }
    contadorImpacto = 0; // Reseta após o envio
  }

  Serial.print("Valor filtrado: ");
  Serial.println(g_liquido);
}

void lerDadosGPS() {

  //Obtém (se possível) os dados de data, horário e localização lidos pelo GPS
  if(gps.location.isValid() && gps.time.isValid() && gps.date.isValid())
  {
    leituradadosGPS.latitude = gps.location.lat();
    leituradadosGPS.longitude = gps.location.lng();
    leituradadosGPS.ano = gps.date.year();
    leituradadosGPS.mes = gps.date.month();
    leituradadosGPS.dia = gps.date.day();
    leituradadosGPS.hora = gps.time.hour();
    leituradadosGPS.minuto = gps.time.minute();
    leituradadosGPS.segundo = gps.time.second();
  }
  else
  {
    Serial.println("Localização atual não encontrada");
  }
}

void enviarDadosFirebase(float valPico) {

  char alerta[8] = "Ativado", endereco_db[8] = "Alertas", placa[8] = "ABC1B34", horario[9], data[11], end_latitude[26];
  char end_longitude[27], end_horario[25], end_data[22], end_alerta[24], end_placa[23];
  
  sprintf(end_latitude, "/%c/%c/Latitude", endereco_db, placa);
  sprintf(end_longitude, "/%c/%c/Longitude", endereco_db, placa);
  sprintf(end_horario, "/%c/%c/Horario", endereco_db, placa);
  sprintf(end_data, "/%c/%c/Data", endereco_db, placa);
  sprintf(end_alerta, "/%c/%c/Alerta", endereco_db, placa);
  sprintf(end_placa, "/%c/%c/Placa", endereco_db, placa);

  // Obtém os dados de GPS a serem enviados
  lerDadosGPS();

  sprintf(horario, "%02d:%02d:%02d", leituradadosGPS.hora, leituradadosGPS.minuto, leituradadosGPS.segundo);
  sprintf(data, "%02d/%02d/%04d", leituradadosGPS.dia, leituradadosGPS.mes, leituradadosGPS.ano);

  Serial.print("Valor enviado: ");
  Serial.println(valPico);

  if(Firebase.ready())
  {
    if(gps.location.isValid() && gps.time.isValid() && gps.date.isValid())
    {
        // Envia os dados ao banco de dados (sinalizando se o envio foi ou não realizado corretamente)
        if (
            Firebase.setString(fbdo, end_latitude, leituradadosGPS.latitude) && 
            Firebase.setString(fbdo, end_longitude, leituradadosGPS.longitude) &&
            Firebase.setString(fbdo, end_horario, horario) &&
            Firebase.setString(fbdo, end_data, data) &&
            Firebase.setString(fbdo, end_placa, placa) &&
            Firebase.setString(fbdo, end_alerta, alerta)
            ) {
            Serial.println("Dados enviados com sucesso!");
        } 
        else 
        {
            Serial.print("Erro ao enviar dado: "); 
            Serial.println(fbdo.errorReason());
        }
    }
    else
    {
        Serial.println("Não foi possível enviar os dados pois não há localização GPS válida");
    }
  }
  else
  {
    Serial.println("Não é possível conectar-se ao Firebase");
    Serial.println(WiFi.status() == WL_CONNECTED ? "Conectado" : "Desconectado");
    Serial.print("Erro detalhado: "); 
    Serial.println(fbdo.errorReason());
  }
}