# E2E Test Infra: AuraCam KMP Material 3 Expressive Overhaul

## Test Philosophy
- Multi-tier verification: Unit/Desktop test coverage + UI component state verification + End-to-End device deployment and visual rendering confirmation.
- Automated Gradle test execution: `./gradlew :shared:desktopTest`
- Automated device installation: `./gradlew :composeApp:installDebug`
- Real-time device execution verification: `adb shell am start -n com.auracam.pixelcamera.debug/com.auracam.app.MainActivity` and screen capture inspection.

## Feature Inventory & Test Coverage
| # | Feature | Tier 1 (Unit/State) | Tier 2 (Boundary/Edge) | Tier 3 (Integration) | Tier 4 (Device E2E) |
|---|---------|:-------------------:|:----------------------:|:--------------------:|:-------------------:|
| 1 | M3 Expressive Tokens & Theme | ✓ | ✓ | ✓ | ✓ |
| 2 | Frosted Glass Blur & Pill Containers | ✓ | ✓ | ✓ | ✓ |
| 3 | High-Contrast Typography & Leveler | ✓ | ✓ | ✓ | ✓ |
| 4 | Shutter Button & Viewfinder HUD | ✓ | ✓ | ✓ | ✓ |
| 5 | Mode Carousel Scroll Snapping | ✓ | ✓ | ✓ | ✓ |
| 6 | Zoom Selector Dial & Presets | ✓ | ✓ | ✓ | ✓ |
| 7 | Dual Exposure Sliders (Sun & Moon) | ✓ | ✓ | ✓ | ✓ |
| 8 | Pro Controls Bottom Sheet Motion | ✓ | ✓ | ✓ | ✓ |
| 9 | Pro Controls Tactile Sliders | ✓ | ✓ | ✓ | ✓ |
| 10 | Live RGB / Luminance Histogram Graph | ✓ | ✓ | ✓ | ✓ |
| 11 | Gallery Viewer & EXIF Details Card | ✓ | ✓ | ✓ | ✓ |
| 12 | Native Share Sheet Integration | ✓ | ✓ | ✓ | ✓ |
| 13 | Multiplatform Compilation & Desktop Tests | ✓ | ✓ | ✓ | ✓ |
| 14 | Physical Device Deployment | - | - | - | ✓ |
| 15 | Screen Capture & Visual Verification | - | - | - | ✓ |
