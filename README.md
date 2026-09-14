# O.status :: Enhanced Fork

A fork of [O.status by CATCHINGL](https://github.com/CATCHINGL/O.status) with additional features. All core concept and original code belong to CATCHINGL.

## Features

- Duo ring indicator: battery, Wi-Fi, and cellular signal in one compact status bar overlay
- Colour modes: Auto, Black, White, Custom HSV picker
- Battery percentage number inside the ring
- Internet speed overlay (KB/s or MB/s, zero extra permissions)
- AMOLED pixel shifting for burn-in protection
- Position, size, and thickness controls with hold-to-repeat buttons
- Auto-hides in full-screen apps, avoids DND pill, starts on boot

## Requirements

- Android 10+ (API 29)
- "Display Over Other Apps" permission
- Optional: Phone State (cellular signal), DND Access

## Install

Download the APK from [Releases](../../releases), enable unknown sources, install, and toggle **O Status Bar** ON.

## Build

```bash
git clone https://github.com/oscarrego/o.status-fork
cd o.status-fork

# Windows
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
.\gradlew assembleDebug
# Output: app\build\outputs\apk\debug\app-debug.apk
```

## License

Inherits the license of the [original repository](https://github.com/CATCHINGL/O.status).
