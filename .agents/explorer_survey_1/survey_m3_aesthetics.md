# Survey & Architectural Analysis: Requirement R1 (Pixel Material 3 Expressive Design & Viewfinder Aesthetics)

**Date**: 2026-08-29  
**Investigator**: Explorer 1  
**Project**: AuraCam KMP Material 3 Expressive Overhaul  
**Target Requirement**: R1 — Pixel Material 3 Expressive Design & Viewfinder Aesthetics (authentic Google Pixel M3 Expressive design tokens, pill containers, frosted glass blur backgrounds, high-contrast typography)

---

## 1. Executive Summary

A comprehensive architectural inspection of the AuraCam Kotlin Multiplatform (KMP) codebase was conducted to evaluate the visual presentation, styling architecture, HUD overlays, typography, and viewfinder container systems against Google Pixel Material 3 Expressive standards (Android 14/15/16 Camera / Google Camera 9.x+ design language).

While AuraCam establishes a functional Compose Multiplatform camera UI structure, its styling layer currently relies on standard Material 3 defaults with basic alpha fills (`Color(0x881E1E1E)`), lacks specialized M3 Expressive design tokens, lacks unified frosted glass / translucent scrim modifiers, uses default unstyled typography lacking high-contrast drop shadows and tabular HUD metrics, and exhibits layout coupling between the aspect ratio viewport and top HUD status badges.

This document details the exact current implementation, catalogs all aesthetic gaps, and provides concrete technical blueprints for implementing authentic Google Pixel Material 3 Expressive styling across the shared UI layer.

---

## 2. Codebase Inventory & Component Mapping

