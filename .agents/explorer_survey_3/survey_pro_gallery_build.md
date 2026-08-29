# AuraCam KMP Codebase Survey — R3 & R4 Analysis Report

**Explorer**: Explorer 3  
**Date**: 2026-08-29  
**Scope**: Requirement R3 (Pro Controls Sheet & Gallery Viewer Polish) & Requirement R4 (Automated Build, Verification & Physical Device Deployment)  
**Project Root**: `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp`

---

## 1. Executive Summary

AuraCam KMP is a Kotlin Multiplatform (Compose Multiplatform 1.7.3, Kotlin 2.1.0, AGP 8.7.3) camera application targeting Android (CameraX 1.4.1 / Camera2), Desktop (JVM 17), and iOS. The core camera domain logic and computational pipeline are situated in `:shared`, while the Material 3 Compose UI layer resides in `:composeApp`.

This survey thoroughly evaluated the existing implementations for **Requirement R3** (Pro Controls bottom sheet, live RGB/Luminance histogram, in-app gallery viewer with EXIF and native share) and **Requirement R4** (Gradle build configuration, desktop unit test suite, Android debug APK compilation, physical Android device deployment, and screen capture verification).

All build systems, test suites (`:shared:desktopTest`), and physical device connections (`Nothing A015` on Android 16) were verified operational. Specific gaps in UI polish, tactile feedback, bezier curve histogram rendering, responsive slider indicators, and gallery image decoding were cataloged with concrete architecture recommendations.

---

## 2. Requirement R3: Pro Controls Sheet & Gallery Viewer

### 2.1 Pro Controls Bottom Sheet Implementation

#### File & Component Mapping
- **Primary Composable**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` (`ProControlsSheet`)
- **State Models**: `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraModels.kt` (`ProSettings`)
- **Invocation & Transition**: `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` (lines 186–197) wrapped in `AnimatedVisibility(visible = cameraMode == CameraMode.PRO, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())`
- **Theme Tokens**: `composeApp/src/commonMain/kotlin/com/auracam/ui/theme/AuraCamTheme.kt` (`PixelYellowAccent`, `PixelFocusPeakingGreen`, `PixelSurfaceDark`)

#### Current Implementation Analysis
`ProControlsSheet` uses a two-level hierarchy:
1. **Tab Selector Row**: A horizontal `LazyRow` rendering tabs for `ISO`, `Shutter`, `Focus`, `EV`, `WB`, and `Histogram`. Each tab item displays its title and current active parameter badge (e.g. `ISO 100`, `1/125s`, `5500K`, `0.0 EV`, `Macro 🌷`, `RGB`).
2. **Individual Control Panels**:
   - **ISO (`IsoControl`)**: Displays an `AssistChip` for "Auto ISO" and a discrete `Slider` stepping through `[50, 100, 200, 400, 800, 1600, 3200, 6400]` with `steps = 6`.
   - **Shutter Speed (`ShutterControl`)**: Displays an `AssistChip` for "Auto Shutter" and a discrete `Slider` stepping through 14 denominator values `[8000L, 4000L, 2000L, 1000L, 500L, 250L, 125L, 60L, 30L, 15L, 8L, 4L, 2L, 1L]` with `steps = 12`.
   - **Manual Focus (`FocusControl`)**: Displays an `AssistChip` for "Auto Focus", a continuous `Slider` (`0.0f..1.0f`), and a secondary `Row` with a `Switch` for Focus Peaking outline.
   - **Exposure Value (`EvControl`)**: Continuous `Slider` from `-3.0f` to `+3.0f` EV with `0.1` rounding.
   - **White Balance (`WbControl`)**: Displays an `AssistChip` for "Auto AWB" and a continuous `Slider` from `2000K` to `10000K`.

#### Identified Gaps & Polish Recommendations
1. **Slider Indicator & Tactile Motion**:
   - Current sliders use stock `androidx.compose.material3.Slider` with standard circle thumbs.
   - **Recommendation**: Create an expressive custom slider composable (`PixelProSlider`) with:
     - A custom thumb with a glowing ring indicator / tactile pill shape.
     - Visual tick marks on active stops (ISO speeds, shutter denominations).
     - Floating badge indicator above or beside the thumb during active drag.
     - Haptic tick feedback (`soundAndHaptics.vibrateSnap()`) on discrete index transitions.
2. **Tab Bar Spacing & Screen Overflow**:
   - On compact screens or portrait devices, the rightmost tab ("Histogram") can be partially clipped without visual cue.
   - **Recommendation**: Apply horizontal edge fade (`Brush.horizontalGradient`) or responsive pill widths with M3 Expressive tonal containers.
3. **Focus Control Enhancement**:
   - **Recommendation**: Add visual endpoint glyphs for Macro (🌷 flower) at `1.0` and Infinity (⛰️ mountain) at `0.0`, with animated indicator feedback when snapping near ends.

---

### 2.2 Real-Time Live RGB / Luminance Histogram Pipeline & Styling

#### File & Component Mapping
- **Model**: `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraModels.kt` (`HistogramData(redBins, greenBins, blueBins, luminanceBins)`)
- **Data Pipeline Engine (Hardware Android)**: `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt` (`processImageFrameForHistogram`)
  - Sub-samples pixel buffer with `step = 4` during CameraX `ImageAnalysis` frames.
  - Computes 32 bins per channel and 32 luminance bins using Rec.601 coefficients ($Y = 0.299R + 0.587G + 0.114B$).
  - Normalizes max bin height to $100$ and emits updates to `_liveHistogram: MutableStateFlow<HistogramData>`.
- **Data Pipeline Simulation (Desktop/iOS)**: `shared/src/commonMain/kotlin/com/auracam/camera/domain/BaseCameraEngine.kt` (`startSensorSimulation`)
  - Simulates dynamic dynamic Gaussian/sinusoidal distribution modulated by `proSettings.evBias` and random noise at 10 Hz (100 ms).
- **UI Composable**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` (`HistogramViewer`)

