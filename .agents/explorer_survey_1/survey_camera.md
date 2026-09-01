# Comprehensive Camera Architecture & Dual Streaming Survey Report

**Project**: AuraCam KMP — Samsung Galaxy Director-style Dual Recording (Vlog Mode)  
**Surveyor**: Explorer 1 (Camera Architecture & Dual Streaming)  
**Target Codebase**: `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp`  
**Date**: September 1, 2026  

---

## 1. Executive Summary

This investigation explores the camera capture architecture in AuraCam across Kotlin Multiplatform (KMP) shared domain and Android source sets. The goal is to provide a comprehensive, rigorous architectural blueprint for implementing a **Samsung Galaxy Director-style Dual Recording (Vlog Mode)** featuring seamless 50/50 Split and Picture-in-Picture (PiP) modes, live synchronized tone filters, and a single combined video recording output.

### Key Takeaways
1. **Camera Architecture Foundation**: AuraCam employs a clean multiplatform architecture:
   - `shared` module defines the reactive `CameraEngine` contract (`StateFlow`s for mode, zoom, exposure, filters, video recording, etc.) and `BaseCameraEngine` mock/simulation pipeline.
   - `shared/src/androidMain` contains `PlatformCameraEngine` built on **CameraX 1.4.1** + **Camera2 Interop** (`Camera2CameraInfo`, `Camera2CameraControl`, `CaptureRequestOptions`).
   - `composeApp` provides declarative UI using Compose Multiplatform with platform `AndroidView(PreviewView)` bridges (`CameraPreview` and `SecondaryCameraPreview`).
2. **Concurrent Dual Streaming Support**:
   - Android 11+ (API 30+) provides `CameraManager.getConcurrentCameraIds()` and CameraX provides `ProcessCameraProvider.availableConcurrentCameraInfos` + `ConcurrentCamera.SingleCameraConfig`.
   - `PlatformCameraEngine` already possesses the foundational hooks (`bindSecondaryPreview(PreviewView)`, concurrent camera binding via `provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))`).
3. **Director-Style Viewport & Filter System**:
   - The UI layer contains `DualVlogOverlay` in `composeApp/src/commonMain/kotlin/com/auracam/ui/components/DualVlogLayer.kt`.
   - Layout state supports `DualVlogLayout.SPLIT_50_50` and `DualVlogLayout.PIP_RECT` / `PIP_CIRCLE`.
   - Live tone filters (Real Tone, Vibrant, Cinematic Warm, Monochrome, Natural) can be applied synchronously across both feeds using Compose hardware-accelerated color matrices / shaders.
   - Viewfinder clutter (grids, leveler, zebra masks) is cleanly suppressed during Dual Vlog mode.
4. **Single Combined Video Recording**:
   - Recording both feeds into a single unified MP4 video with synchronized audio requires compositing the primary and secondary camera frames into a single video encoder stream (`DualStreamRecorder` / OpenGL ES Surface Compositor or CameraX Recorder configuration).

---

## 2. Current Camera Architecture Deep Dive

### 2.1 Multiplatform Domain Model & Engine (`shared`)

The camera domain is structured around reactive Kotlin Coroutines `StateFlow` streams.

#### File Inventory
- `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraEngine.kt`:
  - Defines the core contract for all camera operations.
  - Exposes 30+ reactive StateFlows including `cameraMode`, `currentLens`, `zoomRatio`, `aspectRatio`, `colorProfile`, `captureFormat`, `isRecording`, `recordingDurationSeconds`, `captureProgress`, `galleryList`, and `recentMedia`.
- `shared/src/commonMain/kotlin/com/auracam/camera/domain/BaseCameraEngine.kt`:
  - Abstract base class managing coroutine scope (`Dispatchers.Default + SupervisorJob()`), internal `MutableStateFlow` backing properties, sensor leveler / histogram simulations, photo capture countdown, and video recording timers.
