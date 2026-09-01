# Survey Report: Video Recording, Tone Filtering, Audio Sync & Deployment

**Explorer**: Explorer 3 (Survey & Blueprint)  
**Date**: 2026-09-01  
**Project**: AuraCam KMP — Samsung Galaxy Director-Style Dual Recording (Vlog Mode)  
**Target Device**: Nothing Phone (2a) (Tetris, Android 14/15, SDK 35)

---

## 1. Executive Summary

This investigation surveys AuraCam's existing video recording architecture, tone filter engine, audio synchronization, and testing/deployment pipelines. We provide an end-to-end technical blueprint for implementing Samsung Galaxy Director-style Dual Recording (Vlog Mode):
1. **Single Combined Video Recording**: High-definition (1080p/4K) single MP4 recording capturing the exact active viewport layout (50/50 Split or Movable PiP) with synchronized microphone audio.
2. **Synchronous Live Tone Filters**: Hardware-grade, 1:1 color-graded tone filters (`Real Tone`, `Vibrant`, `Cinematic Warm`, `Monochrome`, `Natural`) rendered simultaneously on both preview streams and baked directly into recorded video frames.
3. **Multi-Stream Dual Recording Architecture**: Concurrent camera handling with EGL/OpenGL ES surface compositing and MediaCodec + MediaMuxer synchronization.
4. **Verification & Deployment Setup**: 100% passing multiplatform unit test suite (`./gradlew testDebugUnitTest`, `./gradlew :shared:desktopTest`) and physical device deployment verification via ADB to the connected Nothing Phone (2a).

---

## 2. Current Video & Audio Recording Pipeline Analysis

### 2.1 Existing Codebase Implementation (`AndroidCameraEngine.kt`)
In the current implementation:
- **CameraX VideoCapture Binding**:
  ```kotlin
  val recorder = Recorder.Builder()
      .setQualitySelector(
          QualitySelector.fromOrderedList(
              orderedQualities,
              FallbackStrategy.lowerQualityOrHigherThan(targetQuality)
          )
      )
      .build()
  videoCapture = VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
  ```
- **Recording Invocation**:
  ```kotlin
  val pending = video.output.prepareRecording(ctx, mediaStoreOutput)
  if (hasPermission(ctx, Manifest.permission.RECORD_AUDIO)) {
      pending.withAudioEnabled()
  }
  activeRecording = pending.start(ContextCompat.getMainExecutor(ctx)) { recordEvent -> ... }
  ```
- **Dual Vlog Binding (`startCamera()` lines 444–498)**:
  - When `DUAL_VLOG` mode is active, `ProcessCameraProvider.bindToLifecycle` attempts concurrent camera binding (`ConcurrentCamera.SingleCameraConfig`).
  - `primaryGroup` contains `preview` + `videoCapture`.
  - `secondaryGroup` contains only `secondaryPreview`.

### 2.2 Critical Gaps in Current Video Recording
1. **Single Camera Output Only**: CameraX's `VideoCapture` is attached exclusively to the primary camera use case group. When recording in Dual Vlog mode, the recorded MP4 file contains **only** the primary camera stream. The secondary camera feed (and any Split or PiP layout) is completely omitted from the recorded file.
2. **No Filter Baking**: Tone filters selected via `FilterDrawer` are only UI overlays or Compose tints; they are never passed to the video encoder.
3. **No Dynamic Layout Switching in File**: If the user toggles between Split 50/50 and PiP or drags the PiP window during recording, the recorded video does not reflect these interactions.

---

## 3. Live Tone Filters Pipeline (Real Tone, Vibrant, Cinematic Warm, Monochrome, Natural)

### 3.1 Color Matrix & Filter Specifications

| Tone Filter | Visual Intent | Color Matrix / Transformation Weights | Preview Application | Video Output Baking |
|---|---|---|---|---|
| **Natural** (`NATURAL`) | Neutral, true-to-life color reproduction | Identity matrix: $R'=R, G'=G, B'=B$ | Default pass-through | Pass-through GLSL shader |
| **Real Tone** (`REAL_TONE`) | Google Real Tone skin tone fidelity, highlight rolloff protection, preserved melanin depth | $R'=1.05R + 0.02G$, $G'=0.98G + 0.02R$, $B'=0.92B$, midtone warmth lift $+0.04$ | Compose `ColorFilter.colorMatrix` across both `PreviewView`s | OpenGL Fragment Shader LUT / Matrix uniform |
| **Vibrant** (`VIBRANT`) | Punchy saturation, vivid skies, rich foliage contrast | Saturation multiplier $1.35\times$, subtle S-curve contrast boost | Compose `ColorFilter.colorMatrix` | OpenGL Fragment Shader color grading pass |
| **Cinematic Warm** (`CINEMATIC_WARM`) | Golden hour 35mm film grade, amber midtones, teal shadow split-tone | Amber warm shift ($R+0.12, G+0.04, B-0.08$), shadow cooling | Compose `ColorFilter.colorMatrix` | 3D LUT / Color grading GLSL shader |
| **Monochrome** (`HIGH_CONTRAST_MONO`) | Deep high-contrast black and white, crisp highlights | $Y = 0.299R + 0.587G + 0.114B$, contrast factor $1.4\times$ | Compose `ColorFilter.colorMatrix` | GLSL Luminance + contrast step |

