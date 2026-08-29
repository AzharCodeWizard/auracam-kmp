# Handoff Report — Requirement R1: Pixel Material 3 Expressive Design & Viewfinder Aesthetics

**Date**: 2026-08-29  
**Agent**: Explorer 1 (`explorer_survey_1`)  
**Mission**: Codebase investigation, styling gap analysis, and architectural blueprints for Requirement R1.

---

## 1. Observation

Direct observations from codebase inspection:

1. **Theme & Tokens** (`composeApp/src/commonMain/kotlin/com/auracam/ui/theme/AuraCamTheme.kt:8-45`):
   - Defined 10 color constants (`PixelDarkBackground`, `PixelSurfaceDark`, `PixelSurfaceVariant`, `PixelYellowAccent`, `PixelGoogleBlue`, `PixelLevelerGreen`, `PixelFocusPeakingGreen`, `PixelRecordRed`, `PixelTextWhite`, `PixelTextMuted`).
   - `darkColorScheme` specifies only 9 fields, missing M3 Expressive tonal container tokens (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceDim`, `surfaceBright`, `outlineVariant`, `scrim`).
   - `typography = Typography()` uses default Material3 typography with no custom text shadow, tabular digits, or camera metric typography definitions.
   - `shapes` parameter is omitted from `MaterialTheme`.
   - No unified frosted glass / translucent scrim / pill modifier is defined.

2. **Viewfinder Layout Hierarchy** (`composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt:67-176`):
   - Viewport uses `Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center)` containing `ratioModifier.fillMaxSize().clip(RoundedCornerShape(24.dp))`.
   - `ViewfinderOverlay` is placed inside this ratio-constrained Box, causing top status badges and quick settings pill to be constrained/shifted when changing aspect ratios (e.g., `1:1` or `4:3`).

3. **Viewfinder HUD Overlays** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/ViewfinderOverlay.kt:67-379`):
   - Top status bar (`lines 164-288`): Quick settings pill and status badges (`ColorProfile`, `Ultra HDR`, `RAW`, `Flash`, `Timer`, `Settings`) are laid out in a flat `Row(spacedBy = 6.dp)` with basic alpha boxes (`0x881E1E1E`, `0x55333333`, `0x66FFDB58`), risking overflow when multiple badges are active.
   - 3D Horizon Leveler (`lines 120-161`): Single horizontal roll line; lacks dual-axis pitch reticle, green pulse snap transition, and smooth numerical degree rendering.
   - Framing Grids (`lines 67-102`): 1dp hairlines drawn without contrast drop shadows.

4. **Focus & Dual Exposure Sliders** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/FocusBracketOverlay.kt:89-195`):
   - Sliders use basic box indicators inside a `0xCC181818` pill container without Google Pixel Sun EV / Moon Shadow icons or neutral center detent. Lacks an inactivity auto-hide timer.

5. **Zoom Selector & Mode Carousel** (`ZoomSelector.kt:33-78`, `ModeCarousel.kt:49-93`):
   - `ZoomSelector`: Basic `0x771E1E1E` box with discrete buttons; lacks animated sliding pill indicator and frosted glass border highlight.
   - `ModeCarousel`: Basic `LazyRow` with individual clickable boxes; lacks center snap alignment, active pill backing, and edge gradient fade scrims.

6. **Shutter Row** (`composeApp/src/commonMain/kotlin/com/auracam/ui/components/ShutterRow.kt:57-175`):
   - Shutter button has outer white ring and inner disc; lacks tactile pointer-down depression scaling (`0.92f`) and smooth video/photo morphing. Flip button lacks 180° rotation spring animation.

7. **Build & Test Status**:
   - Executed `./gradlew :shared:desktopTest` -> **BUILD SUCCESSFUL in 1s** (4/4 tests passed).

---

## 2. Logic Chain

1. **Observation 1 & 3** show that HUD elements currently use arbitrary, unstandardized alpha colors (`0x881E1E1E`, `0x771E1E1E`, `0x55333333`) and unstyled text without drop shadows.  
   $\rightarrow$ Therefore, creating an extended `PixelExpressiveTheme` with complete M3 Expressive tokens, high-contrast `CameraTypography` (with drop shadows and tabular figures), and a unified `Modifier.pixelGlass` will immediately resolve visual inconsistency and guarantee legibility across extreme lighting conditions.

2. **Observation 2** shows that `ViewfinderOverlay` is nested inside the aspect ratio frame `BoxWithConstraints`.  
   $\rightarrow$ Therefore, decoupling top and bottom HUD controls to anchor directly to the parent screen safe insets (while keeping only the camera preview, grids, leveler, and focus bracket inside the aspect ratio box) is necessary to achieve authentic Google Pixel viewfinder mechanics.

3. **Observations 4, 5, 6** reveal missing tactile visual affordances (Sun/Moon exposure icons, sliding zoom pill capsule, centered carousel snapping with edge gradients, shutter press depression, flip rotation).  
   $\rightarrow$ Therefore, targeted enhancements to these specific component files will satisfy Requirement R1 while cleanly integrating with R2/R3 micro-interactions.

---

## 3. Caveats

1. **Platform Shaders / Backdrop Blur**: On older Android versions (pre-Android 12 API 31) and some desktop GPU drivers, realtime backdrop blur shaders may fall back to translucent scrims (`Color(0x991E1E20)` with 1dp border). The proposed `Modifier.pixelGlass` is designed with this graceful fallback.
2. **Investigation Scope**: This report focuses strictly on Requirement R1 (Theming, Tokens, HUD Overlays, Pill Containers, Frosted Glass, Typography, Viewfinder Aesthetics). Micro-interaction spring physics (R2) and pro controls data pipelines (R3) were evaluated solely in terms of visual layout and styling affordances.

---

## 4. Conclusion

The AuraCam KMP codebase possesses a solid multiplatform architecture and clear component boundaries. Achieving an authentic Google Pixel Material 3 Expressive overhaul requires:
1. Expanding `AuraCamTheme.kt` with full M3 Expressive tokens, `CameraTypography`, and `pixelGlass` frosted glass modifier.
2. Structurally decoupling top status HUD and bottom controls from the aspect ratio viewport in `ViewfinderScreen.kt`.
3. Updating HUD overlays (`ViewfinderOverlay.kt`, `FocusBracketOverlay.kt`, `ZoomSelector.kt`, `ModeCarousel.kt`, `ShutterRow.kt`, `QuickSettingsDialog.kt`, `ProControlsSheet.kt`, `GalleryPreviewSheet.kt`) to utilize expressive pill containers, high-contrast typography with shadows, Sun/Moon dual exposure icons, and dual-axis leveler visuals.

---

## 5. Verification Method

To independently verify these findings:
1. Run `./gradlew :shared:desktopTest` to verify baseline unit tests.
2. Inspect `composeApp/src/commonMain/kotlin/com/auracam/ui/theme/AuraCamTheme.kt` and note the missing M3 Expressive container tokens and default `Typography()`.
3. Inspect `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/ViewfinderScreen.kt:82-175` to verify the nesting of `ViewfinderOverlay` inside the aspect ratio box.
4. Review the full survey report artifact at:
   `/Users/azhar/.gemini/antigravity/scratch/auracam-kmp/.agents/explorer_survey_1/survey_m3_aesthetics.md`.
