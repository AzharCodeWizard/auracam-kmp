# Orchestration Plan: Samsung Galaxy Director-Style Dual Recording (Vlog Mode)

## Objectives
Implement and thoroughly verify Director-style Dual Recording in AuraCam:
- **R1: 50/50 Split View**: Edge-to-edge halves (50% top, 50% bottom), 0 wasted bezel space, minimal floating swap button.
- **R2: Movable PiP Mode**: Primary full-screen, secondary in 16:9 / 4:3 rounded rectangle floating window with magnetic corner snapping (TL, TR, BL, BR), 1-tap swap.
- **R3: Top Director Control Island & Live Tone Filters**: Floating frosted glass capsule with layout toggle, stream swap, live tone filters (Real Tone, Vibrant, Cinematic Warm, Monochrome, Natural) applied synchronously across both feeds, auto-hiding non-essential clutter.
- **R4: Single Combined Video Recording**: Combined visual layout (Split 50/50 or PiP) rendered/recorded into single MP4 with synchronized mic audio.
- **Verification**: `./gradlew testDebugUnitTest` passing 100%, build & deploy to Nothing Phone (2a) via ADB (`./gradlew :composeApp:installDebug`), run/verify.

## Phases
1. **Phase 0: Survey & Architecture Analysis**
   - Explorer 1: Camera capture pipeline (Android Camera2/CameraX/multi-camera support, concurrent cameras, dual stream capture).
   - Explorer 2: UI Viewfinder layout (Compose Multiplatform viewfinder, 50/50 split, PiP gestures & magnetic snapping, Director Control Island, tone filters, clutter hiding).
   - Explorer 3: Video recording & composite rendering pipeline (MediaRecorder / MediaCodec / OpenGL / Surface composition for single combined MP4 recording).
2. **Phase 1: Project Plan & Specification**
   - Synthesize survey reports into `PROJECT.md` with Feature Inventory and Interface Contracts.
   - Establish E2E Testing plan and test infrastructure.
3. **Phase 2: Milestone Execution**
   - M1: Camera Engine & Dual Camera Session State.
   - M2: Director Viewfinder UI & Floating Island Controls.
   - M3: Synchronous Live Tone Filter Pipeline.
   - M4: Combined Video Recording & Audio Muxing.
   - M5: End-to-End Test Suite & Verification.
4. **Phase 3: Final Verification & Device Deployment**
   - Unit tests run and pass.
   - Deploy debug APK to Nothing Phone (2a) via ADB and verify execution.
