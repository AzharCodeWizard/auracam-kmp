# E2E Test Infra: AuraCam Director-Style Dual Recording

## Test Philosophy
- Multi-tier requirement-driven verification covering domain state, UI layout geometry, tone filter mathematical matrices, video composition, and physical device deployment.
- Methodology: Unit test validation + ADB physical device verification on Nothing Phone (2a).

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Dual Vlog Domain State & Stream Swap | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | Samsung-Style 50/50 Split View Geometry | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 3 | Movable PiP Mode & Magnetic Corner Snapping | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 4 | Director Control Island & Clutter Hiding | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 5 | Synchronous Live Tone Filters | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 6 | Single Combined Video Recording & Audio Sync | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |

## Test Architecture
- Unit test runner: `./gradlew testDebugUnitTest` and `./gradlew :shared:desktopTest`
- Physical device deployment: `./gradlew :composeApp:installDebug` -> ADB launch `MainActivity` on Nothing Phone (2a) -> screen capture validation.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Vlog Mode Transition: Switch to DUAL_VLOG -> Verify 50/50 Split active, clutter hidden | F1, F2, F4 | Medium |
| 2 | Stream Interchange: Tap swap button -> Verify top/bottom streams swap | F1, F2 | Low |
| 3 | PiP Drag & Snap: Switch to PiP -> Drag PiP card -> Verify spring snap to TR, TL, BR, BL | F3, F4 | High |
| 4 | Synchronous Tone Grade: Open Filter Drawer -> Select Cinematic Warm -> Verify both streams graded | F4, F5 | Medium |
| 5 | Dual Video Recording: Start recording in Split mode -> Toggle PiP -> Stop recording -> Verify single MP4 with audio | F1, F2, F3, F5, F6 | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥5 realistic application scenarios
