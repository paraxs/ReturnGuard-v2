# Phase 2 Execution Guide

## Scope
- Fill the 30-receipt smoke matrix.
- Identify top 3 recurring OCR error classes.
- Apply targeted fixes only for those classes.

## Files
- Matrix: `docs/smoke-test-matrix-v0.1.0-beta.md`
- Error tracker: `docs/phase2-error-tracker.md`
- Summary tool: `tools/phase2_matrix_summary.py`

## Per-receipt workflow
1. Scan receipt in app.
2. Check OCR draft and debug candidates.
3. Mark matrix row:
   - `Product`, `Merchant`, `Date`, `Price` as `OK` or `FAIL`.
   - Confidence values from UI.
   - Save guard status.
   - Notes with short root-cause hint if failed.
4. Every 5 rows:
   - Run summary:
     - `python tools/phase2_matrix_summary.py`
   - Update top 3 in `phase2-error-tracker.md`.

## Fix policy
- Only fix classes with highest frequency first.
- Avoid one-off vendor hardcoding unless a class is truly vendor-specific and frequent.
- After each fix, rerun at least 5 receipts including prior failures.

## Done criteria (Phase 2)
- 30 rows completed.
- Top 3 classes fixed or mitigated.
- Accuracy targets reached or gap documented with next actions.