| Component / Layer | Source File Path | Key Classes / Composables | Current Aesthetic State |
|---|---|---|---|
| **Theme & Tokens** | `composeApp/src/commonMain/kotlin/com/auracam/ui/theme/AuraCamTheme.kt` | `AuraCamTheme`, `DarkColorScheme`, color constants | Minimal 10-color dark palette; missing M3 Expressive tonal containers, shapes, and camera typography tokens |
| **Root Application** | `composeApp/src/commonMain/kotlin/com/auracam/ui/App.kt` | `App`, `ScreenState` | Basic horizontal slide navigation between viewfinder and settings |
| **Viewfinder Screen** | `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt` | `ViewfinderScreen` | Main layout container; viewport ratio clipping (`RoundedCornerShape(24.dp)`); status badges embedded inside viewport |
| **HUD & Overlays** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ViewfinderOverlay.kt` | `ViewfinderOverlay` | Top status bar badges, 3D Leveler line, Rule of Thirds / Golden Ratio grids, Capture Progress |
| **Focus & Exposure** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/FocusBracketOverlay.kt` | `FocusBracketOverlay` | Yellow corner bracket; EV / Shadow dual vertical sliders with basic alpha pill background |
| **Zoom Selector** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ZoomSelector.kt` | `ZoomSelector` | Floating pill capsule with discrete buttons (`.5`, `1x`, `2`, `5`) and basic color transitions |
| **Mode Carousel** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ModeCarousel.kt` | `ModeCarousel` | `LazyRow` with individual rounded pill buttons for 8 camera modes |
| **Shutter Row** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ShutterRow.kt` | `ShutterRow` | 86.dp shutter button with outer ring, circular gallery preview button, camera flip button |
| **Quick Settings Sheet** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/QuickSettingsDialog.kt` | `QuickSettingsDialog`, `PillButton` | Card container (`0xF0202020`), segmented pill buttons for Aspect, Flash, Timer, Format, LUT, Grid |
| **Pro Controls Sheet** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/ProControlsSheet.kt` | `ProControlsSheet`, `HistogramViewer` | Top-rounded bottom sheet (`0xE6181818`), tab chips for ISO, Shutter, Focus, EV, WB, RGB Histogram |
| **Gallery Viewer** | `composeApp/src/commonMain/kotlin/com/auracam/ui/components/GalleryPreviewSheet.kt` | `GalleryPreviewSheet`, `ExifRow` | Fullscreen modal preview with EXIF metadata card and watermark badge overlay |
| **Settings Screen** | `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/SettingsScreen.kt` | `SettingsScreen`, `SettingsGroup` | M3 settings screen with switches and system info |

---

## 3. Deep Dive: Theme, Colors, Shapes & Typography

### 3.1 Existing Theme Architecture (`AuraCamTheme.kt:1-46`)
```kotlin
val PixelDarkBackground = Color(0xFF0E0E0E)
val PixelSurfaceDark = Color(0xFF1E1E1E)
val PixelSurfaceVariant = Color(0xFF2D2D2D)
val PixelYellowAccent = Color(0xFFFFDB58) // Pixel 8/9 Pro warm golden yellow
val PixelGoogleBlue = Color(0xFF8AB4F8)
val PixelLevelerGreen = Color(0xFF81C995)
val PixelFocusPeakingGreen = Color(0xFF00FF66)
val PixelRecordRed = Color(0xFFEA4335)
val PixelTextWhite = Color(0xFFF1F1F1)
val PixelTextMuted = Color(0xFF9E9E9E)
```
- **Observations**:
  1. Color scheme only specifies a sparse set of `darkColorScheme` attributes: `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `background`, `surface`, `surfaceVariant`, `error`.
  2. Missing Material 3 Expressive tonal surface tokens (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceDim`, `surfaceBright`, `outlineVariant`, `scrim`).
  3. `typography = Typography()` uses default uncustomized Material 3 typography with system standard font metrics.
  4. `shapes` parameter is completely omitted from `MaterialTheme`, falling back to standard M3 rounded corners instead of Pixel Expressive pill tokens.

### 3.2 Gaps in M3 Expressive Tokens & Color Palette
1. **Pixel Camera Tonal Glassmorphism**:
   - Pixel Camera utilizes a multi-tiered translucent scrim system:
     - Ultra-dark backdrop / letterbox: `Color(0xFF000000)` (OLED True Black).
     - Heavy Frosted Glass container: `Color(0x991E1E20)` with 1dp subtle white outline `Color(0x1FFFFFFF)`.
     - Light Frosted Glass pill: `Color(0x662C2C2E)` with 1dp outline `Color(0x26FFFFFF)`.
     - Active Tonal Accent: `Color(0xFFFFDB58)` (Pixel Warm Golden Yellow) for photo mode; `Color(0xFFEA4335)` for video mode; `Color(0xFF8AB4F8)` for RAW/Pro modes; `Color(0xFF81C995)` for Leveler/HDR.
2. **High-Contrast Viewfinder Typography Tokens**:
   - In dynamic camera conditions (e.g. bright sun, snow, backlighting), standard text without drop shadows is illegible.
   - Text rendering requires:
     - `Shadow(color = Color(0xB3000000), offset = Offset(0f, 2f), blurRadius = 4f)` applied to all on-viewfinder metrics.
     - Monospaced / Tabular numbers for live metrics: Shutter speed (`1/1000s`), ISO (`ISO 400`), Zoom (`1.0x`), Timer (`00:04`), EV (`+0.7`), Kelvin (`5500K`).
     - Distinct typography hierarchy: `CameraTypography` encompassing Display Large (countdown timer), Title Medium (mode labels), Label Large (pill buttons), Label Small / Micro (status badges, EV indicators).
3. **M3 Expressive Shape Tokens**:
   - Full Pill shapes (`CircleShape` / `RoundedCornerShape(100.dp)`) for chips, zoom selector, exposure capsule, shutter outer disc.
   - Smooth viewport corner radius (`RoundedCornerShape(28.dp)` or `RoundedCornerShape(32.dp)`).
   - Bottom and Top sheet container radiuses (`RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`).

---

## 4. Deep Dive: Viewfinder Layout, HUD Overlays & Pill Containers

### 4.1 Viewfinder Frame & Aspect Ratio Layout (`ViewfinderScreen.kt:67-176`)
- **Current Observation**:
  - The viewport `BoxWithConstraints` is wrapped in `ratioModifier.fillMaxSize().clip(RoundedCornerShape(24.dp))`.
  - Inside this clipped box, `ViewfinderOverlay` is placed.
  - **Critical Layout Flaw**: Placing `ViewfinderOverlay` inside the aspect ratio container causes the top status bar (Quick Settings pill, Ultra HDR badge, RAW badge, Settings button) and bottom zoom selector to move up/down and shrink horizontally when switching to `1:1` or `4:3` ratio modes.
  - **Pixel Camera Standard**:
    - The Top Action Bar (Quick Settings Pill, Status Badges, Settings Icon) and Bottom Controls (Zoom Selector, Mode Carousel, Shutter Row) are pinned to the outer screen safe bounds.
    - Only the live camera preview, framing grids, 3D leveler, and focus brackets reside within the animated aspect ratio viewport frame.

### 4.2 HUD Overlays & Pill Containers Detailed Analysis

#### A. Top Status Bar & Quick Settings Pill (`ViewfinderOverlay.kt:164-288`)
- **Current State**:
  - `Box` with `RoundedCornerShape(20.dp)` and `background(Color(0x881E1E1E))`.
  - Status badges (`ColorProfile`, `Ultra HDR`, `RAW`, `Flash`, `Timer`) in a single horizontal `Row(spacedBy = 6.dp)`.
- **Gaps**:
  - If multiple badges are active, they exceed screen width on compact devices.
  - Badges use flat boxes without expressive pill borders or glass highlights.
  - Lacks unified M3 Expressive icon + text chip styling.

#### B. 3D Horizon Leveler (`ViewfinderOverlay.kt:120-161`)
- **Current State**:
  - Single line drawn with `drawLine(levelColor, Offset(20f, size.height/2f + roll), Offset(w-20f, size.height/2f - roll))` and center circle.
  - When level (`leveler.isLevel == true`), a `0° LEVEL` pill pops up at `offset(y = -24.dp)`.
- **Gaps**:
  - True Pixel 8/9 Leveler features dual-axis pitch and roll:
    - Pitch indicator: Centered tick reticle that aligns with horizon bar.
    - Snap animation: Elastic scale pulse and color morph from subtle white/gray (`Color(0x80FFFFFF)`) to Google Emerald Green (`Color(0xFF81C995)`) when within ±1.0° roll/pitch.
    - Tactile degree indicator with smooth numeric transition.

#### C. Framing Grids (`ViewfinderOverlay.kt:67-102`)
- **Current State**:
  - Basic 1dp hairlines drawn in `Canvas` with `Color(0x33FFFFFF)`.
  - Supports `RULE_OF_THIRDS`, `GOLDEN_RATIO`, `SQUARE`, `NONE`.
- **Gaps**:
  - Hairlines lack subtle dark drop shadow strokes (`Color(0x40000000)` offset by 1px) to prevent disappearing over white/bright backgrounds (e.g. clouds, white walls).
  - Center crosshair reticle is missing for precision rule-of-thirds centering.

#### D. Focus Bracket & Dual Exposure Sliders (`FocusBracketOverlay.kt:1-197`)
- **Current State**:
  - Yellow corner bracket with pulsing alpha (`pulseAlpha`).
  - EV and Shadow sliders in a static `Row` on the right side of the bracket (`Color(0xCC181818)`).
- **Gaps**:
  - Sliders use standard Box thumbs instead of expressive Pixel pill tracks with Sun EV icon and Moon Shadow icon.
  - Lacks auto-dismiss timer (Pixel Camera hides focus bracket & exposure sliders after 4 seconds of user inactivity).
  - Slider tracks lack tactile detents at `0.0 EV` neutral center position.

#### E. Zoom Selector (`ZoomSelector.kt:1-79`)
- **Current State**:
  - Pill container (`RoundedCornerShape(24.dp)`, `Color(0x771E1E1E)`).
  - Four discrete preset chips: `.5`, `1x`, `2`, `5`.
- **Gaps**:
  - Selected preset uses flat yellow fill without smooth sliding pill backdrop indicator.
  - Lacks continuous zoom dial / expander micro-interaction for smooth pinch-to-zoom feedback.
  - Missing frosted glass translucent scrim styling.

#### F. Mode Carousel (`ModeCarousel.kt:1-94`)
- **Current State**:
  - `LazyRow` with individual rounded pill buttons.
  - Yellow text and `0x33FFDB58` background for selected mode.
- **Gaps**:
  - Modes are not center-aligned; selecting an item does not center-snap it into the viewfinder focus position.
  - Missing edge gradient fade scrims (left/right fade to black `#000000`).
  - Active indicator is a box background rather than an authentic Pixel pill dot or high-contrast pill backdrop.

#### G. Shutter Row (`ShutterRow.kt:1-176`)
- **Current State**:
  - Left: Gallery circle thumbnail (`52.dp`, `0xFF2C2C2C`).
  - Center: Shutter button (`86.dp`, outer white ring 3.5dp, inner white/red disc 68.dp).
  - Right: Camera flip button (`52.dp`, `0xFF2C2C2C`).
- **Gaps**:
  - Shutter button lacks dynamic press depression scale effect (spring bounce to `0.92f` on pointer down).
  - Shutter button inner disc color transition between Photo (white) and Video/Record (red circle/square) is instant rather than animated.
  - Camera flip button lacks rotation spring animation on tap.
  - Gallery thumbnail lacks authentic border elevation and high-contrast mode indicator badge.

#### H. Sheets & Dialogs (`QuickSettingsDialog.kt`, `ProControlsSheet.kt`, `GalleryPreviewSheet.kt`)
- **Current State**:
  - `QuickSettingsDialog`: Standard M3 `Card` with `0xF0202020` fill.
  - `ProControlsSheet`: Rounded top box with `0xE6181818` fill, horizontal tabs, and basic sliders.
  - `GalleryPreviewSheet`: Fullscreen black container with radial gradient placeholder and metadata card.
- **Gaps**:
  - Lack uniform frosted glass blur / translucent scrim treatments.
  - Sliders and assist chips lack M3 Expressive pill shapes and typography contrast.

---

## 5. Architectural Blueprints & Technical Recommendations

### 5.1 Comprehensive M3 Expressive Design Token System
Create a dedicated design system module/file `composeApp/src/commonMain/kotlin/com/auracam/ui/theme/PixelExpressiveTheme.kt` providing:
1. **Extended Color Tokens**:
   - `PixelPitchBlack = Color(0xFF000000)`
   - `PixelDarkSurface = Color(0xFF131314)`
   - `PixelGlassScrim = Color(0x801E1E20)`
   - `PixelGlassBorder = Color(0x26FFFFFF)`
   - `PixelYellowAccent = Color(0xFFFFDB58)` (Warm Golden Yellow)
   - `PixelYellowContainer = Color(0x33FFDB58)`
   - `PixelGoogleBlue = Color(0xFF8AB4F8)`
   - `PixelLevelerGreen = Color(0xFF81C995)`
   - `PixelPeakingGreen = Color(0xFF00E676)`
   - `PixelRecordRed = Color(0xFFEA4335)`
   - `PixelTextPrimary = Color(0xFFF1F1F1)`
   - `PixelTextSecondary = Color(0xFFB0B0B4)`
   - `PixelTextMuted = Color(0xFF757579)`
2. **Frosted Glass Scrim Modifier**:
   - Create reusable composable modifier `Modifier.pixelGlass(...)` that applies `clip(shape)`, `background(PixelGlassScrim)`, `border(1.dp, PixelGlassBorder, shape)`, and optional backdrop blur where supported.
3. **High-Contrast Camera Typography**:
   - Custom `CameraTypography` object providing:
     - `countdown`: 48.sp, bold, tabular figures, text shadow.
     - `hudMetric`: 12.sp, semi-bold, monospaced tabular figures (`1/125s`, `ISO 100`, `+0.3 EV`, `5500K`).
     - `pillLabel`: 13.sp, medium weight, 0.4.sp letter spacing.
     - `badgeSmall`: 10.sp, bold, all caps, 0.8.sp letter spacing.
     - `modeTitle`: 14.sp, Google Sans medium/bold.

### 5.2 Decoupled Viewfinder Layout Architecture
Refactor `ViewfinderScreen.kt` layout hierarchy:
```
Box (fillMaxSize, background(Black), safeDrawing insets)
├── Viewfinder Viewport (Centered Box with aspect ratio clipping)
│   ├── CameraPreview (Hardware stream)
│   ├── Color Profile / Real Tone tint layer
│   ├── Framing Grid Canvas (hairlines + contrast shadows)
│   ├── 3D Horizon Dual-Axis Leveler
│   └── FocusBracketOverlay (Yellow bracket + Sun/Moon dual exposure sliders + auto-dismiss)
├── Pinned Top Status Bar (TopCenter)
│   ├── Quick Settings Pill ("Settings ▾")
│   └── Status Badge Pill Group (Ultra HDR, RAW, Flash, Timer, Color LUT, Settings Gear)
├── Pinned Bottom Controls (BottomCenter Column)
│   ├── Floating Zoom Selector (Pill capsule with sliding indicator)
│   ├── Pro Controls Sheet (Animated expand/collapse with tactile sliders)
│   ├── Mode Carousel (Center-snapped with edge fade gradient)
│   └── Shutter Row (Tactile shutter, live gallery thumbnail, flip spring)
└── Fullscreen Overlays / Sheets
    ├── QuickSettingsDialog (Frosted glass dropdown panel)
    └── GalleryPreviewSheet (Full EXIF card + Share sheet)
```

### 5.3 Concrete File Modification Plan for Implementation

| Target File | Changes Required |
|---|---|
| `composeApp/.../ui/theme/AuraCamTheme.kt` | Expand M3 `DarkColorScheme` with full tonal tokens; add `Shapes` definitions; define `CameraTypography` with high-contrast text shadows & tabular figures; implement `pixelGlass` modifier and frosted glass tokens |
| `composeApp/.../ui/screens/ViewfinderScreen.kt` | Decouple top/bottom bars from viewport aspect ratio box; apply animated aspect ratio resizing; integrate unified frosted glass styling; connect auto-dismiss timers |
| `composeApp/.../ui/components/ViewfinderOverlay.kt` | Refactor top status bar into consolidated glass pill container; enhance 3D leveler with pitch & roll dual-axis indicator, haptic snap and green pulse; add contrast drop-shadows to framing grids |
| `composeApp/.../ui/components/FocusBracketOverlay.kt` | Update exposure sliders with Sun EV icon and Moon Shadow icon; add center detents; implement 4-second auto-hide timeout |
| `composeApp/.../ui/components/ZoomSelector.kt` | Upgrade to frosted glass pill capsule with animated sliding pill highlight; support tactile click bounce |
| `composeApp/.../ui/components/ModeCarousel.kt` | Add edge gradient scrims (`Brush.horizontalGradient`); center active mode; high-contrast typography |
| `composeApp/.../ui/components/ShutterRow.kt` | Refine shutter button layered concentric rings; add touch-down depression scaling; flip camera rotation spring |
| `composeApp/.../ui/components/QuickSettingsDialog.kt` | Apply frosted glass card background, M3 Expressive segmented pill buttons, clean section dividers |
| `composeApp/.../ui/components/ProControlsSheet.kt` | Frosted glass bottom sheet styling, M3 Expressive slider tracks and pill tabs |
| `composeApp/.../ui/components/GalleryPreviewSheet.kt` | Translucent frosted glass EXIF card, high-contrast watermark pill |
| `composeApp/.../ui/screens/SettingsScreen.kt` | M3 Expressive settings list styling with tonal container cards and Pixel yellow accents |

---

## 6. Summary of Recommendations

1. **Tokens First**: Establish the complete Pixel Material 3 Expressive color, shape, typography, and frosted glass design system in `AuraCamTheme.kt`.
2. **Unify Glassmorphism**: Use a consistent `pixelGlass` modifier across all HUD pills, dialogs, and sheets to ensure visual cohesiveness.
3. **High-Contrast Legibility**: Guarantee 100% legibility across extreme camera lighting by adding text shadows to on-screen metrics and framing grid lines.
4. **Structural Decoupling**: Separate pinned top/bottom UI anchors from the animated aspect ratio viewfinder frame.

All findings and blueprints are ready to be utilized for subsequent implementation milestones.
