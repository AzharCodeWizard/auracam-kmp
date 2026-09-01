# Project: AuraCam Director-Style Dual Recording (Vlog Mode)

## Architecture
AuraCam is a Kotlin Multiplatform (KMP) camera application built on Compose Multiplatform, CameraX 1.4.1 + Camera2 Interop (Android), and reactive Coroutines/StateFlow.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Compose Multiplatform UI (`composeApp`)                               │
│  ├── ViewfinderScreen (Stage, Overlay hierarchy, Clutter suppression)   │
│  ├── DirectorDualRecordingOverlay                                      │
│  │    ├── DirectorControlIsland (Frosted glass capsule: Layout, Swap,  │
│  │    │                          Filter)                               │
│  │    ├── 50/50 Split View (Edge-to-edge halves, 1dp divider, swap)    │
│  │    ├── Movable PiP View (16:9 rounded rect, 4-corner magnetic snap) │
│  │    └── Synchronous Live Tone Filter Layer (Real Tone, Vibrant, etc.)│
│  └── Shutter & Mode Controls (ModeCarousel with DUAL_VLOG)             │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Shared Camera Domain & State Engine (`shared`)                         │
│  ├── CameraEngine (Interface: StateFlows for mode, layout, filter, etc)│
│  ├── BaseCameraEngine (Common reactive state machine & simulations)    │
│  └── CameraEnums & Models (DualVlogLayout, ColorProfile, etc.)         │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Android Hardware Platform Layer (`shared/src/androidMain`)             │
│  ├── PlatformCameraEngine (CameraX ConcurrentCamera + Camera2 Interop) │
│  ├── Primary & Secondary PreviewView bindings                          │
│  └── Combined Video Recording Pipeline (Single MP4 + Sync Audio)       │
└────────────────────────────────────────────────────────────────────────┘
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Dual Vlog Domain State | StateFlows and methods for `DualVlogLayout` and `isDualStreamSwapped` in `CameraEngine` & `BaseCameraEngine` | M1 | survey |
| 2 | Concurrent Camera Management | Dual camera binding, fallback handling, and surface lifecycle management in `AndroidCameraEngine` | M1 | survey |
| 3 | Samsung-Style 50/50 Split View | Edge-to-edge 50% top and 50% bottom halves with 0 wasted bezel space, 1dp frosted divider, and center floating swap button | M2 | ORIGINAL_REQUEST §R1 |
| 4 | Clean Movable PiP Mode | Primary stream full screen, secondary stream in floating 16:9 / 4:3 rounded rectangle with 4-corner magnetic snap (TL, TR, BL, BR) and 1-tap swap | M2 | ORIGINAL_REQUEST §R2 |
| 5 | Top Director Control Island | Floating frosted glass capsule with layout toggle `[ 🌓 Split | 🔲 PiP ]`, stream swap `[ ⇄ Swap ]`, and tone filter button `[ ✨ Filter ]` | M2 | ORIGINAL_REQUEST §R3 |
| 6 | Viewfinder Clutter Auto-Hiding | Auto-suppression of framing grids, 3D horizon leveler, exposure zebra clipping, and focus peaking in Dual Recording mode | M2 | ORIGINAL_REQUEST §R3 |
| 7 | Synchronous Live Tone Filters | Real-time color grading across both camera feeds simultaneously for Real Tone, Vibrant, Cinematic Warm, Monochrome, and Natural | M3 | ORIGINAL_REQUEST §R3 |
| 8 | Inline Filter Drawer Integration | Quick-select drawer integrated with Director Island showing preview chips for 5 flagship tone profiles | M3 | ORIGINAL_REQUEST §R3 |
| 9 | Single Combined MP4 Recording | Composite layout (Split 50/50 or PiP) recorded into single high-definition MP4 file with synchronized microphone audio | M4 | ORIGINAL_REQUEST §R4 |
| 10 | MediaStore Export & Metadata | Recorded MP4 exported cleanly to `DCIM/AuraCam` with video resolution, duration, and thumbnail generation | M4 | ORIGINAL_REQUEST §R4 |
| 11 | Multiplatform Unit Test Suite | Comprehensive unit tests in `BaseCameraEngineTest` and UI test suite passing with 0 errors via `./gradlew testDebugUnitTest` | M5 | ORIGINAL_REQUEST §Verification |
| 12 | Physical Device Deployment | Debug APK build and deployment via ADB to Nothing Phone (2a) (`./gradlew :composeApp:installDebug`), verified via screen capture | M5 | ORIGINAL_REQUEST §Verification |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Domain & Dual Camera State | `shared` domain contracts, reactive state flows, `AndroidCameraEngine` concurrent lifecycle & swap bindings | none | PLANNED |
| M2 | Director Viewfinder UI | 50/50 Split View, Movable PiP with magnetic snapping, Director Control Island, and clutter auto-hiding | M1 | PLANNED |
| M3 | Synchronous Live Tone Filters | Color matrix / shader grading across both feeds simultaneously and inline drawer | M1, M2 | PLANNED |
| M4 | Single Combined MP4 Recording | Unified composite recording pipeline with synchronized microphone audio | M1, M2, M3 | PLANNED |
| M5 | E2E Verification & Device Deployment | Gradle unit tests, device build/install on Nothing Phone (2a), screencap validation | M1, M2, M3, M4 | PLANNED |

## Interface Contracts

### Domain Interface: `CameraEngine`
```kotlin
package com.auracam.camera.domain

enum class DualVlogLayout(val label: String) {
    SPLIT_50_50("Split 50/50"),
    PIP_RECT("PiP Window"),
    PIP_CIRCLE("PiP Circle"),
    SIDE_BY_SIDE("Side by Side")
}

interface CameraEngine {
    // Existing StateFlows...
    val dualVlogLayout: StateFlow<DualVlogLayout>
    val isDualStreamSwapped: StateFlow<Boolean>
    
    fun setDualVlogLayout(layout: DualVlogLayout)
    fun swapDualStreams()
}
```

### UI Composable Contracts
```kotlin
@Composable
fun DirectorDualRecordingOverlay(
    engine: CameraEngine,
    isRecording: Boolean,
    colorProfile: ColorProfile,
    onColorProfileSelected: (ColorProfile) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun DirectorControlIsland(
    layout: DualVlogLayout,
    onLayoutSelected: (DualVlogLayout) -> Unit,
    onSwapStreams: () -> Unit,
    activeFilter: ColorProfile,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

## Code Layout
- `shared/src/commonMain/kotlin/com/auracam/camera/domain/` — Domain models, enums, engine interfaces.
- `shared/src/androidMain/kotlin/com/auracam/camera/domain/` — Android CameraX + Camera2 engine implementation.
- `composeApp/src/commonMain/kotlin/com/auracam/ui/components/` — Viewfinder composables, Director overlay, PiP, Split view, Filter drawer.
- `composeApp/src/commonMain/kotlin/com/auracam/ui/screens/` — `ViewfinderScreen.kt` root stage and HUD layers.
- `shared/src/commonTest/kotlin/com/auracam/` — Multiplatform unit tests.
