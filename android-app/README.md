# ReturnGuard Android Basis (v0.1.0-beta)

Dieses Verzeichnis enthält den Startpunkt für die native Android-App.

## Enthalten

- Kotlin + Jetpack Compose App-Grundgerüst
- Lokale Datenhaltung mit Room (`PurchaseEntity`, `PurchaseDao`, `ReturnGuardDatabase`)
- Reminder-Check per WorkManager (`DueReminderWorker`)
- JSON Export/Import über Storage Access Framework
- ViewModel + Compose-Screen als erste nutzbare Struktur
- ML Kit Document Scanner Integration (Google Play Services Scanner)
- ML Kit Text Recognition (On-Device OCR)
- OCR-Entwurf: Datum, Preis, Händler und Produkt werden ins Formular vorbefüllt

## Nächste Schritte

1. Android Studio öffnen (`android-app` als Projekt)
2. Gradle Sync ausführen
3. App auf Emulator/Gerät starten
4. Smoke-Test durchführen: `docs/smoke-test-plan-week5-6.md`
5. Matrixlauf durchführen: `docs/smoke-test-matrix-v0.1.0-beta.md`
6. OCR-Heuristiken mit realen Belegen feinjustieren (verschiedene Layouts)
7. Roadmap abarbeiten: `docs/playstore-roadmap.md`
8. Release-Status: `docs/releases/v0.1.0-beta.md` + `../CHANGELOG.md`

## Hinweis

Für Android Gradle Plugin 8.5.x ist Java 17 erforderlich.
Lokaler Build-Befehl:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```
