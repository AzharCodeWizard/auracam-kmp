# Handoff Report: UI / Compose Architecture Survey for Director-Style Dual Recording

## 1. Observation

1. **`ViewfinderScreen.kt` (lines 171-203)**:
   - Evaluated viewfinder layering order:
     ```kotlin
     // Multi-Stream Dual Vlog / Director's View Overlay
     if (cameraMode == CameraMode.DUAL_VLOG) {
         DualVlogOverlay(
             engine = engine,
             isRecording = isRecording,
             onFlipStream = {
                 val nextLens = if (currentLens == LensFacing.FRONT) LensFacing.BACK_WIDE else LensFacing.FRONT
                 engine.setLens(nextLens)
             },
             modifier = Modifier.fillMaxSize()
         )
     }

     if (cameraMode != CameraMode.DUAL_VLOG) {
         ExposureMaskLayer(engine = engine, modifier = Modifier.fillMaxSize())
         FramingGridOverlay(gridType = if (settings.framingHintsEnabled) gridType else GridType.NONE, modifier = Modifier.fillMaxSize())
         if (settings.framingHintsEnabled) {
             LevelerLayer(engine = engine, onLevelReached = soundAndHaptics::vibrateLevelLock, modifier = Modifier.align(Alignment.Center))
         }
     }
     ```
   - Observed that clutter suppression for `ExposureMaskLayer`, `FramingGridOverlay`, and `LevelerLayer` is already partially wired up for `CameraMode.DUAL_VLOG`, but top-level controls and focus brackets still require polish during active dual recording.

2. **`DualVlogLayer.kt` (lines 58-236)**:
   - Evaluated current PiP and Split implementations:
     - PiP offset calculation relies on fixed pixel coordinates: `snapLeft = rawDragOffset.x < 360f`, `targetX = if (snapLeft) 20f else (maxX - 280f).coerceAtLeast(400f)`. This does not dynamically adapt across different screen densities and aspect ratios.
     - PiP window dimensions are fixed to `114.dp x 152.dp` (3:4 ratio) rather than standard 16:9 / 4:3 video proportions.
     - In 50/50 Split view, the primary stream is rendered fullscreen across the entire screen backdrop while the bottom half is masked with `SecondaryCameraPreview`.

3. **`CameraPreview.android.kt` (lines 16-75)**:
   - Primary preview (`CameraPreview`) and secondary preview (`SecondaryCameraPreview`) use individual `PreviewView` instances configured with `PreviewView.ScaleType.FILL_CENTER` and `PreviewView.ImplementationMode.PERFORMANCE`.
   - Android lifecycle binding calls `engine.bindToLifecycle(context, lifecycleOwner, previewView)` and `engine.bindSecondaryPreview(secondaryPreviewView)`.

4. **`AndroidCameraEngine.kt` (lines 451-498)**:
   - Concurrent camera binding checks `provider.availableConcurrentCameraInfos.any { it.size >= 2 }` and binds dual `SingleCameraConfig` groups with `preview` and `secondaryPreview`.

5. **`ColorProfile` enum (`CameraEnums.kt` lines 80-90)**:
   - Tone filters supported: `NATURAL`, `REAL_TONE`, `VIBRANT`, `CINEMATIC_WARM`, `HIGH_CONTRAST_MONO`, `VINTAGE_FILM`, `COOL_BREEZE`, `ASTRO_BOOST`, `CLEAN_DOC`.

6. **Unit test execution command (`./gradlew testDebugUnitTest`)**:
   - Command executed cleanly with code 0 (`BUILD SUCCESSFUL in 1s`, 41 actionable tasks).

---

## 2. Logic Chain

1. From **Observation 1**, `ViewfinderScreen.kt` controls the overlay hierarchy. Viewfinder clutter (grids, leveler, zebra masks) is suppressed conditionally when `cameraMode == CameraMode.DUAL_VLOG`. This architecture should be preserved and enhanced to ensure a distraction-free director interface.
2. From **Observation 2**, `DualVlogLayer.kt` currently lacks dynamic coordinate bounds and proportional 16:9 / 4:3 geometry. Implementing responsive corner calculations based on container `maxWidth` and `maxHeight` ensures reliable magnetic corner snapping (Top-Left, Top-Right, Bottom-Left, Bottom-Right).
3. From **Observation 2 and 3**, in 50/50 Split mode, dividing the viewport into two equal 50% vertical halves (`Modifier.weight(1f).fillMaxWidth()`) with isolated `CameraPreview` and `SecondaryCameraPreview` containers using `clipToBounds()` guarantees zero bezel waste and true 50/50 framing.
4. From **Observation 1, 2, and 5**, the Top Director Control Island should unify layout switching (`[ 🌓 Split 50/50 | 🔲 PiP ]`), stream swapping (`[ ⇄ Swap ]`), and live tone filter selection (`[ ✨ Filter ]`). Propagating selected `ColorProfile` through `engine.setColorProfile(profile)` updates both streams synchronously.

---

## 3. Caveats

- **Physical Dual Camera Hardware Variations**: Devices with non-concurrent camera hardware (or devices lacking concurrent camera support in CameraX HAL) fall back to single camera mode or simulated secondary feeds. The UI layout components remain fully functional regardless of hardware stream capability.
- **Audio Synchronization**: UI displays recording HUD indicators; backend audio/video muxing into a single MP4 file is handled by the recording pipeline (analyzed separately by backend/engine surveys).

---

## 4. Conclusion

The UI and Compose architecture of AuraCam is well-structured for the Samsung Galaxy Director-style Dual Recording feature. The recommended implementation plan is:
1. **Refactor `DualVlogLayer.kt`** into modular components:
   - `DirectorDualRecordingOverlay`
   - `DirectorControlIsland`
   - `DirectorSplit5050View`
   - `DirectorMovablePipView`
   - `DirectorFilterPicker`
2. **Implement responsive 4-corner magnetic snapping** in PiP mode using spring animation physics (`animateOffsetAsState`).
3. **Implement edge-to-edge 50/50 Split View** with floating center swap control.
4. **Integrate floating frosted glass Director Island** with layout toggle, stream swap, and live tone filter picker.
5. **Ensure clutter auto-hiding** suppresses grids, levelers, and zebra masks when `CameraMode.DUAL_VLOG` is active.

All findings are documented in detail in `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_2/survey_ui.md`.

---

## 5. Verification Method

1. **Inspect Survey Report**:
   ```bash
   view_file /Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_2/survey_ui.md
   ```
2. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Compile and Deploy to Android Device (Nothing Phone 2a)**:
   ```bash
   ./gradlew :composeApp:installDebug
   /Users/azhar/Library/Android/sdk/platform-tools/adb shell am start -n com.auracam.app/.MainActivity
   ```
4. **Invalidation Conditions**:
   - PiP fails to snap smoothly to any of the 4 corners.
   - 50/50 Split mode produces bezel padding or uneven division.
   - Tone filter changes fail to reflect synchronously on both camera feeds.
