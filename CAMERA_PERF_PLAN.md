# Camera & Performance Plan

Scope: make the camera features real (not UI-only) and remove the measured performance
hot spots. Every task lists the exact file, the defect, and the acceptance check.

## A. Camera correctness

| # | Defect | File | Fix | Accept |
|---|--------|------|-----|--------|
| A1 ✅ | `CaptureFormat.RAW_DNG` writes a JPEG but labels it `image/x-adobe-dng` with a `.dng` name. Corrupt file that no viewer can open. | `AndroidCameraEngine.capturePhoto` | Gate RAW on `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW`; when unsupported, fall back to JPEG and report it. Only claim DNG when a DngCreator path actually wrote one. | Saved file's mime matches its bytes; `exiftool`/decoder opens it |
| A2 ✅ | Pro ISO / shutter / white balance / manual focus are UI-only. Only EV reaches the camera. | `AndroidCameraEngine.updateProSettings` | Apply via `Camera2Interop.Extender` on `ImageCapture` + `Preview`: `CONTROL_AE_MODE=OFF`, `SENSOR_SENSITIVITY`, `SENSOR_EXPOSURE_TIME`, `CONTROL_AWB_MODE=OFF`, `COLOR_CORRECTION_GAINS`, `CONTROL_AF_MODE=OFF`, `LENS_FOCUS_DISTANCE`. Clamp each to the device's reported range; skip silently when the device lacks `MANUAL_SENSOR`. | Changing ISO visibly changes exposure; values land in saved EXIF |
| A3 ✅ | Aspect ratio only crops the viewfinder frame; capture always uses the sensor default. | `AndroidCameraEngine.startCamera` | Set `ResolutionSelector` with `AspectRatioStrategy` on `Preview` + `ImageCapture`. | 1:1 selection produces a square file |
| A4 ✅ | Colour profile LUTs are translucent overlays on the viewfinder only; saved images are unaffected. | `ViewfinderScreen`, engine | Either apply the LUT to the captured bitmap before save, or relabel the control as "preview only". Do not ship a control that implies it changes the file. | Saved file matches what the viewfinder showed, or the UI says preview-only |
| A5 ✅ | Video stabilization setting is inert. | `AndroidCameraEngine` | `CONTROL_VIDEO_STABILIZATION_MODE` when `availableVideoStabilizationModes` allows; otherwise disable the toggle. | Toggle changes recorded output or is disabled |
| A6 ✅ | Focus peaking and zebra clipping toggles do nothing. | `ProControlsSheet`, engine | Implement from the `ImageAnalysis` frame already available in Pro mode (Sobel threshold overlay / luma > 250 stripes), or remove the toggles. | Overlay appears, or control is gone |
| A7 ✅ | Night Sight / Astro / Long Exposure / Portrait / Cinematic / Panorama all take one ordinary frame; only the progress text differs. | `ComputationalPipeline.processCapture` | Out of scope to implement fully. Mark them honestly in the UI and README rather than showing fake pipeline stages. | No fabricated progress messages for work not performed |

## B. Performance

| # | Hot spot | File | Fix | Accept |
|---|----------|------|-----|--------|
| B1 ✅ | `processAnalysisFrame` allocates 4 `IntArray(32)` + `ByteArray(rowStride)` per frame at 30fps, then 4 boxed `List<Int>` for `HistogramData`. Sustained GC churn while Pro mode is open. | `AndroidCameraEngine` | Hoist the arrays to reusable fields, `fill(0)` per frame. Change `HistogramData` to hold `IntArray` instead of `List<Int>`, or emit at 10fps rather than every frame. | No steady-state allocation per frame in a profile |
| B2 ✅ | `rememberMediaImage` re-decodes the full bitmap on every recomposition and every rail scroll. No cache. | `MediaImage.android.kt` | Add a size-bounded LRU keyed by `uri + maxDimension`, backed by `LruCache` sized from `Runtime.maxMemory()/8`. | Scrolling the rail does not re-decode |
| B3 ✅ | `refreshGallery` opens and parses EXIF for every file on every gallery open, on one IO dispatcher pass. | `AndroidCameraEngine.refreshGallery` | Return the list from the MediaStore cursor immediately; load EXIF lazily per item when the details sheet opens. | Gallery opens without an EXIF read per file |
| B4 ✅ | `ViewfinderScreen` collects ~19 `StateFlow`s at the top of one composable, so any single change recomposes the whole tree. | `ViewfinderScreen` | Push collection down to the components that use each value, or group into one derived UI-state class. | Zoom change does not recompose the mode carousel |
| B5 ✅ | Mode change calls `provider.unbindAll()` then rebinds, causing a visible black flash. | `AndroidCameraEngine.startCamera` | Only rebind when the use-case set actually differs; keep `Preview` bound across a mode change where possible. | Switching Photo→Portrait shows no black frame |
| B6 ✅ | No baseline profile; release start-up runs fully interpreted. | `composeApp` | Add `androidx.baselineprofile` plugin and generate a profile. | `baseline-prof.txt` present in the release APK |

## Status

All 13 tasks complete. Two caveats:

- **A7** was resolved honestly, not implemented. The staged progress strings that claimed
  frame stacking, depth estimation and star tracking were replaced with neutral text, and
  modes without a real pipeline now show "<mode> saves a single exposure in this build".
  No multi-frame pipeline was written.
- **B6** ships the Compose libraries' bundled baseline profile via `profileinstaller`
  (`assets/dexopt/baseline.prof` is in the release APK). An app-specific profile still needs
  a `:baselineprofile` benchmark module run against a device.

## Execution

Tasks are dispatched individually to `opencode run`, each verified here with
`./gradlew :composeApp:assembleDebug :shared:desktopTest :composeApp:lintRelease`
plus an on-device check where the change is observable.

Note: `opencode` Zen and Go balances are exhausted; only `*-free` models are usable, so
tasks are scoped narrowly and reviewed rather than trusted.
