#include <Wire.h>
#include "MAX30105.h"
#include "spo2_algorithm.h"
#include "heartRate.h"
#include "BluetoothSerial.h"
#include "PPGProcessing.h"

BluetoothSerial SerialBT;
MAX30105 sensorPpg;

#define MAX_BRIGHTNESS 255

uint32_t irBuffer[100];
uint32_t redBuffer[100];
uint32_t waveBuffer[100];

const int bufferSize = 100;
uint32_t redBufferSpo[bufferSize]; //Spo2
uint32_t irBufferSpo[bufferSize]; //Spo2
float redFilteredSpo[bufferSize]; //Spo2
float irFilteredSpo[bufferSize];  //Spo2

const unsigned long spo2Interval = 1000;  // Hitung SpO2 setiap 1 detik
unsigned long lastSpO2Millis = 0; //Spo2
int32_t spo2a = 0; // spo2

int32_t bufferLength = 100;
int32_t spo2 = 0;
int8_t validSPO2 = 0;
int32_t heartRate = 0;
int8_t validHeartRate = 0;

unsigned long lastHRUpdate = 0;
const int hrUpdateInterval = 400;  // update setiap 0.4 detik (400 ms)
const int irUpdateInterval = 5;
unsigned long lastIRUpdate = 0;
bool fingerDetected = false;
bool isConnected = false;  // Status koneksi Bluetooth

// Variabel untuk Butterworth Filter
float x_ir[3] = {0}, y_ir[3] = {0};
float x_red[3] = {0}, y_red[3] = {0};

// Fs = 100 Hz, fc = 2 Hz, Butterworth LPF order 2
const float a0 = 0.02008337;
const float a1 = 0.04016673;
const float a2 = 0.02008337;
const float b1 = -1.56101808;
const float b2 = 0.64135154;

// Fungsi Butterworth Filter
float butterworthFilter(float x[], float y[], float newValue) {
  x[2] = x[1];
  x[1] = x[0];
  x[0] = newValue;
  y[2] = y[1];
  y[1] = y[0];
  float y_new = a0 * x[0] + a1 * x[1] + a2 * x[2] - b1 * y[1] - b2 * y[2];
  y[0] = y_new;
  return y_new;
}


int medianFilter(int *buffer, int size) {
  int sorted[size];
  memcpy(sorted, buffer, size * sizeof(int));
  for (int i = 0; i < size - 1; i++) {
    for (int j = i + 1; j < size; j++) {
      if (sorted[i] > sorted[j]) {
        int temp = sorted[i];
        sorted[i] = sorted[j];
        sorted[j] = temp;
      }
    }
  }
  return sorted[size / 2]; // Ambil nilai tengah
}

// Variabel untuk HPF
float prevInputIR = 0, prevOutputIR = 0;
float prevInputRED = 0, prevOutputRED = 0;

// Fungsi High-Pass Filter (cutoff sekitar 0.2Hz)
float highPassFilter(float input, float &prevInput, float &prevOutput) {
  float alpha = 0.9876; // cutoff ~0.2 Hz
  float output = alpha * (prevOutput + input - prevInput);
  prevInput = input;
  prevOutput = output;
  return output;
}


void setup() {
  Serial.begin(115200);
  SerialBT.begin("MonitoringPPG"); // Nama Bluetooth ESP32
  Serial.println("🔵 Bluetooth Ready. Pair dengan 'PPG_Bluetooth'");

  if (!sensorPpg.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("❌ MAX30105 not found. Check wiring.");
    while (1);
  }
  sensorPpg.setup(60, 4, 2, 100, 118, 16384);

  // Menyesuaikan pengaturan sensor untuk meningkatkan SNR
  sensorPpg.setPulseAmplitudeRed(0x1F);
  sensorPpg.setPulseAmplitudeIR(0x1F);
  sensorPpg.setSampleRate(100);  // sample rate
  sensorPpg.setPulseWidth(411);  // pulse width
}

