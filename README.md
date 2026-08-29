# 📸 AuraCam (Google Pixel Camera KMP)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.0-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-yellowgreen.svg)](LICENSE)

**AuraCam** is a high-performance, open-source Google Pixel Camera (GCam) application built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** for Android, iOS, and Desktop.

It follows the **Google Pixel Material 3 Expressive** design language and provides advanced manual camera controls, computational photography pipelines, real-time sensor leveling, and hardware Camera2 / CameraX acceleration.

---

## ✨ Features

- **🚀 60 FPS Hardware Viewfinder**: Native `androidx.camera.view.PreviewView` integration with zero-latency preview.
- **📷 Complete Camera Modes**:
  - `Photo`: HDR+ capture with Zero Shutter Lag (ZSL).
  - `Night Sight`: Multi-frame low-light stacking pipeline with real tone synthesis.
  - `Portrait`: Depth estimation and simulated f/1.4 aperture bokeh blur.
  - `Video`: 4K 60FPS recording with live duration HUD and MediaStore integration.
  - `Cinematic Pan`: Smooth motion video stabilization.
  - `Pro / Expert`: Full manual camera control with real-time histograms.
  - `Astrophotography`: Long-exposure celestial tracking simulation.
  - `Long Exposure`: Light trails and smooth water motion accumulation.
- **🎛️ Pro Controls Sheet**:
  - Manual ISO (50–6400) with Auto ISO chip.
  - Shutter Speed (1/8000s down to 30s).
  - Manual Focus distance (Macro 🌷 to Infinity ⛰️) with **Focus Peaking** neon green edge highlights.
  - White Balance (2000K to 10000K Kelvin).
  - Exposure Compensation (-3.0 to +3.0 EV).
  - **Live 32-Bin RGB & Luminance Histogram** graph updated directly from camera frame buffers.
- **🎯 Tap-to-Focus & Dual Exposure Sliders**:
  - Pixel yellow focus brackets with 3A metering lock.
  - Draggable **Brightness (Sun EV)** and **Shadow Tone (Moon Contrast)** sliders.
- **📐 3D Horizon Leveler**:
  - Physical device `SensorManager` rotation vector listener with green lock indicator at 0° level.
- **⚡ Quick Controls Dropdown**:
  - Aspect Ratios (`4:3`, `16:9`, `1:1`, `Full`).
  - Flash Modes (`Off`, `Auto`, `On`, `Torch`).
  - Timers (`Off`, `3s`, `10s`) with animated center countdown beeps.
  - Capture Formats (`JPEG`, `RAW DNG`, `RAW + JPEG`, `Ultra HDR`).
  - Real Tone & Cinema LUTs (`Natural`, `Vibrant`, `Real Tone`, `B&W Mono`, `Cinematic`, `Astro Boost`).
  - Viewfinder Framing Grids (`3x3`, `Golden Ratio`, `Square`, `Off`).
  - Pixel Watermark (`Shot on Pixel | 24mm f/1.68 1/250s ISO 100`).
- **🖼️ In-App Gallery & EXIF Inspector**:
  - Full EXIF inspector (Device, Lens aperture, Shutter, ISO, Resolution, GPS coordinates).
  - Android Native Share Sheet (`Intent.ACTION_SEND`).

---

## 🛠️ Project Structure

```
auracam-kmp/
├── composeApp/                 # Compose Multiplatform UI Module
│   ├── src/commonMain/kotlin/  # Shared Viewfinder, Pro Controls & Themes
│   ├── src/androidMain/kotlin/ # Android CameraPreview & Intent Sharing
│   ├── src/desktopMain/kotlin/ # Desktop Compose Window
│   └── src/iosMain/kotlin/     # iOS UI Bridge
├── shared/                     # Domain & Hardware Camera Engine Module
│   ├── src/commonMain/kotlin/  # Models, Enums & Computational Pipeline
│   ├── src/androidMain/kotlin/ # CameraX, Camera2, SensorManager & MediaStore
│   ├── src/desktopMain/kotlin/ # Desktop Simulation Engine
│   └── src/iosMain/kotlin/     # iOS AVFoundation Engine
└── gradle/                     # Version Catalog & Gradle Configs
```

---

## 🚀 Getting Started

### Prerequisites
- JDK 17+
- Android SDK (API 34+)
- Android device or emulator with Camera support

### Build & Run Commands

```bash
# Run Desktop App
./gradlew :composeApp:run

# Run Unit Tests
./gradlew :shared:desktopTest

# Install Debug APK on Connected Android Device
./gradlew :composeApp:installDebug

# Build Release APK
./gradlew :composeApp:assembleRelease

# Build Google Play Store Bundle (.aab)
./gradlew :composeApp:bundleRelease
```

---

## 📄 License

```
Copyright 2026 AuraCam Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
