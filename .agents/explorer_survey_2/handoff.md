# Handoff Report — Explorer 2: Requirement R2 Survey (Fluid Spring Motion & Tactile Micro-Interactions)

## 1. Observation

Direct code inspection of the AuraCam KMP codebase revealed the following exact locations, structures, and implementations relating to motion, gestures, animations, and tactile feedback:

1. **Camera Mode Carousel** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt:47-92`):
   - Uses `LazyRow(state = listState)` with `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.
   - Scale, text color, and background animations rely on `tween(durationMillis = 200)`:
     ```kotlin
     val scale = animateFloatAsState(
         targetValue = if (isSelected) 1.05f else 0.95f,
         animationSpec = tween(durationMillis = 200)
     )
     ```
   - No `rememberSnapFlingBehavior` or `HorizontalPager` is employed; list does not snap to center or auto-scroll when a mode is tapped. No haptic feedback is triggered during scroll crossing.

2. **Viewfinder Gestures** (`composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt:87-94`):
   - Only a single-tap detector is registered on the viewfinder viewport:
     ```kotlin
     .pointerInput(Unit) {
         detectTapGestures { offset ->
             val normX = offset.x / size.width
             val normY = offset.y / size.height
             engine.setFocusPoint(normX, normY)
             soundAndHaptics.vibrateSnap()
         }
     }
     ```
   - Pinch-to-zoom (`detectTransformGestures`), double-tap lens switch/reset, long-press AE/AF lock, and viewfinder horizontal swipe are entirely absent.

3. **Zoom Selector & Presets** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/ZoomSelector.kt:29-76`):
   - Hardcoded 4 static presets `listOf(0.5f, 1.0f, 2.0f, 5.0f)`.
   - Transitions use `tween(150)` color changes on static circular buttons.
   - Continuous zoom scrubbing (ruler dial / slider), horizontal drag gesture expansion, and tick haptics are missing.

4. **Focus Bracket & Dual Exposure Sliders** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/FocusBracketOverlay.kt:88-195`):
   - Vertical sliders use a 4.dp width track with 14.dp circular thumb and a Column touch box with no padding or touch slop compensation.
   - Drag amounts are quantized directly without spring position interpolation (`kotlin.math.round(newEv * 10) / 10f`).
   - Generic icons `Icons.Default.Brightness5` and `Icons.Default.Contrast` are used.
   - No magnetic zero-detent snap zone or haptic detent tick at 0.0 EV / 0.0 Shadow.
   - No auto-dismiss timer or spring enter/exit transitions.

5. **Pro Controls Sheet Transitions** (`composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt:186-190` & `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt:45-95`):
   - Triggered strictly by `cameraMode == CameraMode.PRO` wrapped in `AnimatedVisibility(enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())`.
   - Lacks interactive drag-to-collapse / drag-to-expand physics, drag handle notch, sliding tab indicator capsule, and slider step haptics.

6. **Tactile Haptics Engine** (`composeApp/src/commonMain/kotlin/com/auracam/ui/util/CameraSoundAndHaptics.kt:3-9` & `CameraSoundAndHaptics.android.kt:43-59`):
   - Interface provides only `vibrateSnap()` (30ms one-shot) and `vibrateLevelLock()` (15ms one-shot).
   - Missing `vibrateTick()`, `vibrateDetent()`, `vibrateModeChange()`, and `vibrateLock()`. Android implementation does not utilize modern `VibrationEffect.createPredefined(EFFECT_TICK)` or `EFFECT_CLICK`.

---

## 2. Logic Chain

1. **Premise**: Requirement R2 mandates authentic Google Pixel Material 3 Expressive motion dynamics — specifically Mode Carousel scroll snapping, Zoom Selector dial / slider / quick presets with spring transitions, generous Dual Exposure Sun EV and Moon Shadows sliders, and Pro Controls bottom sheet spring transitions.
2. **Analysis of Current State (Obs 1-6)**:
   - The current UI relies on standard `LazyRow`, static boxes, and `tween()` linear durations.
   - User interactions feel rigid and disconnected from physical device physics because gesture recognizers are minimal (tap-only on viewfinder) and spring curves (`spring(dampingRatio, stiffness)`) are not utilized.
   - Touch targets for exposure adjustments are too narrow (~20-30dp) for comfortable one-handed camera operation.