void loop() {
  // === Cek koneksi Bluetooth ===
  if (SerialBT.hasClient()) {
    if (!isConnected) {
      isConnected = true;
      Serial.println("✅ Device Connected!");
    }
  } else {
    if (isConnected) {
      isConnected = false;
      Serial.println("⚠️ Device Disconnected!");
    }
  }

  static int i = 0; //Spo2
  // === Cek data baru dari sensor ===
  while (!sensorPpg.available())
    sensorPpg.check();

  uint32_t irValue = sensorPpg.getIR();
  uint32_t redValue = sensorPpg.getRed();

  redBufferSpo[i] = redValue; //Spo2
  irBufferSpo[i] = irValue; //Spo2

  // === Filter Butterworth LPF ===
  float irFiltered = butterworthFilter(x_ir, y_ir, irValue);
  float redFiltered = butterworthFilter(x_red, y_red, redValue);

  // === High-Pass Filter untuk hilangkan baseline drift ===
  float irHPF = highPassFilter(irFiltered, prevInputIR, prevOutputIR);
  float redHPF = highPassFilter(redFiltered, prevInputRED, prevOutputRED);

  // === Deteksi jari dan proses buffer data ===
  if (irFiltered > 500) {
    if (!fingerDetected) {
      Serial.println("✅ Jari terdeteksi.");
      fingerDetected = true;
    }
    // Geser data buffer
    for (int i = 1; i < 100; i++) {
      waveBuffer[i - 1] = waveBuffer[i];
      irBuffer[i - 1] = irBuffer[i];
      redBuffer[i - 1] = redBuffer[i];
    }
    // Simpan data terbaru ke buffer
    waveBuffer[99] = irHPF;
    irBuffer[99] = irHPF;
    redBuffer[99] = redHPF;
    Serial.print("📡 IR (Filtered): "); Serial.print(irFiltered);
    Serial.print(", RED (Filtered): "); Serial.print(redFiltered);
    Serial.print(", Wave: "); Serial.println(waveBuffer[99]);
  } else {
    if (fingerDetected) {
      Serial.println("⚠️ Jari tidak terdeteksi, menghentikan pengiriman data.");
      fingerDetected = false;
    }
    return; // Stop loop jika jari tidak terdeteksi
  }

  sensorPpg.nextSample();

  // === Kirim data IR, RED, dan PPG Wave via Bluetooth tiap 5ms ===
  if (millis() - lastIRUpdate > irUpdateInterval) {
    lastIRUpdate = millis();
    // Kirim hanya 3 sampel terakhir untuk efisiensi
    String jsonWave = "{";
    jsonWave += "\"IR\":" + String(irFiltered) + ",";
    jsonWave += "\"RED\":" + String(redFiltered) + ",";
    jsonWave += "\"PPGWave\":[";
    for (int i = 90 ; i < 100; i++) {
      jsonWave += String(waveBuffer[i]);
      if (i < 99) jsonWave += ",";
    }
    jsonWave += "]}";
    Serial.println("📤 Mengirim JSON ke Bluetooth: " + jsonWave);
    SerialBT.println(jsonWave);
    Serial.println("📡 Data IR, RED, dan Wave sent via Bluetooth");
  }

  // === Hitung HR & SpO2 setiap 5 detik ===
  if (millis() - lastHRUpdate > hrUpdateInterval) {
    lastHRUpdate = millis();
    if (bufferLength > 0) {
      maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);
    }
    Serial.print("💓 HR: "); Serial.print(heartRate);
    Serial.print(", SpO2: "); Serial.print(spo2);
    Serial.print(" | Valid? HR: "); Serial.print(validHeartRate);
    Serial.print(", SpO2: "); Serial.println(validSPO2);
    String jsonHRSpO2 = "{";
    Serial.println("📤 Mengirim JSON ke Bluetooth: " + jsonHRSpO2);
    SerialBT.println(jsonHRSpO2);
    Serial.println("📡 Data HR & SpO2 sent via Bluetooth");
  }

  i = (i + 1) % bufferSize;
  // Kirim SpO2 setiap 1000ms
  if (millis() - lastSpO2Millis >= spo2Interval) {
    float spo2a = calculateSpO2(redBufferSpo, irBufferSpo, bufferSize);

    String jsonSpO2a = "{";
    jsonSpO2a += "\"spO2\":" + String(spo2a, 1);
    jsonSpO2a += "}";

    Serial.println(jsonSpO2a);
    SerialBT.println(jsonSpO2a);

    lastSpO2Millis = millis();
  }
}
