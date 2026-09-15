[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="english"></a>
# FaceContext APK

Android source code for Rokid AI glasses.

## Setup

1. Open in Android Studio
2. Connect glasses via USB-C
3. `adb devices` — verify device
4. Run → installs directly via adb

## Requirements

- Android Studio Hedgehog+
- SDK 31+ (Android 12)
- ADB enabled on glasses via companion app (Hi Rokid)

## Deployment

```bash
adb devices
# glasses should appear as authorized
scrcpy --max-size 640   # mirrors the 480x640 display
```

---

[🇬🇧 English](#english) | [🇧🇷 Português](#portuguese)

<a id="portuguese"></a>
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
