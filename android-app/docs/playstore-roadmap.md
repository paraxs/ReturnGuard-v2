# ReturnGuard Play-Store Roadmap

Last update: 2026-02-27

## Phase status
- [x] Phase 1: Android baseline + OCR draft flow
- [ ] Phase 2: Smoke-test matrix and parser stabilization
- [ ] Phase 3: UX hardening and editing ergonomics
- [ ] Phase 4: QA automation and release engineering
- [ ] Phase 5: Play-Store compliance and closed testing
- [ ] Phase 6: Launch candidate

## Phase 1 (done)
- Compose app structure, Room, WorkManager, import/export.
- OCR scanner + recognition.
- Confidence model + save guard + debug panel.
- Baseline release `v0.1.0-beta`.

## Phase 2 (active next)
- Run 30-receipt matrix and collect field metrics.
- Rank top parser failure classes by frequency.
- Apply only high-impact parser fixes.

## Phase 3 (UI/UX)
- Date picker for purchase date.
- Price field with numeric formatting and validation hints.
- Keep OCR debug behind explicit toggle.
- Styling milestone:
  - Implement dedicated visual pass with a **liquid-glass** design language.
  - Include translucent layered surfaces, depth blur, highlight gradients, and motion polish.
  - Must remain performant on mid-tier Android devices.

## Phase 4 (quality)
- Add parser unit tests with snapshot OCR fixtures.
- Add CI gate (assemble + lint + tests).
- Validate no regressions before each beta increment.

## Phase 5 (store readiness)
- Privacy policy and Data Safety details.
- App signing and release AAB checks.
- Internal track -> closed track progression.

## Phase 6 (release)
- Go/No-Go gate:
  - No blocker crashes
  - OCR field metrics at target levels
  - Reminder/import/export stable
- Publish production track.