### 3.2 Synchronous Color Grading Pipeline
To guarantee 100% synchronization:
1. **Viewfinder Preview (Compose Layer)**:
   - `DualVlogOverlay` and `CameraPreview` observe `engine.colorProfile`.
   - Applying `Modifier.drawWithContent` with `ColorFilter.colorMatrix(filterMatrix)` wraps both primary and secondary camera previews instantaneously on the same frame render cycle.
2. **Recorded Stream (Encoder Layer)**:
   - The active `ColorProfile` matrix is passed as a `mat4` uniform to the OpenGL ES compositor shader during video recording.
   - Every frame drawn into the `MediaCodec` input surface has the tone filter baked in at render time.

---

## 4. Single Combined Video Recording Architecture

### 4.1 Visual Layout Specifications

#### A. Samsung-Style 50/50 Split View
- **Screen Allocation**: Top 50% Primary Camera, Bottom 50% Secondary Camera.
- **Aspect Ratio Handling**: Center-cropped to fill $9:8$ aspect ratio per half in standard $9:16$ portrait video ($1080 \times 960$ per camera in $1080 \times 1920$ MP4).
- **Divider & Controls**: 1dp frosted divider with central floating "SWAP" button.
- **Stream Swap**: Instantaneous interchange of top and bottom camera feeds.

#### B. Clean Movable Picture-in-Picture (PiP) Mode
- **Base Feed**: Primary camera rendered full-screen ($1080 \times 1920$).
- **Floating Inset**: Secondary camera rendered in a rounded rectangle ($16:9$ or $4:3$ aspect ratio, $28\%$ screen width, $16\text{dp}$ corner radius, $1.5\text{dp}$ white border and soft shadow).
- **Magnetic Snapping**: Draggable with spring physics to snap to 4 corners:
  - Top-Left: `(x: 20dp, y: 110dp)`
  - Top-Right: `(x: maxX - width - 20dp, y: 110dp)`
  - Bottom-Left: `(x: 20dp, y: maxY - height - 140dp)`
  - Bottom-Right: `(x: maxX - width - 20dp, y: maxY - height - 140dp)`
- **1-Tap Swap**: Corner button to swap main and PiP cameras instantly.

### 4.2 End-to-End Media Pipeline Blueprint

```
 ┌────────────────────────────────────────────────────────┐
 │ Primary Camera (SurfaceTexture / ImageReader 1)        │──┐
 └────────────────────────────────────────────────────────┘  │
                                                             ▼
                                                ┌─────────────────────────┐
                                                │ DualCameraCompositor    │
 ┌──────────────────────────────────────────────────┐ (OpenGL ES 2.0 / EGL)│
 │ Secondary Camera (SurfaceTexture / ImageReader 2)│─▶ Composites:       │
 └──────────────────────────────────────────────────┘  - 50/50 Split      │
                                                       - Movable PiP      │
 ┌──────────────────────────────────────────────────┐  - Live Tone Filter │
 │ Active Tone Filter (ColorMatrix Uniform)         │──▶ Outputs to:      │
 └──────────────────────────────────────────────────┘  MediaCodec Surface │
                                                               │
                                                               ▼
 ┌──────────────────────────────────────────────────┐  ┌──────────────────┐
 │ Audio Source: AudioRecord (MIC / CAMCORDER)      │  │ Video Encoder    │
 │ 44.1kHz / 48kHz, 16-bit PCM Mono/Stereo          │  │ MediaCodec (H264)│
 └──────────────────────────────────────────────────┘  └──────────────────┘
                          │                                     │
                          ▼                                     ▼
                 ┌──────────────────┐                  ┌──────────────────┐
                 │ Audio Encoder    │                  │ Video Track      │
                 │ MediaCodec (AAC) │                  │ H.264 NAL / SPS  │
                 └──────────────────┘                  └──────────────────┘
                          │                                     │
                          └─────────────────┬───────────────────┘
                                            ▼
                                ┌────────────────────────┐
                                │ MediaMuxer             │
                                │ (Synchronized MP4 Mux) │
                                └────────────────────────┘
                                            │
                                            ▼
                                ┌────────────────────────┐
                                │ MediaStore / DCIM      │
                                │ AuraCam/VID_*.mp4      │
                                └────────────────────────┘
```

