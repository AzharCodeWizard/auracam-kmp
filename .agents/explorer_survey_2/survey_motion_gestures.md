# Requirement R2 Survey Report: Fluid Spring Motion & Tactile Micro-Interactions

## Executive Summary
This report presents an exhaustive technical survey of the motion, gesture recognition, animation mechanics, and tactile haptic systems across the **AuraCam KMP** codebase. The investigation focuses specifically on **Requirement R2: Fluid Spring Motion & Tactile Micro-Interactions** for the Google Pixel Material 3 Expressive overhaul.

Across the current codebase, UI transitions and gesture handlers rely predominantly on static list containers, basic `tween()` animations, narrow touch targets, and rudimentary single-tap gesture detectors. Implementing authentic Pixel 9 Pro / M3 Expressive fluidity requires replacing linear animations with physically modeled spring curves (`Animatable`, `spring()`), implementing multi-touch gesture fusion (pinch-to-zoom, tap-to-focus, double-tap zoom/flip, long-press AE/AF lock), upgrading the Mode Carousel with centered scroll-snapping, introducing an expandable continuous Zoom Dial with tick haptics, engineering generous Dual Exposure Sun/Moon sliders with zero-detents, and elevating the Pro Controls sheet with draggable spring physics.

---

## 1. Inventory of Current Motion & Gesture Files

| Component / Subsystem | File Path | Key Composable / Class | Current Implementation |
|---|---|---|---|
| **Viewfinder Screen** | `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` | `ViewfinderScreen` | Single `detectTapGestures` on viewfinder box (lines 87-94). `AnimatedVisibility` with default `expandVertically()` for Pro Sheet (lines 186-190). |
| **Camera Mode Carousel** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt` | `ModeCarousel` | `LazyRow` with `rememberLazyListState()`. `tween(200)` for scale, text color, and background color. No scroll snapping or auto-centering. |
| **Zoom Controls** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ZoomSelector.kt` | `ZoomSelector` | 4 static circular buttons (`.5`, `1x`, `2`, `5`). Transitions use `tween(150)`. No continuous slider, no drag expansion, no dial. |
| **Focus & Dual Exposure HUD** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/FocusBracketOverlay.kt` | `FocusBracketOverlay` | Infinite pulsing alpha bracket. 2 vertical sliders next to focus point with 4dp width tracks and small touch bounds. Raw linear drag amounts. |
| **Pro Controls Sheet** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` | `ProControlsSheet`, `IsoControl`, `ShutterControl`, etc. | LazyRow tab bar with static item switching. Standard M3 `Slider` and `AssistChip` without custom spring physics or drag gestures. |
| **Shutter & Flip Row** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ShutterRow.kt` | `ShutterRow` | Circular shutter button with scale animation on capture, circular progress indicator for computational capture. |
| **Viewfinder Overlays & Leveler** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ViewfinderOverlay.kt` | `ViewfinderOverlay` | Canvas grid rendering, horizon line canvas rotation based on gyro roll degrees, pulsing record indicator. |
| **Tactile & Sound Engine** | `composeApp/src/commonMain/kotlin/com/auracam/ui/util/CameraSoundAndHaptics.kt`<br>`composeApp/src/androidMain/.../CameraSoundAndHaptics.android.kt` | `SoundAndHaptics`, `PlatformSoundAndHaptics` | Basic `vibrateSnap()` (30ms one-shot) and `vibrateLevelLock()` (15ms one-shot). No tick, detent, or mode-change haptics. |

---

## 2. In-Depth Component Analysis & Deficiencies

