# Changelog

All notable changes to this project are documented in this file.

## [0.1.0-beta] - 2026-02-27

### Added
- Native Android app foundation with Kotlin + Jetpack Compose (`android-app`).
- Local persistence via Room and reminder checks via WorkManager.
- JSON export/import flow for local backup and restore.
- OCR pipeline with ML Kit Document Scanner + Text Recognition.
- OCR draft dialog with confidence scoring and field-level confidence.
- Low-confidence save guard (`<55%`) with explicit user confirmation for product and price.
- OCR debug panel with raw text and ranked candidates for product/merchant/date/price.
- Smoke-test plan document and Play-Store roadmap document.
- Android CI workflow (assemble + lint + unit tests) via GitHub Actions.

### Changed
- Product extraction now uses ranked multi-candidate selection and stronger rejection of contact/header lines.
- Price extraction hardened for mixed formats and OCR noise.
- Confidence model calibrated to reduce false warnings on clear receipts, especially date and price.

### Fixed
- Product misclassification from phone/fax/contact lines.
- Price outlier selection fallback (e.g. inflated totals caused by OCR substitutions).
- Multiple OCR parsing regressions around invoice drafts.

### Known limitations
- OCR quality still varies on noisy images and unusual receipt layouts.
- Confidence values are heuristic and require continued calibration with real-world samples.
- Automated parser tests are still limited; broader snapshot test coverage is planned.