- `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraEnums.kt`:
  - `CameraMode`: Includes `DUAL_VLOG("Dual Vlog", "DUAL", true)` alongside `PHOTO`, `VIDEO`, `PRO`, `NIGHT_SIGHT`, etc.
  - `DualVlogLayout`: `PIP_RECT`, `PIP_CIRCLE`, `SPLIT_50_50`, `SIDE_BY_SIDE`.
  - `LensFacing`: `BACK_ULTRA_WIDE (0.5x)`, `BACK_WIDE (1.0x)`, `BACK_TELEPHOTO (2.0x)`, `BACK_SUPER_TELE (5.0x)`, `FRONT (1.0x)`.
  - `ColorProfile`: `NATURAL`, `REAL_TONE`, `VIBRANT`, `CINEMATIC_WARM`, `HIGH_CONTRAST_MONO`, `VINTAGE_FILM`, `COOL_BREEZE`, `ASTRO_BOOST`, `CLEAN_DOC`.
  - `VideoResolution`: `UHD_4K_60`, `UHD_4K_30`, `FHD_1080P_60`, `FHD_1080P_30`, `HD_720P_30`.

### 2.2 Android Hardware Engine (`PlatformCameraEngine`)

Implemented in `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt` (1434 lines):

```
+-------------------------------------------------------------------------------+
|                             PlatformCameraEngine                              |
|                                                                               |
|  +---------------------------+  +------------------------------------------+  |
|  |   ProcessCameraProvider   |  |   Camera2 Interop Capabilities           |  |
|  +---------------------------+  +------------------------------------------+  |
|                |                                       |                      |
|                v                                       v                      |
|  +---------------------------+          +----------------------------------+  |
|  | Primary Use Cases:        |          | Manual Sensor Settings:          |  |
|  | - Preview                 |          | - ISO / Exposure Time            |  |
|  | - ImageCapture (50MP/12MP)|          | - AWB Kelvin                     |  |
|  | - ImageAnalysis (RGBA)    |          | - Manual Lens Focus              |  |
|  | - VideoCapture<Recorder>  |          | - Hardware OIS / Denoise / Edge  |  |
|  +---------------------------+          +----------------------------------+  |
|                |                                                              |
|                v                                                              |
|  +-------------------------------------------------------------------------+  |
|  | Concurrent Multi-Camera (Dual Vlog):                                    |  |
|  | - Primary Config: CameraSelector (Back/Front) + Primary UseCaseGroup    |  |
|  | - Secondary Config: CameraSelector (Front/Back) + Secondary UseCaseGroup|  |
|  | - ConcurrentCamera = provider.bindToLifecycle(listOf(primary, secondary))|
|  +-------------------------------------------------------------------------+  |
+-------------------------------------------------------------------------------+
```

#### Lifecycle & Use Case Binding
- **Lifecycle Attachment**: `bindToLifecycle(context, lifecycleOwner, previewView)` initializes sensors, location, orientation listener, queries camera provider, discovers hardware lenses, and binds use cases.
- **Use Case Cache Key**: `useCaseSignature()` prevents redundant unbinding/rebinding during UI recomposition:
  ```kotlin
  "${if (isVideoMode(_cameraMode.value)) "video" else "photo"}|$analysis|$facing|${aspectGroup(_aspectRatio.value)}|dual=$isDual"
  ```
- **Secondary Preview Lifecycle**:
  - `bindSecondaryPreview(previewView: PreviewView)`: caches the secondary preview surface and re-executes `startCamera()` when in `DUAL_VLOG` mode.
  - `unbindSecondaryPreview()`: cleans up secondary preview references.
- **Resource Teardown**: `release()` cleanly cancels in-flight recordings, removes analyzers, disables orientation and sensor listeners, shuts down `cameraExecutor` and `analysisExecutor`, and cancels coroutines.

---

## 3. Dual Camera Streaming on Android

### 3.1 Android API & CameraX Concurrent Streaming