### 2.1 Camera Mode Carousel & Mode Switching
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt`

```kotlin
// Current implementation snippet:
LazyRow(
    state = listState,
    modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically
) {
    items(modes) { mode ->
        val isSelected = mode == currentMode
        val scale = animateFloatAsState(
            targetValue = if (isSelected) 1.05f else 0.95f,
            animationSpec = tween(durationMillis = 200)
        )
        // ...
    }
}
```

#### Deficiencies Identified:
1. **Lack of Scroll Snapping**: A standard `LazyRow` allows free-wheeling scrolling that stops arbitrarily between modes rather than snapping cleanly to the centered active mode.
2. **Missing Center Auto-Scroll**: Tapping a non-centered mode changes the state but does not smoothly animate the `LazyRow` or `Pager` to position the selected mode at the exact horizontal center of the screen.
3. **Linear / Tween Easing**: Uses `tween(durationMillis = 200)` instead of bouncy M3 Expressive spring physics (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`).
4. **No Scroll Haptics**: No feedback is triggered as the user swipes through items and crosses mode boundaries.
5. **Static Background Box**: The active mode pill is rendered with a simple tinted Box per item rather than a unified sliding spring capsule or authentic Pixel pill geometry.

---

### 2.2 Viewfinder Multi-Touch Gestures & Recognizers
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` (lines 87-94)

```kotlin
// Current implementation snippet:
BoxWithConstraints(
    modifier = ratioModifier
        .fillMaxSize()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val normX = offset.x / size.width
                val normY = offset.y / size.height
                engine.setFocusPoint(normX, normY)
                soundAndHaptics.vibrateSnap()
            }
        }
)
```

#### Deficiencies Identified:
1. **No Pinch-to-Zoom**: Viewfinder completely lacks multi-touch gesture recognition (`detectTransformGestures` or custom pointer input). Users cannot pinch on the preview stream to zoom in/out.
2. **No Double-Tap Quick Switch**: Double-tapping the viewfinder is not recognized to toggle between primary lenses (e.g. 1x <-> 2x) or flip camera (front/back).
3. **No Long-Press AE/AF Lock**: Long pressing on a focus point does not lock auto-exposure and auto-focus with an "AE/AF LOCK" pill badge.
4. **No Viewfinder Mode Swipe**: Swiping horizontally across the viewfinder frame does not cycle modes.

---

### 2.3 Zoom Controls: Selector Dial, Slider, Pills & Quick Presets
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ZoomSelector.kt`

```kotlin
// Current implementation snippet:
val zoomPresets = remember { listOf(0.5f, 1.0f, 2.0f, 5.0f) }
// Renders static circle boxes for each preset with tween(150) color transitions.
```

#### Deficiencies Identified:
1. **Static 4-Preset Limitation**: Only `.5`, `1x`, `2`, and `5` are selectable. Intermediate continuous zoom levels (e.g. 1.4x, 3.2x, 8.0x up to 20.0x) cannot be dialed in directly from the UI.
2. **No Expandable Continuous Zoom Dial / Slider**: In the Google Pixel Camera experience, dragging horizontally on the zoom pill or pinching on the viewfinder expands a continuous curved/linear zoom dial featuring graduated tick marks and a real-time zoom multiplier readout.
3. **No Dynamic Spring Morphing**: The pill container does not expand and contract using spring physics (`Animatable` for container width, tick opacity, and pill scale).
4. **No Scrubbing Micro-Haptics**: When continuous zoom is dragged, there is no tactile feedback when passing optical camera transition thresholds (0.5x Ultra-Wide, 1.0x Wide, 2.0x Crop-Sensor Tele, 5.0x Periscope Tele).

---

