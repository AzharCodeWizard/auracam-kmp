# Original User Request

## 2026-08-29T13:38:31Z

Comprehensive Google Pixel Material 3 Expressive design overhaul and visual polish for the AuraCam KMP camera application, enhancing frosted glass surfaces, fluid spring physics, tactile haptics, interactive control sheets, and deploying directly onto connected Android hardware.

Working directory: /Users/azhar/.gemini/antigravity/scratch/auracam-kmp
Integrity mode: development

## Requirements

### R1. Pixel Material 3 Expressive Design & Viewfinder Aesthetics
Refine the viewfinder layout, top quick-controls status pill, and HUD badges using authentic Google Pixel M3 Expressive design tokens, pill containers, frosted glass blur backgrounds, and high-contrast typography.

### R2. Fluid Spring Motion & Tactile Micro-Interactions
Implement spring physics and smooth transitions for the Mode Carousel scroll snapping, Zoom Selector dial, Dual Exposure sliders (Sun EV & Moon Shadows), and the Pro Controls bottom sheet.

### R3. Pro Controls Sheet & Gallery Viewer Polish
Refine the Pro Controls bottom sheet with responsive slider indicators, real-time live RGB / Luminance histogram graph styling, and polish the in-app Gallery Viewer with full EXIF details card and native Share Sheet action.

### R4. Automated Build, Verification & Physical Device Deployment
Compile the application, run unit tests, install debug build onto the connected physical Android device via `./gradlew :composeApp:installDebug`, launch `MainActivity`, and verify UI rendering via screen capture.

## Acceptance Criteria

### Visual & Interactive Polish
- [ ] Viewfinder, mode carousel, zoom pills, and pro controls follow Google Pixel Material 3 Expressive visual standards.
- [ ] Mode switching and Pro sheet expansions animate smoothly with fluid transitions.
- [ ] Dual exposure sliders on viewfinder respond to vertical drag gestures with live EV readouts.
- [ ] In-app gallery viewer renders full EXIF metadata and native share triggers cleanly.

### Device Deployment & Automated Verification
- [ ] `./gradlew :shared:desktopTest` passes with 0 test failures.
- [ ] `./gradlew :composeApp:installDebug` succeeds and installs the APK onto the connected Android device.
- [ ] App launches into foreground on device, verified via ADB screencap.
