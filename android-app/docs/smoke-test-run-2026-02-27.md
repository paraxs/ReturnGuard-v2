# Smoke-Test Run (2026-02-27)

## Build status
- `:app:assembleDebug` passed.
- `:app:lintDebug` passed.

## Focus scenario
- Real invoice scan (Neureiter layout) on physical Android device.

## Observed extraction result (final iteration)
- Product: correct (`Erika85 Unterflurzugsäge`)
- Merchant: correct (`Neureiter Maschinen GmbH`)
- Purchase date: correct (`2024-07-09`)
- Price: correct (`3872,12`)

## Confidence snapshot
- Product: `98%`
- Merchant: `92%`
- Purchase date: `68%`
- Price: `75%`

## Notes
- Confidence false-warning rate for date/price reduced significantly after calibration.
- OCR debug panel remains enabled for ongoing parser triage.
