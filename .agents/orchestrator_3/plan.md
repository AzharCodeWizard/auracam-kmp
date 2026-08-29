# Orchestration Plan — AuraCam KMP Pixel M3 Expressive Overhaul

## Overview
Perform an authentic Google Pixel Material 3 Expressive design overhaul, fluid spring physics & gestures, Pro Controls & live histogram polish, and physical Android device deployment with automated verification.

## Milestones

### Milestone 1: Pixel M3 Expressive Design & Viewfinder Aesthetics (R1)
- Authentic Google Pixel M3 Expressive design tokens, colors, shapes, typography.
- Reusable `Modifier.pixelGlass` frosted glass blur modifier and pill containers.
- Viewfinder HUD decoupling (top status bar with expressive pills, framing grids with dark shadows, dual-axis 3D horizon leveler with snap animation, layered spring shutter button).
- Viewfinder screen assembly and layout cleanup.

### Milestone 2: Fluid Spring Motion & Tactile Micro-Interactions (R2)
- Center-snapping Mode Carousel with spring physics and mode switch haptics.
- Continuous Zoom Selector dial with tick marks, spring expansion, and quick preset pills (.5x, 1x, 2x, 5x, 10x).
- Dual Exposure sliders (Sun EV in warm gold, Moon Shadows in cool white) with 48dp touch targets and 0.0 zero-detent snaps.
- Viewfinder multi-touch gestures: pinch-to-zoom, double-tap lens flip, long-press AE/AF lock.
- Pro Controls bottom sheet fluid spring transitions.

### Milestone 3: Pro Controls Sheet, Live Histogram & Gallery EXIF Polish (R3)
- `PixelProSlider` custom slider with responsive floating indicators, stop ticks, and tactile snap feedback.
- Real-time live RGB / Luminance histogram graph rendering with cubic bezier spline paths, vertical linear gradient fills, highlight/shadow clipping alerts, and RGB/Luma/Split toggles.
- In-app Gallery Viewer overhaul with high-res photo rendering, Google Photos / Pixel-style M3 Expressive EXIF card (aperture, shutter, ISO, computational badges, GPS chip), and native Android Share Sheet intent.

### Milestone 4: Automated Build, Verification & Physical Device Deployment (R4)
- Multiplatform test execution via `./gradlew :shared:desktopTest`.
- Android debug build compilation and APK install via `./gradlew :composeApp:installDebug` to connected physical device (`Nothing A015` on Android 16).
- Launch `MainActivity` on physical device via `adb shell am start`.
- Capture screenshot via `adb exec-out screencap -p` and verify UI rendering and visual polish.

## Iteration & Verification Loop
For each milestone:
1. Worker implements changes and executes build/unit tests.
2. Reviewers (2) verify code quality, theme consistency, and correctness.
3. Challengers (2) verify edge cases, boundary conditions, and responsiveness.
4. Forensic Auditor (1) verifies code integrity.
5. Gate evaluation in `GATE_STATUS.md`.