#### Current Implementation Analysis
The current `HistogramViewer` renders a `Canvas` with 32 vertical rectangles per channel with fixed alpha:
- Red: `Color(0x66FF5252)`
- Green: `Color(0x6669F0AE)`
- Blue: `Color(0x66448AFF)`

#### Identified Gaps & Polish Recommendations
1. **Histogram Rendering Polish (Curved Area Splines)**:
   - Current 32-bar discrete rectangles look blocky and rudimentary.
   - **Recommendation**: Implement smooth cubic bezier paths (`Path.cubicTo`) with vertical linear gradient fills (`Brush.verticalGradient`) underneath the curves, topped with an anti-aliased glowing stroke line.
2. **Display Modes (RGB Combined vs. Luminance vs. Split Channels)**:
   - Current viewer only shows overlaid RGB bars.
   - **Recommendation**: Add a toggle pill row inside the Histogram tab allowing users to switch between:
     - **RGB Overlaid**: Multi-colored transparent overlapping curves with additive blend.
     - **Luminance Only**: High-contrast white/yellow curve with 18% middle-gray marker line.
     - **RGB 3-Way Split**: Three compact stacked mini-graphs for R, G, and B.
3. **Clipping & Exposure Zebra Warnings**:
   - **Recommendation**: Add highlight clipping warnings (bin 31 peak indicator) and shadow crush indicators (bin 0 peak indicator).

---

### 2.3 In-App Gallery Viewer & EXIF Details Card