3. **Synthesis & Inferences**:
   - Replacing `LazyRow` with `HorizontalPager` or a centered-snapping `LazyRow` with `rememberSnapFlingBehavior` and `(screenWidth - itemWidth) / 2` content padding will achieve the authentic Pixel mode carousel snap and center-alignment.
   - Introducing a composite gesture detector on the viewfinder (`detectTransformGestures` for pinch-to-zoom combined with `detectTapGestures` for tap-focus, double-tap zoom, and long-press AE/AF lock) will satisfy standard mobile camera ergonomics.
   - Creating an expandable dual-state Zoom Selector (resting preset pills <-> continuous ruler dial on drag/pinch) with spring physics (`Animatable`, `Spring.DampingRatioMediumBouncy`) and micro-haptic ticks will match the Pixel 8/9 Pro zoom experience.
   - Refactoring `FocusBracketOverlay` into generous 48dp touch targets with Sun EV and Moon Shadows styling, magnetic 0.0 zero-detents, and 4-second spring auto-dismiss will resolve touch ergonomics and exposure tuning.
   - Refactoring Pro Controls into a draggable bottom sheet with spring expand/collapse physics (`spring(dampingRatio = 0.80f, stiffness = 380f)`) and sliding tab capsule will provide fluid manual control.
   - Enhancing `SoundAndHaptics` with `vibrateTick()`, `vibrateDetent()`, `vibrateModeChange()`, and `vibrateLock()` will provide the tactile foundation for all micro-interactions.

---

## 3. Caveats

- **Device Haptics Variability**: Physical haptic feedback quality depends on the connected Android device's hardware linear resonant actuator (LRA). API 29+ predefined vibration effects (`EFFECT_TICK`, `EFFECT_CLICK`) provide the highest fidelity, while fallback millisecond one-shots are maintained for older Android versions and Desktop/iOS targets.
- **Compose Multiplatform Gestures**: Pointer input on the live viewfinder must coexist with `CameraPreview` (AndroidView wrapping CameraX `PreviewView`). Pointer input must be placed on Compose overlay Box containers to ensure multi-touch gestures (pinch, drag, tap) are cleanly intercepted without blocking CameraX preview rendering.

---

## 4. Conclusion

The motion and gesture architecture of AuraCam KMP is currently at a foundational stage, ripe for the Material 3 Expressive overhaul. All required components for Requirement R2 are fully documented in `survey_motion_gestures.md` with:
1. Exact file paths, composable names, and line numbers.
2. Complete animation physics matrix specifying `dampingRatio` and `stiffness` for every interaction.
3. Concrete architectural patterns for:
   - Centered Mode Carousel with scroll snapping and haptics.
   - Viewfinder multi-touch fusion (pinch-to-zoom, tap-to-focus, double-tap, long-press AE/AF lock).
   - Dual-state Zoom Selector (pill presets <-> continuous ruler dial with tick haptics).
   - Dual Exposure Sun EV & Moon Shadows sliders with 48dp touch targets, 0.0 magnetic detents, and 4-second auto-dismiss.
   - Draggable Pro Controls bottom sheet with spring expand/collapse and sliding tab capsule.
   - Expanded multiplatform tactile haptics system.

---

## 5. Verification Method

To verify the survey findings and downstream implementations:

1. **Report Verification**:
   - Inspect `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_2/survey_motion_gestures.md`.
2. **Build and Unit Test Verification**:
   - Run `./gradlew :shared:desktopTest`
   - Run `./gradlew :composeApp:compileKotlinDesktop`
3. **Android Device Deployment & Interaction Protocol**:
   - Run `./gradlew :composeApp:installDebug`
   - Launch app: `adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity`
   - Verify Mode Carousel center snap & haptics.
   - Verify Pinch-to-zoom on viewfinder & zoom dial expansion.
   - Verify Tap-to-focus & Dual Exposure Sun/Moon sliders with zero-detent magnetic snap.
   - Verify Pro sheet spring expand/collapse and sliding tab capsule.
   - Capture device screenshot: `adb exec-out screencap -p > auracam_motion_test.png`
