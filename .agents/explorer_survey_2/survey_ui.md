# AuraCam UI Architecture Survey: Samsung Galaxy Director-Style Dual Recording

## 1. Executive Summary

This survey analyzes the Compose Multiplatform UI architecture of **AuraCam** and outlines the technical blueprint for implementing a **Samsung Galaxy Director-style Dual Recording (Vlog Mode)**. 

The feature introduces:
1. **Samsung-Style 50/50 Split View**: Viewport split evenly into two edge-to-edge halves (50% top stream, 50% bottom stream) with 0 wasted bezel space and a sleek frosted glass floating swap control.
2. **Clean Movable Picture-in-Picture (PiP) Mode**: Primary stream displayed fullscreen with a secondary proportional 16:9 / 4:3 floating window featuring magnetic corner snapping (Top-Left, Top-Right, Bottom-Left, Bottom-Right) and 1-tap stream swapping.
3. **Top Director Control Island**: A floating frosted glass capsule at the top of the viewport featuring a layout toggle `[ 🌓 Split 50/50 | 🔲 PiP ]`, stream swap `[ ⇄ Swap ]`, and a live tone filter picker `[ ✨ Filter ]` supporting Real Tone, Vibrant, Cinematic Warm, Monochrome, and Natural color grades applied synchronously across both feeds.
4. **Viewfinder Clutter Auto-Hiding**: Automatic suppression of non-essential viewfinder HUD elements (framing grids, dual-axis leveler, exposure zebra clipping, focus peaking) during Dual Recording to maintain a pristine, distraction-free director interface.

---

## 2. Current Viewfinder UI Architecture & Interaction Stack

### 2.1 Viewfinder Layout Hierarchy (`ViewfinderScreen.kt`)

The existing root camera screen is organized into a vertical stack bounded by safe drawing insets:

```
Box (Root: PixelPitchBlack, windowInsetsPadding(WindowInsets.safeDrawing))
└── Column (fillMaxSize)
    ├── FloatingTopBar (Pinned top safe header: Quick Settings Pill, Filter Wand, Settings Gear)
    ├── Box (Main Viewfinder Stage: weight=1f, padding horizontal 8.dp)
    │   └── BoxWithConstraints (Ratio-modified, clip RoundedCornerShape(28.dp), background PixelDarkBackground)
    │       ├── PointerInput: detectTransformGestures (Pinch-to-zoom 0.5x - 20x)
    │       ├── PointerInput: detectTapGestures (Tap-to-focus & EV anchor)
    │       │
    │       ├── Layer 1: CameraPreview(engine) [Primary Hardware Stream]
    │       ├── Layer 2: DualVlogOverlay(engine) [Active when cameraMode == DUAL_VLOG]
    │       ├── Layer 3: ExposureMaskLayer(engine) [Zebra & Peaking - skipped in DUAL_VLOG]
    │       ├── Layer 4: FramingGridOverlay(gridType) [Rule of 3rds, Golden Ratio - skipped in DUAL_VLOG]
    │       ├── Layer 5: LevelerLayer(engine) [Dual-axis 3D leveler - skipped in DUAL_VLOG]
    │       ├── Layer 6: FocusBracketOverlay [Focus reticle + EV drag slider]
    │       ├── Layer 7: CountdownOverlay [Timer countdown overlay]
    │       ├── Layer 8: ComputationalCaptureBanner [Capture progress bar]
    │       └── Layer 9: FrontScreenFlashOverlay [Warm ring light / screen flash]
    │
    │   ├── VideoRecordingHUD / SlowMotionRecordingHud / TimelapseRecordingHud (if isRecording)
    │   └── Mode Notice & Microphone Warning Badges
    │
    ├── ZoomSelectorLayer (Compact Preset Pills + Expandable Rotary Ticker Dial)
    └── Column (Pinned Bottom Controls Area)
        ├── SlowMotionSpeedSelector (when SLOW_MOTION)
        ├── TimelapseIntervalSelector (when TIME_LAPSE)
        ├── ProControlsLayer (when PRO)
        ├── ModeCarousel (Snapping LazyRow with 11 camera modes)
        └── ShutterControlRow (Gallery Thumbnail, ExpressiveShutterButton, Flip Camera)
```

### 2.2 Dual Stream Hardware Binding (`CameraPreview.android.kt` & `AndroidCameraEngine.kt`)

