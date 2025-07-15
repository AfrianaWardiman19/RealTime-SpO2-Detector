#ifndef PPG_PROCESSING_H
#define PPG_PROCESSING_H
#include <math.h>

float calculateSpO2(uint32_t redBufferSpo[], uint32_t irBufferSpo[], int size) {
  float redAC = 0, redDC = 0, irAC = 0, irDC = 0;
  float redMean = 0, irMean = 0;

  // Hitung rata-rata (DC)
  for (int i = 0; i < size; i++) {
    redMean += redBufferSpo[i];
    irMean += irBufferSpo[i];
  }
  redMean /= size;
  irMean /= size;

  redDC = redMean;
  irDC = irMean;

  // Hitung peak-to-peak (AC)
  uint32_t redMax = 0, redMin = 0xFFFFFFFF;
  uint32_t irMax = 0, irMin = 0xFFFFFFFF;

  for (int i = 0; i < size; i++) {
    if (redBufferSpo[i] > redMax) redMax = redBufferSpo[i];
    if (redBufferSpo[i] < redMin) redMin = redBufferSpo[i];
    if (irBufferSpo[i] > irMax) irMax = irBufferSpo[i];
    if (irBufferSpo[i] < irMin) irMin = irBufferSpo[i];
  }

  redAC = (float)(redMax - redMin);
  irAC = (float)(irMax - irMin);

  // Hitung rasio R
  float R = (redAC / redDC) / (irAC / irDC);

  const float a = 114.68;
  const float b = 14.2;
  float spo2 = a - b * R;


  // Clamp nilai agar realistis
  if (spo2 > 99.0) spo2 = 99.0;
  if (spo2 < 0.0) spo2 = 0.0;

  spo2 = floor(spo2 + 0.5);

  return spo2;
}

#endif