#### File & Component Mapping
- **Primary Composable**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/GalleryPreviewSheet.kt` (`GalleryPreviewSheet`)
- **State Models**: `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraModels.kt` (`CapturedMedia`, `ExifInfo`)
- **Metadata Generator**: `shared/src/commonMain/kotlin/com/auracam/processing/ComputationalPipeline.kt` (`generateExif`, `formatPixelWatermark`)
- **Platform Share Abstraction**:
  - `composeApp/src/commonMain/kotlin/com/auracam/ui/util/PlatformShare.kt` (`rememberPlatformShare`)
  - Android: `composeApp/src/androidMain/kotlin/com/auracam/ui/util/PlatformShare.android.kt`
  - Desktop: `composeApp/src/desktopMain/kotlin/com/auracam/ui/util/PlatformShare.desktop.kt`
  - iOS: `composeApp/src/iosMain/kotlin/com/auracam/ui/util/PlatformShare.ios.kt`

#### Current Implementation Analysis
- **Top Bar**: Displays Close button, media filename, mode/format subtitle, native Share icon button, and EXIF toggle button.
- **Image Viewport**: Currently renders a stylized camera placeholder icon on a dark radial gradient background with mode text, resolution, and watermark pill.
- **EXIF Metadata Card**: Card containing tabular key-value rows for:
  - `Device`, `Lens / Aperture`, `Shutter Speed`, `ISO Sensitivity`, `Exposure Bias`, `White Balance`, `Format / Demosaic`, `Resolution`, `Captured At`, `GPS Coordinates`.
- **Platform Share (Android)**: Invokes `Intent.ACTION_SEND` with `Uri.parse(media.uri)`, `type = "image/jpeg"` or `"video/mp4"`, and `Intent.FLAG_GRANT_READ_URI_PERMISSION`.

#### Identified Gaps & Polish Recommendations
1. **Actual Image Rendering**:
   - `GalleryPreviewSheet` does not currently decode or render real image files from `media.uri` or disk.
   - **Recommendation**: Integrate bitmap loading (via Compose Multiplatform image loading or Android content resolver bitmap decoder) with graceful fallback to simulated high-res photo rendering.
2. **Material 3 Expressive EXIF Card Layout**:
   - Current EXIF card is a simple vertical list in a Card below the viewport.
   - **Recommendation**: Transform the EXIF card into an authentic Google Photos / Pixel-style sheet with:
     - Metric badge pills: Shutter (`1/250s`), Aperture (`f/1.68`), Focal Length (`24mm`), ISO (`ISO 100`).
     - Computational badges: `50 MP Ultra HDR DNG`, `Real Tone Night Synthesis`.
     - Location Map chip with GPS coordinates.
     - Swipe-up expandable bottom sheet interaction.
3. **Interactive Gestures**:
   - **Recommendation**: Add double-tap zoom, pinch-to-zoom, and swipe-down dismiss gestures for the full-screen gallery viewer.

---

## 3. Requirement R4: Build, Verification & Physical Device Deployment

### 3.1 Build & Dependency Configuration
- **Gradle Version**: Gradle 8.11.1
- **Kotlin Multiplatform**: Kotlin 2.1.0, Compose Multiplatform 1.7.3, AGP 8.7.3
- **Targets**:
  - Android (`androidTarget`, JVM 17, `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`)
  - Desktop JVM (`jvm("desktop")`, JVM 17)
  - iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`)
- **Key Dependencies**:
  - AndroidX CameraX (`1.4.1`): `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-video`
  - AndroidX Exif (`1.3.7`): `androidx.exifinterface`
  - KotlinX Coroutines (`1.10.1`), KotlinX Serialization (`1.8.0`), KotlinX DateTime (`0.6.1`)
  - Compose Multiplatform Material 3 (`1.7.3`)

### 3.2 Desktop Test Suite Verification
- **Command Executed**: `./gradlew :shared:desktopTest`
- **Status**: **PASSED (Exit Code 0)**
- **Test File**: `shared/src/commonTest/kotlin/com/auracam/CameraEngineTest.kt`
- **Tests Evaluated**:
  1. `testCameraModesAndBadges`: Verifies display names and badges for `NIGHT_SIGHT`, `ASTRO`, `PRO`.
  2. `testProSettingsFormatting`: Verifies auto vs. manual string formatting for ISO, Shutter, WB, and Focus distances.
  3. `testComputationalPipelineExifGeneration`: Verifies EXIF metadata construction, focal length mapping, and watermark string generation.
  4. `testAspectRatioDimensions`: Verifies aspect ratio width/height proportions (4:3, 16:9, 1:1).
  5. `testLensFacingZoomBases`: Verifies zoom base constants for Ultra-Wide (0.5x), Wide (1.0x), Telephoto (2.0x), Super-Tele (5.0x).

### 3.3 Android Debug Build Target
- **Command Executed**: `./gradlew :composeApp:assembleDebug`
- **Status**: **PASSED (Exit Code 0)**
- **Output Artifact**: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- **Application ID**: `com.auracam.pixelcamera.debug`
- **Main Activity**: `com.auracam.app.MainActivity`