In Android:
- `CameraPreview` embeds a primary `PreviewView` connected to `ProcessCameraProvider.bindToLifecycle()`.
- `SecondaryCameraPreview` embeds a secondary `PreviewView` connected to `engine.bindSecondaryPreview(secondaryPreviewView)`.
- When `_cameraMode.value == CameraMode.DUAL_VLOG`, `AndroidCameraEngine.kt` uses CameraX `ConcurrentCamera` (`provider.availableConcurrentCameraInfos`) to bind both primary and secondary camera feeds simultaneously.

### 2.3 Existing `DualVlogLayer.kt` Assessment & Limitations

The current `DualVlogLayer.kt` has several architectural limitations:
1. **PiP Snapping Hardcoding**: Corner snap coordinates use hardcoded pixel assumptions (`360f`, `650f`, `280f`) rather than responsive density-aware container constraints, causing layout distortion on different screen resolutions and aspect ratios.
2. **Aspect Ratio of PiP Window**: Dimensions are fixed to `114.dp x 152.dp` (3:4 ratio) instead of modern standard 16:9 / 4:3 video proportions.
3. **50/50 Split Feed Layout**: In the current split view, the primary feed is rendered fullscreen behind the entire Box and the bottom half is covered by `SecondaryCameraPreview`. The top feed is not isolated or centered, leading to off-center framing.
4. **Director Controls**: Currently separated into multiple disconnected pills without a unified Director Island and lacking an integrated live tone filter picker.

---

## 3. UI Design & Architecture: Samsung-Style 50/50 Split View

### 3.1 Edge-to-Edge Geometry & Zero Bezel Waste

In Samsung Galaxy Director's View, the viewport is split into two equal 50% vertical halves:
- **Top Half (50%)**: Displays Stream A (e.g. Rear Wide or Front Selfie).
- **Bottom Half (50%)**: Displays Stream B (e.g. Front Selfie or Rear Wide).
- **Dividing Seam**: A clean 1dp frosted hairline divider (`Color(0x40FFFFFF)`) positioned at the exact 50% vertical midpoint.
- **Zero Wasted Space**: Both streams use `Modifier.weight(1f).fillMaxWidth()` and `clipToBounds()`. The underlying `PreviewView` runs in `ScaleType.FILL_CENTER` so both feeds seamlessly fill their respective halves without black bars or letterboxing.

### 3.2 Floating Center Swap Control

Positioned directly at the center of the dividing seam:
- **Visual Design**: Sleek frosted glass capsule (`Modifier.pixelGlass(shape = CircleShape, backgroundColor = PixelGlassScrimHeavy, borderColor = PixelGlassBorder)`).
- **Interaction**:
  - Tapping the swap button triggers an animated 180° rotation of the swap icon (`Icons.Default.Cached` or `Icons.Default.SwapVert`).
  - Triggers instant haptic feedback (`soundAndHaptics.vibrateSnap()`).
  - Swaps the assigned streams between top and bottom viewports instantaneously (`isSwapped = !isSwapped`).

```
+------------------------------------------+
|                                          |
|            TOP STREAM (50%)              |
|        [Rear Main Camera Feed]           |
|                                          |
+-------------------[ ⇄ ]------------------+  <-- 1dp Divider & Center Swap Pill
|                                          |
|           BOTTOM STREAM (50%)            |
|       [Front Selfie Camera Feed]         |
|                                          |
+------------------------------------------+
```

---

## 4. UI Design & Architecture: Movable Picture-in-Picture (PiP) Mode

### 4.1 Layout Structure

- **Backdrop (Fullscreen)**: Primary camera stream fills the entire viewfinder stage (`CameraPreview`).
- **Floating PiP Window**: Secondary camera stream (`SecondaryCameraPreview`) rendered inside a floating rounded rectangle container.
- **Proportions**:
  - Standard 16:9 vertical portrait ratio: `width = 118.dp`, `height = 188.dp` (or dynamic width = 28% of viewfinder width).
  - Shape: `RoundedCornerShape(20.dp)`.
  - Border: `1.5.dp` frosted glass stroke (`Color(0x66FFFFFF)`).
  - Shadow: `shadow(elevation = 14.dp, shape = RoundedCornerShape(20.dp))`.

### 4.2 Magnetic Corner Snapping Algorithm

The floating PiP window supports freeform dragging with smooth magnetic snapping to four discrete corners:
1. **Top-Left (TL)**
2. **Top-Right (TR)**
3. **Bottom-Left (BL)**
4. **Bottom-Right (BR)**

#### Safe Inset Boundary Constraints:
- `marginHorizontal = 12.dp`
- `marginTop = 56.dp` (clears the Top Director Control Island)
- `marginBottom = 72.dp` (clears bottom zoom / shutter controls)

