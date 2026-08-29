# Project: AuraCam KMP — Google Pixel Material 3 Expressive Overhaul

## Architecture
AuraCam KMP is a Kotlin Multiplatform camera application targeting Android (via CameraX & Jetpack Compose) and Desktop (Compose Multiplatform mock engine & test harness).
- `shared`: Multiplatform business logic, state models (`CameraUiState`, `CameraSettings`, `ExifMetadata`, `HistogramData`), audio/haptics contracts (`SoundAndHaptics`), and desktop test suite.
- `composeApp`: Android and desktop UI layer containing Compose components (`AuraCamTheme`, `ViewfinderScreen`, `ModeCarousel`, `ZoomSelector`, `DualExposureSliders`, `ProControlsSheet`, `HistogramViewer`, `GalleryPreviewSheet`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | M3 Expressive Design Tokens & Palette | Pixel Camera authentic color schemes, high-contrast dark theme, expressive container tokens | M1 | Survey 1 (R1) |
| 2 | Frosted Glass Blur & Pill Containers | `pixelGlass` modifier, blur backgrounds, pill containers with subtle stroke and elevation | M1 | Survey 1 (R1) |
| 3 | High-Contrast Typography & Leveler | Tabular numbers, drop shadows for viewfinder legibility, dual-axis 3D leveler indicator | M1 | Survey 1 (R1) |
| 4 | Authentic Shutter Button & Viewfinder HUD | Layered spring depression shutter button, top/bottom bar layout, framing grid overlays | M1 | Survey 1 (R1) |
| 5 | Mode Carousel Scroll Snapping | Centered snapping mode carousel with spring deceleration physics and tactile mode change haptics | M2 | Survey 2 (R2) |
| 6 | Zoom Selector Dial & Spring Transitions | Quick preset pills (.5x, 1x, 2x, 5x, 10x) expanding into continuous ruler dial with micro-haptic ticks | M2 | Survey 2 (R2) |
| 7 | Dual Exposure Sliders (Sun EV & Moon Shadows) | Sun EV (warm gold) and Moon Shadows (cool white) sliders with 48dp touch targets, magnetic 0.0 zero-detent, and auto-dismiss | M2 | Survey 2 (R2) |
| 8 | Pro Controls Bottom Sheet Motion | Fluid spring expand/collapse transitions, sliding tab indicator capsule, and drag handle | M2 | Survey 2 (R2) |
| 9 | Pro Controls Tactile Sliders & Manual Controls | Tactile `PixelProSlider` for ISO, Shutter, Focus, WB, and EV with snap ticks and indicator animations | M3 | Survey 3 (R3) |
| 10 | Real-Time Live RGB / Luminance Histogram Graph | Smooth cubic bezier area curves, linear gradient fills, highlight/shadow clipping indicators, RGB/Luma toggles | M3 | Survey 3 (R3) |
| 11 | In-App Gallery Viewer Polish & EXIF Details Card | High-res photo display, expandable Google Photos / Pixel-style M3 Expressive EXIF card, metadata pills | M3 | Survey 3 (R3) |
| 12 | Native Share Sheet Integration | Robust Android Intent integration for sharing captured photos directly from gallery sheet | M3 | Survey 3 (R3) |
| 13 | Automated Test Suite Verification | Multiplatform unit & UI tests passing via `./gradlew :shared:desktopTest` | M4 | Survey 3 (R4) |
| 14 | Physical Device Deployment & Launch | Clean build & install via `./gradlew :composeApp:installDebug` on connected Android device | M4 | Survey 3 (R4) |
| 15 | UI Screen Capture & Visual Polish Verification | Launch `MainActivity`, capture device screen via `adb exec-out screencap`, verify visual rendering | M4 | Survey 3 (R4) |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Pixel M3 Expressive Design & Viewfinder Aesthetics | Features 1, 2, 3, 4: AuraCamTheme, typography, pixelGlass, Viewfinder HUD, leveler, shutter button | none | IN_PROGRESS |
| 2 | M2: Fluid Spring Motion & Tactile Micro-Interactions | Features 5, 6, 7, 8: Mode Carousel snapping, Zoom Dial/presets, Dual Exposure sliders, bottom sheet spring physics | M1 | PLANNED |
| 3 | M3: Pro Controls Sheet, Real-Time Histogram & Gallery EXIF | Features 9, 10, 11, 12: Tactile pro sliders, Bezier curve live histogram, Gallery EXIF card, Native Share | M1, M2 | PLANNED |
| 4 | M4: Automated Build, Desktop Tests, Android Device Install & Screen Capture | Features 13, 14, 15: Gradle test suite, debug install to physical device, launch activity, screen capture verification | M1, M2, M3 | PLANNED |

## Interface Contracts
### `CameraUiState` ↔ UI Components
- State: `CameraUiState` exposes `zoomRatio`, `exposureCompensation`, `shadowCompensation`, `activeCameraMode`, `proSettings`, `histogramData`, `lastCapturedPhoto`.
- Events: `onZoomChanged(Float)`, `onExposureChanged(Float)`, `onShadowChanged(Float)`, `onModeSelected(CameraMode)`, `onProSettingChanged(...)`, `onCaptureClicked()`.

### `SoundAndHaptics` Contract
- `vibrateTick()`: micro-haptic on dial tick / slider step (API 29+ `EFFECT_TICK`).
- `vibrateDetent()`: magnetic zero-snap detent click (API 29+ `EFFECT_CLICK` or heavy click).
- `vibrateModeChange()`: mode switch feedback.
- `vibrateLock()`: AE/AF lock confirmation.

## Code Layout
- `shared/src/commonMain/kotlin/com/auracam/`:
  - `domain/`: `CameraEngine.kt`, `SoundAndHaptics.kt`, `PlatformShare.kt`
  - `model/`: `CameraMode.kt`, `CameraSettings.kt`, `ExifMetadata.kt`, `HistogramData.kt`
- `composeApp/src/commonMain/kotlin/com/auracam/ui/`:
  - `theme/`: `AuraCamTheme.kt`, `Color.kt`, `Type.kt`, `Shape.kt`
  - `components/`:
    - `Glass.kt`: `pixelGlass` modifier and frosted glass styling
    - `ModeCarousel.kt`: Snapping mode selector
    - `ZoomSelector.kt`: Preset pills & continuous dial
    - `DualExposureSliders.kt`: Sun EV and Moon Shadow sliders with zero detents
    - `ProControlsSheet.kt`: Manual controls and parameter sliders
    - `HistogramViewer.kt`: Bezier curve RGB/Luma live graph
    - `GalleryPreviewSheet.kt`: Gallery viewer & EXIF metadata card
    - `ViewfinderControls.kt`: Shutter button, HUD top/bottom bars, leveler
  - `screen/`: `ViewfinderScreen.kt`
- `shared/src/desktopTest/kotlin/com/auracam/`: Unit tests and state verification