### 3.4 Connected Physical Android Device & Deployment Status
- **Device Verification**: `adb devices`
  - Output: `00118655F004928 device`
- **Device Specifications**:
  - Model: `Nothing A015`
  - OS Version: `Android 16` (Build Version Release `16`)
- **Package Status**:
  - Package `com.auracam.pixelcamera.debug` installed on device.
- **Activity Launch Command**:
  - `adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity` (Verified successful launch).
- **UI Screen Capture Verification**:
  - Screen capture taken via `adb shell screencap -p /sdcard/auracam_survey_screen2.png` and inspected.
  - Verified active viewfinder stream, M3 Expressive top controls, zoom selector bar, mode carousel, shutter button, and hardware camera indicator.

---

## 4. Source File & Component Reference Table

| Requirement Area | File Path | Key Classes / Functions | Primary Responsibility |
|---|---|---|---|
| **R3: Pro Sheet** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` | `ProControlsSheet`, `IsoControl`, `ShutterControl`, `FocusControl`, `EvControl`, `WbControl`, `HistogramViewer` | Tabbed manual camera controls & responsive slider UI |
| **R3: Pro Domain** | `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraModels.kt` | `ProSettings`, `HistogramData`, `ExifInfo`, `CapturedMedia` | Pro camera parameter data structures & string formatters |
| **R3: Histogram Pipeline** | `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt` | `processImageFrameForHistogram` | 32-bin live RGB & Luminance analysis on CameraX frame buffer |
| **R3: Gallery Sheet** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/GalleryPreviewSheet.kt` | `GalleryPreviewSheet`, `ExifRow` | In-app photo viewer, EXIF details card, and watermark pill |
| **R3: Native Share** | `composeApp/src/androidMain/kotlin/com/auracam/ui/util/PlatformShare.android.kt` | `rememberPlatformShare` | Native Android `Intent.ACTION_SEND` chooser integration |
| **R3: Pipeline Exif** | `shared/src/commonMain/kotlin/com/auracam/processing/ComputationalPipeline.kt` | `generateExif`, `formatPixelWatermark`, `processCapture` | Computational pipeline EXIF and watermark generation |
| **R4: Build Config** | `build.gradle.kts`, `composeApp/build.gradle.kts`, `shared/build.gradle.kts` | KMP plugins, target configs | Multiplatform compilation for Android, Desktop JVM, and iOS |
| **R4: Manifest & App**| `composeApp/src/androidMain/AndroidManifest.xml`, `MainActivity.kt` | `MainActivity` | Android permissions, immersive full-screen setup, launcher filters |
| **R4: Unit Tests** | `shared/src/commonTest/kotlin/com/auracam/CameraEngineTest.kt` | `CameraEngineTest` | Unit tests for camera engine models, EXIF, and Pro settings |

---

## 5. Architectural Recommendations for Implementation Phase

1. **Custom M3 Expressive Pro Slider (`PixelProSlider`)**:
   - Implement custom track drawing with rounded tick marks, highlighted active progress range, spring-damped thumb motion, and haptic feedback on discrete step boundaries.
2. **Bezier Curve Live Histogram (`PixelLiveHistogram`)**:
   - Replace bar chart with smooth cubic bezier paths, multi-mode selector (RGB Overlay, Luminance, RGB Split), and highlight/shadow clipping indicators.
3. **Interactive Pixel Gallery Card (`PixelGalleryViewer`)**:
   - Upgrade `GalleryPreviewSheet` with genuine bitmap decoding, interactive pinch-to-zoom, and an expandable M3 Expressive EXIF card featuring pill badges and metadata iconography.
4. **Automated Verification Pipeline**:
   - Standardize automated testing and deployment workflow:
     1. Run `./gradlew :shared:desktopTest`
     2. Run `./gradlew :composeApp:installDebug`
     3. Launch `adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity`
     4. Capture verification screenshot via `adb shell screencap -p /sdcard/auracam_verify.png && adb pull /sdcard/auracam_verify.png`.