#### Math & Physics Model:
```kotlin
enum class PipCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

fun calculateCornerOffset(corner: PipCorner, containerWidth: Dp, containerHeight: Dp, pipWidth: Dp, pipHeight: Dp): Offset {
    val marginX = 12.dp.toPx()
    val marginTop = 56.dp.toPx()
    val marginBottom = 72.dp.toPx()
    val rightX = (containerWidth - pipWidth).toPx() - marginX
    val bottomY = (containerHeight - pipHeight).toPx() - marginBottom
    
    return when (corner) {
        PipCorner.TOP_LEFT -> Offset(marginX, marginTop)
        PipCorner.TOP_RIGHT -> Offset(rightX, marginTop)
        PipCorner.BOTTOM_LEFT -> Offset(marginX, bottomY)
        PipCorner.BOTTOM_RIGHT -> Offset(rightX, bottomY)
    }
}
```

- When the user drags the PiP window (`detectDragGestures`), the position tracks the touch delta in real time bounded by `coerceIn()`.
- On release (`onDragEnd`), the Euclidean distance to all 4 corner coordinates is evaluated, selecting the closest quadrant.
- The window animates to the selected target using spring physics:
  `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.

### 4.3 1-Tap Stream Swap Gesture Disambiguation

- Tapping anywhere on the floating PiP window triggers a stream swap.
- To prevent tap gestures from conflicting with drag gestures, a drag threshold (e.g. > 6dp movement) distinguishes intentional drags from quick taps.
- A discreet swap icon badge is placed in the corner of the PiP window for clear affordance.

---

## 5. Top Director Control Island & Live Tone Filters

### 5.1 Frosted Glass Director Capsule Design

Positioned at `Alignment.TopCenter` with `padding(top = 8.dp)`:
- High-blur translucent container (`pixelGlass` modifier with `PixelGlassScrimHeavy` + `PixelGlassBorder`).
- Capsule shape (`RoundedCornerShape(50)`).
- Three integrated control segments:

```
+-------------------------------------------------------------------------+
|  [ 🌓 Split | 🔲 PiP ]   ·   [ ⇄ Swap ]   ·   [ ✨ Real Tone ▾ ]         |
+-------------------------------------------------------------------------+
       Layout Toggle              Stream Swap        Tone Filter Picker
```

### 5.2 Control Segments Specification

1. **Layout Toggle `[ 🌓 Split | 🔲 PiP ]`**:
   - Compact segmented toggle.
   - The active layout option is highlighted with `PixelYellowAccent` and bold typography.
   - Smooth animated transition between 50/50 Split and PiP layouts.

2. **Stream Swap `[ ⇄ Swap ]`**:
   - Single tap interchanges primary (Rear) and secondary (Front) feeds across both Split and PiP modes.
   - Animated rotation on tap with haptic click.

3. **Live Tone Filter Picker `[ ✨ Filter ]`**:
   - Displays a magic wand icon `Icons.Default.AutoFixHigh` and the current filter label (e.g. "Real Tone").
   - Tapping toggles an inline dropdown capsule directly below the Director Island.
   - Features quick-select chips for 5 flagship tone profiles:
     - **Real Tone**: Google Real Tone skin tone accuracy
     - **Vibrant**: Punchy saturated colors
     - **Cinematic Warm**: Golden hour film grade
     - **Monochrome**: Deep high-contrast B&W
     - **Natural**: True-to-life neutral tones
   - Selection updates `engine.setColorProfile(profile)`, applying color grading synchronously across both camera feeds.

---

## 6. Viewfinder Clutter Auto-Hiding System

During Dual Recording, visual distractions must be eliminated so the director can focus purely on framing both subjects.

### 6.1 Viewfinder HUD Element Visibility Matrix

| Viewfinder Element | Normal Photo / Video Mode | Dual Recording (Director Mode) | Rationale |
| :--- | :--- | :--- | :--- |
| **Top Director Control Island** | Hidden | **VISIBLE (Top Center)** | Primary control hub for dual recording |
| **Framing Grids (3x3, Golden, Square)** | Visible (if enabled) | **HIDDEN** | Grids interfere with split dividing line & PiP window |
| **Dual-Axis 3D Horizon Leveler** | Visible (if enabled) | **HIDDEN** | Leveler crosshair clutters split seam |
| **Exposure Zebra Clipping Mask** | Visible (in Pro/Video) | **HIDDEN** | Zebra stripes obscure secondary subject |
| **Focus Peaking Green Outlines** | Visible (in Pro) | **HIDDEN** | Peaking distracts from dual framing |
| **Standard FloatingTopBar Actions** | Visible | **MINIMIZED / INTEGRATED** | Settings & flash accessible without duplicate clutter |
| **Focus Bracket & EV Slider** | Visible on tap | **MINIMAL AUTO-FADE (1.5s)** | Fades rapidly after focus lock |
| **Video Recording HUD (Timer / 4K)** | Visible when recording | **VISIBLE (Top Center Pill)** | Essential recording duration & status |
| **Mode Carousel & Shutter Row** | Visible | **VISIBLE** | Standard shutter button & mode switching |

### 6.2 Implementation Strategy in `ViewfinderScreen.kt`

```kotlin
// Multi-Stream Dual Vlog / Director's View Overlay
if (cameraMode == CameraMode.DUAL_VLOG) {
    DirectorDualRecordingOverlay(
        engine = engine,
        isRecording = isRecording,
        colorProfile = colorProfile,
        onColorProfileSelected = { engine.setColorProfile(it) },
        modifier = Modifier.fillMaxSize()
    )
}

