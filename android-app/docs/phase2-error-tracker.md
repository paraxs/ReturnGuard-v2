# Phase 2 Error Tracker

Last update: 2026-02-27

## Goal
- Track recurring OCR errors from the 30-receipt matrix.
- Fix only the top 3 error classes by frequency.

## Error classes

| Rank | Error class | Frequency | Affected IDs | Severity | Status | Planned fix |
|---|---|---:|---|---|---|---|
| 1 | pending | 0 | - | - | open | - |
| 2 | pending | 0 | - | - | open | - |
| 3 | pending | 0 | - | - | open | - |

## Entry rules
- Count only real extraction mistakes (not manual user edits).
- Group by root cause, not by vendor.
- Prefer parser-level fixes over vendor-specific hardcoding.

## Update cadence
- Update this file after every 5 new receipts.
- Re-rank top 3 classes when frequencies change.
