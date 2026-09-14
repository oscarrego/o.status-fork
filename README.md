# O.status — Enhanced Fork

A feature-rich fork of [O.status by CATCHINGL](https://github.com/CATCHINGL/O.status), bringing the minimalist iPhone-style Dynamic Island status bar indicator to Android — with significant enhancements and new features.

> **Credit:** All core concept, original overlay architecture, and base drawing code belong to [CATCHINGL](https://github.com/CATCHINGL). This fork builds on top of that work to add user-requested features.

---

## What is O.status?

O.status is an Android overlay app that combines **Wi-Fi**, **cellular signal**, **Do Not Disturb**, and **battery** status into a single minimalist circular indicator that sits in the status bar area — inspired by the iPhone Dynamic Island design language.

---

## Features

### 🔵 Duo Indicator (Status Bar Overlay)
- Combined ring showing **battery level**, **Wi-Fi arcs**, and **4 cellular dots** in one compact circle
- Lives in the status bar corner as a floating overlay — no root needed
- **Avoids DND pill** automatically when Do Not Disturb is active
- **Auto-hides in full-screen apps** (configurable)
- **Starts on boot**

### 🎨 Colour Modes
- **Auto** — adapts to light/dark wallpaper automatically
- **Black** — always black indicator
- **White** — always white indicator
- **Custom colour** — full HSV colour wheel picker with live preview

### 🔋 Battery Percentage Number
- Optional battery percentage number displayed inside the ring
- **Separate size control** (50%–200%, steps of 5)
- Number colour follows the global colour mode setting

### 📐 Position & Size Controls
- **Side** — pin to Left or Right edge
- **Horizontal offset** — unlimited range with tap or hold `+`/`−`
- **Vertical offset** — unlimited range
- **Size** — scale 50%–250%
- **Thickness** — ring stroke width 50%–200%
- **Hold-to-repeat** on all `+`/`−` buttons (420ms initial, 80ms repeat)
- Tap value label to reset that field to default

### 🌐 Internet Speed Overlay (New)
A separate minimal floating overlay showing live download speed, matching the reference minimalist style (`10.6KB/s`):
- Single-line display: `XX.X KB/s` or `XX.XX MB/s`
- **Unit mode:** Auto (auto-scales), KB/s (always kilobytes), MB/s (always megabytes)
- Uses `TrafficStats` — **zero extra permissions required**
- Has its own independent **Position & Size & Thickness** controls
- Pauses automatically when the screen is off (battery-friendly)

### 🔆 AMOLED Burn-in Protection (Pixel Shifting)
- The overlay automatically shifts by ±1dp every **60 seconds** through a 9-point spiral pattern
- User-saved positions are never modified — the shift is a temporary render offset only
- Protects AMOLED displays from permanent pixel burn-in

### ⚡ Battery Efficiency
- Duo indicator polls every **2 seconds** with a dirty-flag (only redraws on state change)
- Speed overlay polls every 2 seconds and stops entirely when the screen is off
- No wakelocks, no background network permissions

---

## Requirements

- Android 10 (API 29) or higher
- "Display Over Other Apps" permission (requested on first launch)
- Optional: "Phone State" permission for cellular signal reading
- Optional: "Do Not Disturb Access" for DND-aware positioning

---

## Installation

1. Download the latest APK from [Releases](../../releases)
2. Enable "Install from unknown sources" on your device
3. Install the APK
4. Open O.status and grant the overlay permission
5. Toggle **O Status Bar** ON

---

## Building from Source

**Requirements:** JDK 17, Android SDK (API 35)

```bash
git clone https://github.com/oscarrego/o.status-fork
cd o.status-fork

# Windows
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
.\gradlew assembleDebug

# Output
app\build\outputs\apk\debug\app-debug.apk
```

---

## SharedPreferences Keys (`"settings"` file)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `duo_enabled` | Boolean | false | Duo indicator on/off |
| `color_mode` | String | `"auto"` | `"auto"` / `"black"` / `"white"` / `"#RRGGBB"` |
| `battery_percentage` | Boolean | false | Show battery % number |
| `battery_num_scale_pct` | Int | 100 | Battery number size (50–200) |
| `duo_scale_v3_pct` | Int | 100 | Indicator size (50–250) |
| `duo_h_offset` | Int | 0 | Horizontal offset (px) |
| `duo_v_offset_v2` | Int | 0 | Vertical offset (px) |
| `duo_side` | String | `"right"` | `"left"` / `"right"` |
| `duo_thickness_pct` | Int | 100 | Ring stroke thickness (50–200) |
| `speed_enabled` | Boolean | false | Speed overlay on/off |
| `speed_unit_mode` | String | `"auto"` | `"auto"` / `"kbps"` / `"mbps"` |
| `speed_scale_pct` | Int | 100 | Speed overlay size (50–250) |
| `speed_h_offset` | Int | 0 | Speed horizontal offset |
| `speed_v_offset` | Int | 100 | Speed vertical offset |
| `speed_side` | String | `"right"` | `"left"` / `"right"` |
| `speed_thickness_pct` | Int | 100 | Speed text size scale (50–200) |

---

## Fork Changes vs Original

| Feature | Original | This Fork |
|---------|----------|-----------|
| Colour picker | Cycle Black/White | Full HSV wheel + Auto + Custom hex |
| Size step | ±5 | ±1 (precise control) |
| Position range | ±30 | ±10000 (unlimited practical range) |
| Hold to repeat | ❌ | ✅ (420ms initial, 80ms repeat) |
| Battery % number size | ❌ | ✅ (50–200%, step 5) |
| Ring thickness control | ❌ | ✅ (50–200%) |
| Internet speed overlay | ❌ | ✅ minimal pill, auto KB/MB |
| Speed unit toggle | ❌ | ✅ Auto / KB/s / MB/s |
| Speed thickness | ❌ | ✅ |
| AMOLED pixel shifting | ❌ | ✅ (±1dp every 60s, 9-point spiral) |
| Poll efficiency | 1.2s constant redraw | 2s + dirty-flag (only redraws on change) |
| Screen-off pause | ❌ | ✅ (speed overlay pauses entirely) |

---

## License

This project inherits the license of the original repository. See [original repo](https://github.com/CATCHINGL/O.status) for details.
