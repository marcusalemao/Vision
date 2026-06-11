# FaceContext APK

Código-fonte Android para os óculos Rokid AI.

## Setup

1. Abrir no Android Studio
2. Conectar os óculos via USB-C
3. `adb devices` — verificar dispositivo
4. Run → instala direto via adb

## Requisitos

- Android Studio Hedgehog+
- SDK 31+ (Android 12)
- ADB habilitado nos óculos via companion app (Hi Rokid)

## Deploy

```bash
adb devices
# óculos deve aparecer como autorizado
scrcpy --max-size 640   # espelha o display 480x640
```
