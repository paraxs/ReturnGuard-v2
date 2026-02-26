# ReturnGuard Android Smoke-Test (Woche 5-6)

Datum: 2026-02-26

## Ziel
- Schnelltest der Kernfunktionen nach Umbau auf Android-App-Struktur.
- Fokus auf reale User-Flows: `OCR`, `Scan`, `Import`, `Notification`, `Offline`.

## Setup
- Build: `:app:assembleDebug` muss erfolgreich sein.
- Testgerät: Android 10+ (empfohlen Android 13+ für Notification-Permission-Test).
- Mindestens 1 echter Rechnungsbeleg (Foto oder PDF-Screenshot).

## Testfälle
1. OCR-Scan mit Beleg
- Schritt:
  - App starten.
  - `Beleg scannen (OCR)` tippen.
  - Beleg aus Galerie wählen oder Kamera verwenden.
  - Nach OCR den vorbefüllten Dialog prüfen.
- Erwartung:
  - OCR läuft ohne Absturz.
  - Dialog öffnet sich automatisch.
  - `Datum` und `Preis` sind plausibel.
  - `Produkt` und `Händler` sind vorbefüllt (dürfen manuell korrigiert werden).

2. OCR-Übernahme in Liste
- Schritt:
  - Vorbefüllten Dialog prüfen/ggf. korrigieren.
  - `Speichern`.
- Erwartung:
  - Neuer Eintrag erscheint sofort in der Liste.
  - Kartenwerte (`Kaufdatum`, `Rückgabe bis`, `Garantie bis`, `Preis`) sind konsistent.

3. JSON Export/Import
- Schritt:
  - 2-3 Einträge anlegen.
  - `Export` ausführen und Datei speichern.
  - App-Daten löschen oder auf leerem Zustand testen.
  - `Import` mit exportierter Datei ausführen.
- Erwartung:
  - Import erfolgreich ohne Crash.
  - Einträge sind wieder vorhanden.
  - Keine offensichtlichen Duplikat-/ID-Konflikte.

4. Reminder/Notification
- Schritt:
  - Einen Eintrag mit kurzer Frist anlegen (z.B. Kaufdatum heute, Rückgabe 0-1 Tage).
  - Bei Android 13+ Notification-Permission erlauben.
  - `Reminder` Button ausführen.
- Erwartung:
  - Kein Absturz.
  - Reminder-Worker läuft an.
  - Notification erscheint (abhängig von Geräte-Einstellungen).

5. Offline-Verhalten
- Schritt:
  - Flugmodus aktivieren.
  - App neu starten.
  - Liste öffnen, Eintrag hinzufügen, bearbeiten, archivieren.
- Erwartung:
  - Kernfunktionen laufen weiter offline.
  - Daten bleiben nach App-Neustart erhalten.

## Abnahmekriterien (Go/No-Go)
- Go:
  - Kein Crash in allen 5 Testfällen.
  - OCR liefert für 2 von 3 Testbelegen verwertbare Drafts.
  - Import/Export und Reminder funktionieren stabil.
- No-Go:
  - Reproduzierbare Abstürze.
  - Datenverlust nach Neustart.
  - Reminder oder Import/Export brechen regelmäßig ab.