Concurrent camera streaming is supported on Android 11+ (API 30+) devices where the SoC/ISP supports simultaneous hardware pipelines:
1. **Camera2 API**: `CameraManager.getConcurrentCameraIds()` returns sets of camera IDs that can stream concurrently (e.g. `[{"0", "1"}]` for rear main + front).
2. **CameraX API (v1.4.1)**:
   - `provider.availableConcurrentCameraInfos`: Returns combinations of `CameraInfo`s capable of concurrent streaming.
   - `ConcurrentCamera.SingleCameraConfig`: Associates each `CameraSelector` with its `UseCaseGroup` and `LifecycleOwner`.
   - `provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))`: Returns `ConcurrentCamera` containing `cameras: List<Camera>`.

### 3.2 Dual Viewfinder Surface Routing

In AuraCam, preview routing is decoupled into two composable views:
- **`CameraPreview`** (`composeApp/src/androidMain/kotlin/com/auracam/ui/components/CameraPreview.android.kt`):
  - Wraps a `PreviewView` with `ImplementationMode.PERFORMANCE` and binds to `engine.bindToLifecycle(...)`.
- **`SecondaryCameraPreview`**:
  - Wraps a secondary `PreviewView` with `ImplementationMode.PERFORMANCE` and binds to `engine.bindSecondaryPreview(...)`.

### 3.3 Hardware Availability & Graceful Fallbacks

On devices without concurrent camera hardware support (or simulators):
- `provider.availableConcurrentCameraInfos` returns an empty list.
- `PlatformCameraEngine` gracefully catches exceptions and logs a fallback warning, maintaining primary camera streaming without crashing.
- On desktop / unit tests, `BaseCameraEngine` runs mock simulation pipelines cleanly.

---

## 4. Samsung Galaxy Director-Style Dual Recording Architecture

### 4.1 R1: Samsung-Style 50/50 Split View
- **Layout Specification**:
  - Divides the active viewport evenly: Top 50% for Primary Stream (Rear Wide) and Bottom 50% for Secondary Stream (Front Camera).
  - Zero wasted bezel space: Both streams scale with `ScaleType.FILL_CENTER` / `ContentScale.Crop` to fill their respective 50% bounds.
- **Floating Minimal Divider & Swap Action**:
  - Center horizontal hairline divider with frosted glass pill button: `[ ⇄ SWAP ]`.
  - Tapping swaps top and bottom camera feeds seamlessly (swapping `currentLens` from `BACK_WIDE` to `FRONT` or switching surface assignment).

### 4.2 R2: Clean Movable Picture-in-Picture (PiP) Mode
- **Layout Specification**:
  - Primary camera occupies the full viewfinder stage (100% fill).
  - Secondary camera floats in an inset card (16:9 or 4:3 aspect ratio, 114dp x 152dp, `RoundedCornerShape(22.dp)`, `shadow(12.dp)`).
- **Magnetic Corner Snapping with Spring Physics**:
  - Drag gesture detection via `pointerInput` and `detectDragGestures`.
  - On gesture release, computes nearest viewport quadrant and animates via `animateOffsetAsState` with `Spring(dampingRatio = MediumBouncy, stiffness = MediumLow)` to:
    1. **Top-Left**: `(20dp, 110dp)`
    2. **Top-Right**: `(maxX - 134dp, 110dp)`
    3. **Bottom-Left**: `(20dp, maxY - 200dp)`
    4. **Bottom-Right**: `(maxX - 134dp, maxY - 200dp)`
- **1-Tap Swap Action**:
  - Floating swap badge in the corner of the PiP card to instantly flip full-screen and PiP streams.

### 4.3 R3: Top Director Control Island & Live Tone Filters
- **Top Frosted Glass Director Capsule**:
  - Positioned at `Alignment.TopCenter` with `pixelGlass` styling (`CircleShape`, translucent dark scrim `Color(0xD9101216)`, 1dp white border).
  - Controls:
    1. **Layout Toggle Pill**: `[ 🌓 Split 50/50 | 🔲 PiP ]` with animated transitions.
    2. **Stream Swap Pill**: `[ ⇄ Swap ]` with spring rotation feedback.
    3. **Live Tone Filter Wand**: `[ ✨ Filter ]` opening the Tone Filter Drawer.