### 2.4 Dual Exposure Sliders (Sun EV & Moon Shadows Sliders)
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/FocusBracketOverlay.kt`

```kotlin
// Current implementation snippet:
Row(
    modifier = Modifier
        .offset(
            x = (focusPxX + 44.dp).coerceIn(0.dp, maxWidth - 88.dp),
            y = (focusPxY - 60.dp).coerceIn(0.dp, maxHeight - 140.dp)
        )
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xCC181818))
        .padding(horizontal = 8.dp, vertical = 10.dp),
    // ...
)
```

#### Deficiencies Identified:
1. **Narrow Touch Targets**: Sliders have a track width of only 4.dp and Column width of ~20dp, making touch interaction on physical devices prone to missed gestures and accidental viewfinder re-taps.
2. **Generic Iconography**: Employs generic `Icons.Default.Brightness5` and `Icons.Default.Contrast` instead of custom Pixel-styled Sun (Warm Amber/Yellow EV) and Moon (Cool Silver/White Shadows) icons.
3. **No Zero-Detent Center Snap**: When dragging EV (-3.0 to +3.0) or Shadows (-1.0 to +1.0), there is no magnetic zero-notch / detent at 0.0 with haptic tick feedback, making returning to neutral exposure cumbersome.
4. **Direct Raw Quantization Without Spring Damping**: Drag offsets immediately quantize values (`round(newEv * 10) / 10f`) without smooth thumb position interpolation.
5. **No Auto-Dismiss Timer or Spring Exit**: The overlay remains static until the next tap rather than gracefully auto-dismissing with a spring shrink-fade after 4 seconds of inactivity.
6. **Edge Clipping Vulnerability**: Position calculation `(focusPxX + 44.dp)` near the right screen edge can crowd the border or overlap with HUD elements without an adaptive layout strategy.

---

### 2.5 Pro Controls Bottom Sheet Spring Transitions & Expand/Collapse Physics
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` and `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` (lines 186-190)

```kotlin
// Current implementation snippet:
AnimatedVisibility(
    visible = cameraMode == CameraMode.PRO,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
) {
    ProControlsSheet(...)
}
```

#### Deficiencies Identified:
1. **Rigid Binary Visibility**: Toggling Pro mode uses standard `expandVertically() + fadeIn()` without velocity-based gestures or interactive drag-to-collapse / drag-to-expand.
2. **Missing Drag Handle / Pill Notch**: No visual or touch affordance for dragging the sheet down to peek or expand to full height.
3. **Tab Bar Jump**: Switching tabs (ISO, Shutter, Focus, EV, WB, Histogram) instantly swaps content without a smooth sliding spring pill indicator under/behind the active tab.
4. **Standard Sliders**: Sliders inside Pro Controls use default Material 3 `Slider` composables without spring thumb scale on press or haptic ticks on value changes.

---

### 2.6 Tactile Haptics & Sound Mechanics
**Current Source Code**: `composeApp/src/commonMain/kotlin/com/auracam/ui/util/CameraSoundAndHaptics.kt` & `composeApp/src/androidMain/.../CameraSoundAndHaptics.android.kt`

```kotlin
// Current interface:
interface SoundAndHaptics {
    fun playShutterSound()
    fun playVideoStartSound()
    fun playVideoStopSound()
    fun vibrateSnap()
    fun vibrateLevelLock()
}
```

#### Deficiencies Identified:
1. **Limited Haptic Vocabulary**: Only `vibrateSnap()` (30ms pulse) and `vibrateLevelLock()` (15ms pulse) exist.
2. **Missing Expressive Micro-Haptics**:
   - `vibrateTick()`: 5-8ms ultra-light tactile tick for zoom dial scrub and slider parameter increments.
   - `vibrateDetent()`: Distinctive crisp click when passing 0.0 EV, 0.0 Shadow, or 1.0x zoom threshold.
   - `vibrateModeChange()`: Medium crisp pulse when snapping between camera modes.
   - `vibrateLock()`: Double-pulse confirmation for AE/AF lock.
3. **Platform Utilization**: On Android 10+ (API 29+), `VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)` and `VibrationEffect.EFFECT_CLICK` provide crisp, low-latency haptic feedback superior to legacy millisecond one-shots.

---

## 3. Compose Multiplatform Animation Mechanics & Physics Matrix

To achieve authentic Google Pixel M3 Expressive motion dynamics, all UI transitions should be driven by spring physics with tailored damping ratios and stiffness constants:

