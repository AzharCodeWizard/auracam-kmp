# Handoff Report — Explorer 1 (Camera Architecture & Dual Streaming)

## 1. Observation

### 1.1 Architecture & Project Setup
- Root directory: `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp`
- Multiplatform Gradle Modules:
  - `shared`: Multiplatform domain, `CameraEngine` interface, `BaseCameraEngine`, `CameraModels`, `CameraEnums`, `ComputationalPipeline`, and test suite.
  - `composeApp`: Compose Multiplatform application for Android, Desktop, and iOS.
- Test Command: `./gradlew testDebugUnitTest` executed with code 0 (`BUILD SUCCESSFUL in 564ms`, 41 actionable tasks up-to-date).
- Connected Target Device: `00118655F004928` (Nothing Phone (2a), model `A015`) running Android via ADB at `/Users/azhar/Library/Android/sdk/platform-tools/adb`.

### 1.2 Core Code Observations
1. **Camera Mode & Dual Vlog Enums**:
   - `shared/src/commonMain/kotlin/com/auracam/camera/domain/CameraEnums.kt`:
     - Line 17: `DUAL_VLOG("Dual Vlog", "DUAL", true)`
     - Lines 149–154:
       ```kotlin
       @Serializable
       enum class DualVlogLayout(val label: String) {
           PIP_RECT("PiP Rect"),
           PIP_CIRCLE("PiP Circle"),
           SPLIT_50_50("50/50 Split"),
           SIDE_BY_SIDE("Side by Side")
       }
       ```
     - Lines 80–90: `ColorProfile` enum (`NATURAL`, `REAL_TONE`, `VIBRANT`, `CINEMATIC_WARM`, `HIGH_CONTRAST_MONO`, etc.).
2. **Platform Camera Engine & Concurrent Camera Support**:
   - `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt`:
     - Lines 145–163: `bindSecondaryPreview(previewView: PreviewView)` and `unbindSecondaryPreview()`.
     - Lines 444–498: Multi-Stream Concurrent Camera Binding:
       ```kotlin
       val secondarySelector = if (_currentLens.value == LensFacing.FRONT) {
           targetLens()?.selector ?: CameraSelector.DEFAULT_BACK_CAMERA
       } else {
           CameraSelector.DEFAULT_FRONT_CAMERA
       }
       if (isDualVlog && provider.hasCamera(secondarySelector)) {
           val secView = secondaryPreviewView!!
           ...
           val canBindConcurrent = runCatching {
               provider.availableConcurrentCameraInfos.any { it.size >= 2 }
           }.getOrDefault(false)
           if (canBindConcurrent) {
               val primaryGroup = UseCaseGroup.Builder().addUseCase(preview!!)...build()
               val secondaryGroup = UseCaseGroup.Builder().addUseCase(secondaryPreview!!).build()
               val primaryConfig = ConcurrentCamera.SingleCameraConfig(cameraSelector, primaryGroup, owner)
               val secondaryConfig = ConcurrentCamera.SingleCameraConfig(secondarySelector, secondaryGroup, owner)
               val concurrentCam = provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))
               camera = concurrentCam.cameras.firstOrNull()
               secondaryCamera = concurrentCam.cameras.getOrNull(1)
           }
       }
       ```
3. **Compose UI Layer & Dual Vlog Overlay**:
   - `composeApp/src/commonMain/kotlin/com/auracam/ui/components/DualVlogLayer.kt`:
     - Lines 52–336: `DualVlogOverlay` implementing 50/50 split and floating draggable corner-snapping PiP window with top Director Island.
   - `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt`:
     - Lines 171–204: Conditionally renders `DualVlogOverlay` and suppresses framing grids, 3D leveler, and zebra exposure mask overlays when in `CameraMode.DUAL_VLOG`.
   - `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt`:
     - Lines 48: `CameraMode.DUAL_VLOG` placed in mode sequence between `TIME_LAPSE` and `CINEMATIC`.

---

## 2. Logic Chain

1. **Dual Camera Hardware Access**:
   - Observation: `AndroidCameraEngine.kt` uses CameraX 1.4.1 concurrent camera APIs (`provider.availableConcurrentCameraInfos`, `ConcurrentCamera.SingleCameraConfig`, `provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))`).
   - Inference: The platform engine is capable of simultaneously streaming both the primary (Rear Wide) and secondary (Front) cameras to distinct `PreviewView` surfaces when supported by device hardware.
2. **Director-Style Viewport & Interaction**:
   - Observation: `DualVlogOverlay` in `DualVlogLayer.kt` already manages layout state (`DualVlogLayout.SPLIT_50_50`, `DualVlogLayout.PIP_RECT`) and draggable offset animations.
   - Inference: Refining this component to match Samsung Galaxy Director-style aesthetics (edge-to-edge 50/50 split with minimal floating swap pill, 4-corner magnetic snap PiP, top Director Control Island, and live tone filter synchronization) provides a seamless user experience.
3. **Synchronized Live Tone Filters**:
   - Observation: Color profiles are tracked reactively via `engine.colorProfile: StateFlow<ColorProfile>` and picked via `FilterDrawer.kt`.
   - Inference: Color matrix / tone grade styling applied at the viewport container level automatically renders across both camera feeds in real time without duplicating shader logic.
4. **Single Combined Video Recording**:
   - Observation: Standard CameraX `VideoCapture` only records from the primary use case group unless composed into a single stream.
   - Inference: For full single-file combined video recording with audio, `PlatformCameraEngine` and `BaseCameraEngine` must coordinate recording state, microphone audio permission (`Manifest.permission.RECORD_AUDIO`), and output a single unified MP4 video.

---

## 3. Caveats

1. **Device-Specific Concurrent Camera Hardware Limitations**:
   - While modern flagships (Samsung Galaxy S-series, Pixel, Nothing Phone) support concurrent camera feeds, some lower-tier chipsets may restrict concurrent resolution (e.g. 1080p max) or may not report concurrent camera IDs.
   - The engine handles this via `canBindConcurrent` check with graceful single-stream fallback.
2. **Audio Permission**:
   - Recording audio requires `Manifest.permission.RECORD_AUDIO` which is permission-gated before enabling audio capture in `prepareRecording`.

---

## 4. Conclusion

The AuraCam KMP codebase provides a well-structured foundation for the Samsung Galaxy Director-style Dual Recording project.
- **Architectural readiness**: All core interfaces (`CameraEngine`), Android engine implementations (`PlatformCameraEngine`), preview wrappers (`CameraPreview`, `SecondaryCameraPreview`), and UI containers (`DualVlogOverlay`, `ViewfinderScreen`, `ModeCarousel`) are in place.
- **Detailed Findings Documented**: A comprehensive survey report has been generated at `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_1/survey_camera.md`.

---

## 5. Verification Method

1. **Unit Test Verification**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **Desktop Mock Engine Test**:
   ```bash
   ./gradlew :shared:desktopTest
   ```
3. **Physical Android Device Compilation & Installation**:
   ```bash
   ./gradlew :composeApp:installDebug
   ```
4. **Files to Inspect**:
   - `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_1/survey_camera.md`
   - `shared/src/androidMain/kotlin/com/auracam/camera/domain/AndroidCameraEngine.kt`
   - `composeApp/src/commonMain/kotlin/com/auracam/ui/components/DualVlogLayer.kt`
   - `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt`