- **Synchronized Live Tone Filters**:
  - Real-time color grades applied identically and synchronously to both camera feeds:
    1. `NATURAL`: Neutral true-to-life tones.
    2. `REAL_TONE`: Google Real Tone skin accuracy LUT / warm neutral balance.
    3. `VIBRANT`: High-saturation punchy color matrix.
    4. `CINEMATIC_WARM`: Golden hour film tone grade (warm amber highlights, deep shadows).
    5. `HIGH_CONTRAST_MONO`: High-contrast black & white monochrome.
- **Viewfinder Clutter Auto-Suppression**:
  - In `ViewfinderScreen.kt`, when `cameraMode == CameraMode.DUAL_VLOG`:
    - Framing grid overlay (`FramingGridOverlay`) -> HIDDEN
    - Horizon 3D leveler (`LevelerLayer`) -> HIDDEN
    - Exposure & zebra clipping mask (`ExposureMaskLayer`) -> HIDDEN
    - Result: A pristine, professional Director viewfinder.

### 4.4 R4: Single Combined Video Recording
- **Recording Challenge**: Standard CameraX `VideoCapture` only attaches to a single `UseCaseGroup`. If attached to the primary camera, it only records the primary feed.
- **Recording Architecture Options**:
  1. **Option A: Dedicated OpenGL ES Surface Compositor & MediaCodec Pipeline**:
     - Both camera feeds render to external OpenGL textures (`GL_TEXTURE_EXTERNAL_OES`).
     - A GL surface renderer composes both textures in real-time (Split 50/50 or PiP layout with active color filter shader).
     - Renders directly to `MediaCodec` input surface + `AudioRecord` AAC stream -> `MediaMuxer` -> MP4 file in `DCIM/AuraCam`.
  2. **Option B: Dual Vlog Video Capture Coordination via CameraX VideoCapture + MediaStore**:
     - Synchronizes recording duration, audio recording with permission check (`Manifest.permission.RECORD_AUDIO`), and produces a high-definition MP4 output registered to MediaStore.
     - Fallback / Mock capture pipeline in `BaseCameraEngine` for unit tests and non-concurrent environments.

---

## 5. State Management & State Machine Integration

### 5.1 Mode Carousel Integration
- `CameraMode.DUAL_VLOG` is located in `CameraEnums.kt`:
  ```kotlin
  DUAL_VLOG("Dual Vlog", "DUAL", true)
  ```
- `ModeCarousel.kt` lists `CameraMode.DUAL_VLOG` in its ordered sequence:
  `[ NIGHT_SIGHT, PORTRAIT, PHOTO, VIDEO, SLOW_MOTION, TIME_LAPSE, DUAL_VLOG, CINEMATIC, PRO, ASTRO, LONG_EXPOSURE ]`
- Auto-centering and spring snapping ensures fluid scrolling.

### 5.2 Reactive State Machine Flow

```
User selects "DUAL VLOG" on ModeCarousel
                 │
                 ▼
      engine.setMode(DUAL_VLOG)
                 │
                 ▼
PlatformCameraEngine.startCamera()
  ├── Computes signature: "video|false|BACK_WIDE|standard|dual=true"
  ├── Configures Primary Camera (Rear) with Preview + VideoCapture
  ├── Configures Secondary Camera (Front) with SecondaryPreview
  └── Calls provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))
                 │
                 ▼
ViewfinderScreen Compose UI
  ├── Suppresses FramingGrid, HorizonLeveler, ZebraMask
  ├── Renders CameraPreview (Primary stream)
  └── Renders DualVlogOverlay
        ├── Layout: SPLIT_50_50 or PIP_RECT
        ├── Top Director Control Island (Layout, Swap, Filter)
        ├── Renders SecondaryCameraPreview in bottom half or PiP card
        └── Applies active ColorProfile synchronously to both feeds
                 │
                 ▼
User taps Shutter Button
  ├── Audio/Haptics: playVideoStartSound() + vibrateSnap()
  └── engine.toggleVideoRecording() -> Starts single combined MP4 recording
```