| Interaction / Transition | Compose Animation Spec | Damping Ratio (`dampingRatio`) | Stiffness (`stiffness`) | Target Experience |
|---|---|---|---|---|
| **Mode Carousel Snapping** | `spring(dampingRatio = 0.75f, stiffness = 400f)` | `Spring.DampingRatioLowBouncy` (0.75) | Medium-Low (400f) | Fluid, tactile mode deceleration into center alignment. |
| **Mode Pill Highlight Indicator** | `spring(dampingRatio = 0.65f, stiffness = 600f)` | Medium-Low Bouncy (0.65) | Medium (600f) | Snappy pill shape & position morph behind selected text. |
| **Zoom Preset Pill Pop** | `spring(dampingRatio = 0.55f, stiffness = 850f)` | `Spring.DampingRatioMediumBouncy` (0.55) | Medium (850f) | Tactile bounce when tapping `.5`, `1x`, `2x`, `5x`. |
| **Zoom Dial Expand / Collapse** | `spring(dampingRatio = 0.78f, stiffness = 350f)` | Low Bouncy (0.78) | Medium-Low (350f) | Smooth slide & fade transition between presets and dial. |
| **Focus Bracket Reticle Pop** | `spring(dampingRatio = 0.50f, stiffness = 1200f)` | Medium Bouncy (0.50) | High (1200f) | Quick, responsive bracket lock-on animation at tap point. |
| **Dual Exposure Sliders Enter/Exit** | `spring(dampingRatio = 0.70f, stiffness = 500f)` | Low-Medium Bouncy (0.70) | Medium-Low (500f) | Elegant scale-in (0.8 -> 1.0) and fade-out on auto-dismiss. |
| **Dual Exposure Zero-Detent Snap** | `spring(dampingRatio = 0.85f, stiffness = 1600f)` | No Bouncy / High Damped (0.85) | High (1600f) | Magnetic detent snap into 0.0 EV / 0.0 Shadow. |
| **Pro Sheet Expand / Collapse** | `spring(dampingRatio = 0.80f, stiffness = 380f)` | Low Bouncy (0.80) | Low-Medium (380f) | Natural drawer slide physics matching Android 15 gestures. |
| **Pro Tab Indicator Slide** | `spring(dampingRatio = 0.70f, stiffness = 700f)` | Low Bouncy (0.70) | Medium (700f) | Pill capsule glides smoothly between manual tabs. |
| **Shutter Button Press** | `spring(dampingRatio = 0.45f, stiffness = 1500f)` | Medium Bouncy (0.45) | High (1500f) | Instant tactile depression (scale 0.88f) and release pop. |

---

## 4. Concrete Architectural Recommendations for Requirement R2

### Recommendation 1: Mode Carousel with Center Scroll Snapping & Haptics
1. **Adopt `HorizontalPager` or Centered Snapping `LazyRow`**:
   - Use `rememberPagerState` with `HorizontalPager` or `LazyRow` with `rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyListState))`.
   - Add horizontal content padding equal to `(screenWidth - itemWidth) / 2` so that the selected mode is mathematically positioned at the exact screen center.
   - When the user taps a mode, invoke `coroutineScope.launch { pagerState.animateScrollToPage(page, animationSpec = spring(...)) }`.
   - Monitor `pagerState.currentPage` via `LaunchedEffect(pagerState.currentPage)`: when the page index changes during drag/scroll, trigger `soundAndHaptics.vibrateModeChange()` and call `engine.setMode(modes[page])`.
2. **Unified Pill Indicator**:
   - Render a sliding background pill with frosted glass or warm yellow accent behind the active mode item, interpolating its bounds using `spring()`.

---

