# 📸 AuraCam (Pixel-style Camera, Kotlin Multiplatform)

[![CI](https://github.com/AzharCodeWizard/auracam-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/AzharCodeWizard/auracam-kmp/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.3-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache_2.0-yellowgreen.svg)](LICENSE)

**AuraCam** is an open-source camera app built with **Kotlin Multiplatform** and **Compose Multiplatform**, following the **Material 3 Expressive** design language of the Google Pixel Camera.

---

## 🎯 Platform status

Be aware of what is actually implemented before you build:

| Target | Viewfinder | Capture | Status |
|---|---|---|---|
| **Android** | CameraX `PreviewView` | Photo + video to MediaStore | ✅ Shipping target |
| **Desktop (JVM)** | Gradient placeholder | Simulated pipeline | 🧪 UI harness only |
| **iOS** | Gradient placeholder | Simulated pipeline | 🚧 Compiles; no AVFoundation engine and no Xcode project yet |

The desktop and iOS `PlatformCameraEngine` implementations drive the UI from a simulated
sensor/histogram source so the interface can be developed and tested without hardware. Only the
Android engine talks to a real camera.

---

## ✨ Features

**Camera modes** — Photo, Night Sight, Portrait, Video, Cinematic Pan, Pro/Expert,
Astrophotography, Long Exposure, Panorama. Modes select the bound CameraX use-case set: video modes
bind `VideoCapture`, Pro mode binds `ImageAnalysis` for the live histogram, and everything else binds
the minimum set so devices with limited surface-combination support still work.

**Pro controls** — Manual ISO (50–6400), shutter speed, focus distance with focus peaking,
white balance (2000K–10000K), exposure compensation (mapped to the device's real
`ExposureState` range), and a live 32-bin RGB/luminance histogram computed from the
`ImageAnalysis` frame buffer.

**Viewfinder** — Tap-to-focus with 3A metering lock, dual exposure sliders, framing grids
(3×3, golden ratio, square), and a horizon leveler driven by the device rotation-vector sensor
with an accelerometer fallback and low-pass smoothing.

**Quick controls** — Aspect ratio, flash mode, self-timer, capture format, colour profile LUTs,
grid overlay, and watermark.

**Gallery** — In-app preview with an EXIF inspector and an Android share sheet
(`Intent.ACTION_SEND` with a `ClipData` URI grant).

**Privacy** — Geotagging is **off by default**. When enabled, the app requests location permission
at that moment, reads the last known fix, and hands it to CameraX as capture metadata. EXIF
timestamps and coordinates always reflect the real capture; nothing is fabricated.

---

## 🛠️ Project structure

```
auracam-kmp/
├── composeApp/                 # Compose Multiplatform UI
│   ├── src/commonMain/         # Viewfinder, pro controls, settings, theme
│   ├── src/androidMain/        # CameraX preview, permissions, haptics, share
│   ├── src/desktopMain/        # Desktop window + no-op platform bindings
│   └── src/iosMain/            # iOS UI bridge + platform bindings
├── shared/                     # Domain and hardware engine
│   ├── src/commonMain/         # Models, enums, settings, computational pipeline
│   ├── src/androidMain/        # CameraX, sensors, MediaStore, location
│   ├── src/desktopMain/        # Simulated engine
│   └── src/iosMain/            # iOS stubs
└── .github/workflows/ci.yml    # Tests, lint, APK build, Apple compile
```

---

## 🚀 Getting started

### Prerequisites
- JDK 17+
- Android SDK (API 35)
- An Android device or emulator with camera support

### Build and run

```bash
./gradlew :composeApp:installDebug
```

```bash
./gradlew :composeApp:run
```

```bash
./gradlew :shared:desktopTest :composeApp:desktopTest
```

```bash
./gradlew :composeApp:lintRelease
```

---

## 🔐 Release signing

Release builds fall back to the debug key unless you configure a keystore, so **verify before you
publish**:

```bash
./gradlew :composeApp:verifyReleaseSigning
```

Configure signing with a `keystore.properties` at the repository root (git-ignored — copy
`keystore.properties.example`):

```properties
storeFile=/absolute/path/to/auracam-release.jks
storePassword=…
keyAlias=auracam
keyPassword=…
```

Or set `AURACAM_KEYSTORE_FILE`, `AURACAM_KEYSTORE_PASSWORD`, `AURACAM_KEY_ALIAS` and
`AURACAM_KEY_PASSWORD` in the environment (this is what CI uses).

Then:

```bash
./gradlew :composeApp:bundleRelease
```

---

## 📋 Known gaps

- iOS has no `AVCaptureSession` engine and no Xcode project; the target compiles but does not
  capture.
- RAW/DNG, Ultra HDR, and the multi-frame computational modes drive UI state and EXIF metadata but
  are not yet backed by real per-mode capture pipelines — every mode currently saves a single
  CameraX frame.
- Colour profile LUTs are viewfinder overlays only; they are not baked into saved images.
- There are no instrumented (`androidTest`) UI tests yet.

---

## 📄 License

Apache License 2.0 — see [LICENSE](LICENSE).