### 4.3 Audio-Video Synchronization
- **Audio Clock Alignment**: Video frames use nanosecond presentation timestamps derived from camera sensor frame time (`EGLExt.eglPresentationTimeANDROID`).
- **Audio Presentation Timestamps**: Audio samples from `AudioRecord` calculate presentation time via:
  $$\text{PTS}_{\text{audio}} = \text{startNanos} + \left(\frac{\text{totalSamplesRead} \times 1\,000\,000\,000}{\text{sampleRate}}\right)$$
- **Muxer Alignment**: `MediaMuxer` starts writing video and audio tracks only after both encoders have emitted their respective `INFO_OUTPUT_FORMAT_CHANGED` configuration buffers (SPS/PPS for H.264, AudioSpecificConfig for AAC).

---

## 5. Verification, Test Suite & Device Deployment Setup

### 5.1 Existing Unit Test Suite
- **Gradle Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ./gradlew :shared:desktopTest
  ```
- **Current Coverage**:
  - `31/31` tests passing with 0 failures across 5 test classes:
    - `BaseCameraEngineTest`: Zoom clamping, lens selection, focus point normalization, video recording toggle/release state machine.
    - `CameraEngineTest`: CameraMode enums, badges, ProSettings formatting, AspectRatio dimensions, Exif generation.
    - `ExifMetadataTest`: Realistic timestamping, GPS coordinate formatting, auto-ISO fallback.
    - `PixelViewfinderAestheticsTest`: Horizon leveler degree snap thresholds, framing grid geometry, color profile labels.
    - `SettingsStoreTest`: AppSettings persistence and defaults.

### 5.2 Physical Device Verification Setup (Nothing Phone 2a)
- **Target Specifications**:
  - Device: Nothing Phone (2a) (`A015`, codename `Tetris`, Serial: `00118655F004928`)
  - Transport: USB ADB (`/Users/azhar/Library/Android/sdk/platform-tools/adb`)
  - Target SDK: 35 (Android 15 / 14)
- **Automated Deployment Commands**:
  ```bash
  # 1. Compile and install debug APK
  ./gradlew :composeApp:installDebug

  # 2. Launch MainActivity
  /Users/azhar/Library/Android/sdk/platform-tools/adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity

  # 3. Capture device screen to verify UI rendering
  /Users/azhar/Library/Android/sdk/platform-tools/adb exec-out screencap -p > auracam_dual_verified.png
  ```

---

## 6. Recommendations & Implementation Roadmap

1. **Camera Engine Extension (`shared` & `androidMain`)**:
   - Add `activeDualLayout: StateFlow<DualVlogLayout>` and `isStreamSwapped: StateFlow<Boolean>` to `CameraEngine`.
   - Implement `setDualVlogLayout(layout: DualVlogLayout)` and `swapDualStreams()`.
2. **Director Control Island & Viewfinder Refinement (`composeApp`)**:
   - Refine `DualVlogOverlay.kt` to auto-hide exposure zebra masks, focus reticles, and framing grids when in Dual Vlog mode.
   - Provide floating frosted glass Director Island at the top with layout pill, swap pill, and filter button.
3. **Synchronous Live Tone Filters**:
   - Implement Compose `ColorFilter` matrices for `NATURAL`, `REAL_TONE`, `VIBRANT`, `CINEMATIC_WARM`, `HIGH_CONTRAST_MONO`.
   - Apply filter matrix to `PrimaryPreview` and `SecondaryPreview` simultaneously.
4. **Single Combined Video Recorder**:
   - Implement dual-stream composite encoder (`DualCameraCompositor` / `CompositeVideoRecorder`) writing combined Split/PiP frames + active Tone Filter + AAC mic audio into a single MP4.
5. **Testing & Physical Device Deployment**:
   - Add unit tests in `BaseCameraEngineTest` and `PixelViewfinderAestheticsTest` covering Dual Vlog layouts, swap state, and tone filters.
   - Build, install on Nothing Phone (2a), and capture screen verification artifacts.
