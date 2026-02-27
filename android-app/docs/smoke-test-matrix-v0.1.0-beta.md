# Smoke-Test Matrix v0.1.0-beta

Date: 2026-02-27  
Target version: `v0.1.0-beta`

## Goal
- Validate OCR draft quality and stability across diverse receipts before closed testing.

## Dataset target
- 30 receipts minimum.
- Mix by quality:
  - 10 high quality (flat, bright, sharp)
  - 10 medium quality (phone scan, slight perspective)
  - 10 hard cases (noise, folds, shadows, logos-heavy)

## Pass criteria
- Product accuracy >= 85%
- Merchant accuracy >= 95%
- Purchase date accuracy >= 90%
- Price accuracy >= 92%
- No crash during scan -> draft -> save flow

## Execution table

| ID | Vendor/Layout | Image Quality | Product | Merchant | Date | Price | Confidence Overall | Confidence Price | Save Guard Triggered | Notes |
|---|---|---|---|---|---|---|---:|---:|---|---|
| 01 | Neureiter invoice (A4, logo-heavy) | high | OK | OK | OK | OK | 68 | 75 | No | Baseline control sample after confidence calibration |
| 02 |  |  |  |  |  |  |  |  |  |  |
| 03 |  |  |  |  |  |  |  |  |  |  |
| 04 |  |  |  |  |  |  |  |  |  |  |
| 05 |  |  |  |  |  |  |  |  |  |  |
| 06 |  |  |  |  |  |  |  |  |  |  |
| 07 |  |  |  |  |  |  |  |  |  |  |
| 08 |  |  |  |  |  |  |  |  |  |  |
| 09 |  |  |  |  |  |  |  |  |  |  |
| 10 |  |  |  |  |  |  |  |  |  |  |
| 11 |  |  |  |  |  |  |  |  |  |  |
| 12 |  |  |  |  |  |  |  |  |  |  |
| 13 |  |  |  |  |  |  |  |  |  |  |
| 14 |  |  |  |  |  |  |  |  |  |  |
| 15 |  |  |  |  |  |  |  |  |  |  |
| 16 |  |  |  |  |  |  |  |  |  |  |
| 17 |  |  |  |  |  |  |  |  |  |  |
| 18 |  |  |  |  |  |  |  |  |  |  |
| 19 |  |  |  |  |  |  |  |  |  |  |
| 20 |  |  |  |  |  |  |  |  |  |  |
| 21 |  |  |  |  |  |  |  |  |  |  |
| 22 |  |  |  |  |  |  |  |  |  |  |
| 23 |  |  |  |  |  |  |  |  |  |  |
| 24 |  |  |  |  |  |  |  |  |  |  |
| 25 |  |  |  |  |  |  |  |  |  |  |
| 26 |  |  |  |  |  |  |  |  |  |  |
| 27 |  |  |  |  |  |  |  |  |  |  |
| 28 |  |  |  |  |  |  |  |  |  |  |
| 29 |  |  |  |  |  |  |  |  |  |  |
| 30 |  |  |  |  |  |  |  |  |  |  |

## Summary block
- Product accuracy: `1 / 1` (29 pending)
- Merchant accuracy: `1 / 1` (29 pending)
- Date accuracy: `1 / 1` (29 pending)
- Price accuracy: `1 / 1` (29 pending)
- Crash count: `0`
- Go/No-Go: `pending (dataset incomplete)`

## Triage notes
- List top 3 recurring extraction errors with receipt IDs.
- Fix only high-frequency errors first; avoid template overfitting to one vendor.
