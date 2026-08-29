# Orchestration Plan: AuraCam KMP Material 3 Expressive Overhaul

## Goal
Implement a comprehensive Google Pixel Material 3 Expressive design overhaul, fluid spring motion & tactile micro-interactions, pro controls & gallery polish, and verify via compilation, desktop test suite, and physical Android device deployment with UI rendering verification.

## Phases
1. **Phase 0: Survey & Discovery**
   - Explorer 1: UI / Theme / M3 Expressive tokens / Viewfinder overlay / typography / frosted glass blur styling.
   - Explorer 2: Gesture interactions / spring animations / mode carousel / zoom dial / dual exposure sliders / pro controls bottom sheet transitions.
   - Explorer 3: Pro controls sheet, histogram graph computation & rendering, gallery viewer EXIF cards, sharing, and build/test/device deployment pipeline.
2. **Phase 1: Project Scope & Architecture (PROJECT.md)**
   - Consolidate Feature Inventory with explicit mappings.
   - Define module interfaces, contracts, and code layout.
3. **Phase 2: Milestone Execution & Verification Loop**
   - Sub-orchestrators / workers per milestone.
   - Verification via Reviewer, Challenger, and Auditor.
4. **Phase 3: Final Verification & Device Deployment**
   - `./gradlew :shared:desktopTest`
   - `./gradlew :composeApp:installDebug`
   - Android device launch & UI screenshot capture verification.
5. **Phase 4: Synthesis & Human Report**