### Recommendation 2: Viewfinder Multi-Touch Gesture Recognizer Fusion
1. **Combine Gestures via Custom Pointer Input Handler**:
   - Implement a composite pointer input modifier on the Viewfinder box handling:
     * **Pinch-to-Zoom**: Track multi-touch pointer distance delta, multiplying the current zoom ratio by the scale factor: `val newZoom = (currentZoom * zoomChange).coerceIn(0.5f, 20.0f)`. Forward `newZoom` to `engine.setZoom(newZoom)` and dynamically switch the Zoom Selector to expanded dial mode.
     * **Tap-to-Focus**: On single tap, record normalized `(normX, normY)` coordinates, invoke `engine.setFocusPoint(normX, normY)`, play `soundAndHaptics.vibrateSnap()`, reset the 4-second auto-dismiss exposure slider timer, and trigger the reticle pop animation.
     * **Double-Tap**: Rapid switch between 1.0x and 2.0x zoom (or toggle Front/Back lens).
     * **Long-Press AE/AF Lock**: When a pointer is held down for >500ms at the focus point, lock exposure/focus, display an "AE/AF LOCK" pill badge with a padlock icon, and trigger `soundAndHaptics.vibrateLock()`.

---

### Recommendation 3: Expandable Zoom Selector (Presets Pill + Continuous Ruler Dial)
1. **Dual-State Component Structure**:
   - **State 1: Quick Preset Pill (Resting)**:
     * Capsule pill containing `.5`, `1x`, `2`, `5`, `10` preset chips.
     * Tapping a preset springs the active indicator to that chip with `Spring.DampingRatioMediumBouncy` and triggers `soundAndHaptics.vibrateSnap()`.
   - **State 2: Continuous Ruler Dial (Active Drag / Pinch)**:
     * Activated by horizontal drag gesture on the zoom pill or pinch-to-zoom on the viewfinder.
     * Expands into a horizontal scrollable dial / ruler featuring:
       - Minor tick marks every 0.1x.
       - Major tick marks and labels at optical bases (0.5x, 1x, 2x, 5x, 10x, 20x).
       - Floating magnification readout bubble (e.g. `2.4x`) centered above the dial with high-contrast warm yellow typography.
       - Tactile tick haptic (`soundAndHaptics.vibrateTick()`) whenever the zoom value crosses a major tick or integer.
   - **Auto-Collapse Mechanism**:
     * Launches a 2-second auto-collapse timer upon drag release, gracefully springing back into the compact preset pill state.

---

### Recommendation 4: Dual Exposure Sliders with Ergonomic Touch Targets & Detents
1. **Ergonomic HUD Architecture**:
   - Create a dedicated `DualExposureSliders` composable within `FocusBracketOverlay.kt`.
   - Expand touch bounds to minimum 48dp width x 130dp height per slider column with generous touch slop.
   - Place the slider dock intelligently relative to the focus point: if the focus point is in the right 30% of the screen, place the sliders to the left of the bracket; otherwise place them to the right.
2. **Authentic Sun & Moon Iconography & Detents**:
   - **Sun EV Slider**:
     * Warm Golden Yellow styling (`PixelYellowAccent`).
     * Range: -3.0 EV to +3.0 EV.
     * Zero-detent snap zone: within `[-0.15 EV, +0.15 EV]`, magnetically snap value to `0.0 EV` and trigger `soundAndHaptics.vibrateDetent()`.
   - **Moon Shadows Slider**:
     * Cool Silver / Ice White styling.
     * Range: -1.0 to +1.0 shadow tone bias.
     * Zero-detent snap zone at `0.0` with `vibrateDetent()`.
3. **Auto-Dismiss & Lock Physics**:
   - When a focus point is set, start a 4-second countdown coroutine. If no drag occurs on either slider, fade/shrink out the bracket and sliders with `spring()`.
   - If AE/AF is locked, keep the bracket visible with the yellow "AE/AF LOCK" pill until the user taps to dismiss.

---

### Recommendation 5: Draggable Pro Controls Bottom Sheet with Spring Physics
1. **Interactive Sheet Dragging & Snap Anchors**:
   - Implement bottom sheet with drag handle pill, supporting two states: **Collapsed / Peek** (showing only parameter summary pill) and **Expanded** (showing full manual tab controls + live histogram).
   - Use `Animatable` or `detectVerticalDragGestures` with velocity calculation to spring between states on release: `sheetOffset.animateTo(targetOffset, spring(dampingRatio = 0.80f, stiffness = 380f))`.