// Clutter suppression: Only render grids, leveler, and zebra when NOT in Dual Vlog mode
if (cameraMode != CameraMode.DUAL_VLOG) {
    ExposureMaskLayer(engine = engine, modifier = Modifier.fillMaxSize())
    FramingGridOverlay(gridType = if (settings.framingHintsEnabled) gridType else GridType.NONE)
    if (settings.framingHintsEnabled) {
        LevelerLayer(engine = engine, onLevelReached = soundAndHaptics::vibrateLevelLock)
    }
}
```

---

## 7. Architectural Code Blueprint

### 7.1 Data Models & State (`DualVlogUiState.kt`)

```kotlin
package com.auracam.camera.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DirectorLayout(val label: String) {
    SPLIT_50_50("Split 50/50"),
    PIP_WINDOW("PiP Window")
}

enum class PipCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

data class DirectorUiState(
    val layout: DirectorLayout = DirectorLayout.SPLIT_50_50,
    val isSwapped: Boolean = false,
    val pipCorner: PipCorner = PipCorner.TOP_RIGHT,
    val isFilterPickerExpanded: Boolean = false
)
```

### 7.2 Core Composable Component Breakdown

1. **`DirectorDualRecordingOverlay.kt`**: Main container managing layout switching (50/50 vs PiP), gesture events, and director state.
2. **`DirectorControlIsland.kt`**: Top floating capsule with layout toggle, stream swap, and filter button.
3. **`DirectorSplit5050View.kt`**: Zero-bezel 50/50 split view with animated center floating swap pill.
4. **`DirectorMovablePipView.kt`**: Magnetic corner-snapping 16:9 floating window with tap-to-swap support.
5. **`DirectorFilterPicker.kt`**: Inline quick-select tone filter drawer with color gradient previews.

---

## 8. Verification & Physical Device Validation Strategy

1. **Multiplatform Unit Test Suite**:
   - Run `./gradlew testDebugUnitTest` to verify state transitions, layout enum switching, and filter state immutability.
2. **Physical Device Deployment**:
   - Install to target connected Android device (Nothing Phone 2a):
     `./gradlew :composeApp:installDebug`
   - Launch `MainActivity` via ADB:
     `/Users/azhar/Library/Android/sdk/platform-tools/adb shell am start -n com.auracam.app/.MainActivity`
3. **Visual UI Screen Capture Verification**:
   - Capture screenshot of 50/50 Split View and PiP mode in all 4 corners:
     `/Users/azhar/Library/Android/sdk/platform-tools/adb exec-out screencap -p > auracam_director_split.png`
     `/Users/azhar/Library/Android/sdk/platform-tools/adb exec-out screencap -p > auracam_director_pip.png`
   - Verify zero bezel gap in split mode, smooth spring snapping in PiP mode, and synchronous filter grading across both camera streams.

---

## 9. Conclusion & Next Steps

The UI architecture survey confirms that AuraCam's existing Compose Multiplatform foundation provides the ideal primitives for a flagship Samsung Galaxy Director-style Dual Recording experience. By refactoring `DualVlogLayer.kt` into dedicated, modular composables (`DirectorDualRecordingOverlay`, `DirectorControlIsland`, `DirectorSplit5050View`, `DirectorMovablePipView`), AuraCam will deliver a fluid, high-performance dual recording interface.
