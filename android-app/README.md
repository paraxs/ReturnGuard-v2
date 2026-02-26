# ReturnGuard Android Basis (Woche 1-4)

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
4. OCR-Heuristiken mit realen Belegen feinjustieren (verschiedene Layouts)
5. Danach Woche 5-6: Stabilisierung, Telemetrie, Play-Store-Readiness

## Hinweis

In dieser Umgebung konnte kein Gradle-Build abgeschlossen werden, weil `java.exe` nur Java 8 liefert.
Für Android Gradle Plugin 8.5.x ist Java 17 erforderlich.