---

## 6. Catalog of Classes, Files, Interfaces, and Extension Points

| Layer | File Path | Key Components | Extension Points & Notes |
|---|---|---|---|
| **Domain Enums** | `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraEnums.kt` | `CameraMode.DUAL_VLOG`, `DualVlogLayout`, `ColorProfile` | Add/extend layout modes (`PIP_RECT`, `SPLIT_50_50`) and filter definitions |
| **Domain Models** | `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraModels.kt` | `CapturedMedia`, `CaptureProgress`, `ExifInfo` | Holds media metadata, duration, video resolution |
| **Domain Engine** | `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraEngine.kt` | `CameraEngine` interface | Public contract for mode, lens, colorProfile, isRecording |
| **Base Engine** | `shared/src/commonMain/kotlin/com/auracam/camera/domain/BaseCameraEngine.kt` | `BaseCameraEngine` | Default state flows, mock recording timers, test support |
| **Android Engine** | `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt` | `PlatformCameraEngine` | `bindSecondaryPreview`, `startCamera`, `ConcurrentCamera` binding, video recording |
| **Compose Preview** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/CameraPreview.kt` | `CameraPreview`, `SecondaryCameraPreview` | `expect` composable declarations |
| **Compose Preview (Android)** | `composeApp/src/androidMain/kotlin/com/auracam/ui/components/CameraPreview.android.kt` | `CameraPreview.android.kt` | `actual` implementations wrapping `PreviewView` |
| **Dual Vlog Layer** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/DualVlogLayer.kt` | `DualVlogOverlay` | Director 50/50 split view, draggable PiP card, top Director Island |
| **Filter Drawer** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/FilterDrawer.kt` | `FilterDrawer` | Live tone filters / LUT picker |
| **Mode Carousel** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt` | `ModeCarousel` | Centered snapping carousel with tactile feedback |
| **Viewfinder Screen** | `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` | `ViewfinderScreen` | Viewfinder stage, overlay suppression, shutter actions |
| **Unit Tests** | `shared/src/commonTest/kotlin/com/auracam/BaseCameraEngineTest.kt` | `BaseCameraEngineTest` | Engine unit test verification (`./gradlew testDebugUnitTest`) |

---

## 7. Recommendations & Implementation Roadmap

1. **DualVlogLayer Polish**:
   - Refine the 50/50 Split View divider with Samsung-style ultra-thin frosted glass pill and smooth animated feed swapping.
   - Enhance the PiP floating window with spring-based 4-corner magnetic snap physics (Top-Left, Top-Right, Bottom-Left, Bottom-Right) and seamless 1-tap swap button.
2. **Top Director Control Island**:
   - Integrate the unified frosted glass control island at the top containing:
     - `[ 🌓 Split 50/50 | 🔲 PiP ]` layout toggle
     - `[ ⇄ Swap ]` camera feed flip button
     - `[ ✨ Filter ]` quick live tone filter selector
   - Ensure non-essential overlays (grids, levelers, zebras) remain hidden during Dual Vlog mode.
3. **Live Tone Filters Synchronized Application**:
   - Apply the selected `ColorProfile` (Real Tone, Vibrant, Cinematic Warm, Monochrome, Natural) synchronously across both camera stream viewports using Compose color matrix / shader modifiers.
4. **Single Combined Video Recording Output**:
   - Ensure `toggleVideoRecording()` in Dual Vlog mode records with synchronized audio from the microphone and writes a single clean MP4 into `DCIM/AuraCam`.
5. **Quality & Verification**:
   - Verify unit tests pass via `./gradlew testDebugUnitTest`.
   - Verify debug APK builds and installs cleanly on connected physical device (`./gradlew :composeApp:installDebug`).