2. **Animated Tab Indicator**:
   - In `ProControlsSheet`, use `animateDpAsState` or an offset `Animatable` with spring spec to glide the active yellow indicator pill smoothly between ISO, Shutter, Focus, EV, WB, and Histogram tabs.
3. **Micro-Haptic Parameter Scrubbing**:
   - In `IsoControl`, `ShutterControl`, `FocusControl`, `WbControl`, trigger `soundAndHaptics.vibrateTick()` on each discrete step change as the user drags the slider.

---

### Recommendation 6: Tactile Haptics Engine Enhancement
1. **Update `CameraSoundAndHaptics.kt` Interface**:
```kotlin
interface SoundAndHaptics {
    fun playShutterSound()
    fun playVideoStartSound()
    fun playVideoStopSound()
    fun vibrateSnap()
    fun vibrateTick()
    fun vibrateDetent()
    fun vibrateModeChange()
    fun vibrateLock()
    fun vibrateLevelLock()
}
```
2. **Implement Platform-Specific Haptics**:
   - **Android (`CameraSoundAndHaptics.android.kt`)**:
     * On API 29+: Use `VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)` for `vibrateTick()`, `EFFECT_CLICK` for `vibrateSnap()`, `EFFECT_HEAVY_CLICK` for `vibrateLock()`.
     * Fallback for API 26-28: Short millisecond one-shots with appropriate amplitude (`createOneShot(8, 60)` for tick, `createOneShot(20, 150)` for detent, `createOneShot(35, 255)` for lock).
   - **iOS (`CameraSoundAndHaptics.ios.kt`)**:
     * Use `UIImpactFeedbackGenerator(style = .light)`, `.medium`, `.heavy`, and `UISelectionFeedbackGenerator`.
   - **Desktop (`CameraSoundAndHaptics.desktop.kt`)**:
     * Graceful no-op with optional audio tick simulation.

---

## 5. Verification Plan for R2 Implementations

To independently verify the implementation of R2 once changes are applied:

1. **Automated Unit & Multiplatform Tests**:
   - Run `./gradlew :shared:desktopTest` to ensure domain models, zoom clamping, and pro settings formatting remain fully passing.
   - Run `./gradlew :composeApp:compileKotlinDesktop` to verify Compose Multiplatform animations compile across all desktop/shared targets.
2. **Android Build & Physical Device Deployment**:
   - Compile and package Android debug APK: `./gradlew :composeApp:assembleDebug`.
   - Install to connected Android device: `./gradlew :composeApp:installDebug`.
   - Launch MainActivity: `adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity`.
3. **Interactive Device Verification Protocol**:
   - **Mode Carousel**: Swipe horizontally along the bottom mode bar; verify scroll snapping centers each mode with bouncy spring deceleration and haptic pulses.
   - **Pinch-to-Zoom**: Perform two-finger pinch on the live camera viewfinder; verify smooth zoom scaling from 0.5x to 20.0x and expansion into continuous zoom dial.
   - **Tap-to-Focus & Dual Exposure**: Tap arbitrary screen regions; verify spring bracket pop, immediate appearance of Sun EV and Moon Shadows sliders with generous touch targets, magnetic zero-detent snap at 0.0 with haptics, and 4-second auto-dismiss.
   - **Long-Press AE/AF Lock**: Hold finger on viewfinder for >500ms; verify "AE/AF LOCK" pill appearance and haptic confirmation.
   - **Pro Controls Bottom Sheet**: Switch to Pro mode; verify fluid spring bottom sheet entrance, drag-to-collapse gesture, sliding tab indicator capsule, and scrub ticks on manual sliders.
   - **Visual Artifact Verification**: Capture screenshots via `adb exec-out screencap -p > auracam_motion_test.png` to inspect layout alignment and visual polish.
