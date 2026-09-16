# Digga Counter

Android-App: hört zu, erkennt das Wort "Digga" per Android's eingebauter (kostenloser)
Spracherkennung, ordnet es per Picovoice Eagle (on-device Sprechererkennung) einer
trainierten Person zu und bucht ihr 50 Cent. Der Kontostand jeder Person lässt sich
zusätzlich manuell in 50-Cent-Schritten per +/- ändern.

## Einmaliges Setup

1. **Picovoice AccessKey** (für die Sprechererkennung)
   - Kostenlosen Account auf [console.picovoice.ai](https://console.picovoice.ai) anlegen
   - AccessKey kopieren, in `Config.kt` bei `PICOVOICE_ACCESS_KEY` eintragen

3. **GitHub-Repo für Auto-Update** — bereits erledigt
   - Code liegt in [github.com/benjaminstockey-a11y/digga-counter](https://github.com/benjaminstockey-a11y/digga-counter)
     (öffentlich, damit die App die Releases-API ohne Login abfragen kann)
   - Bei jedem Push auf `main` baut `.github/workflows/release-apk.yml` automatisch eine
     signierte APK und veröffentlicht sie als GitHub Release. Die App prüft beim Start,
     ob eine neuere Version verfügbar ist, und bietet Download + Installation an
     (Android verlangt dafür immer eine Bestätigung durch den Nutzer - kein stilles
     Auto-Install).
   - Der Signing-Keystore liegt **nicht** im Repo, sondern als vier verschlüsselte
     GitHub-Actions-Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
     `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) im Repo hinterlegt.

## Nutzung

1. App installieren, Mikrofon- und Benachrichtigungs-Berechtigung erteilen
2. Person per "+" anlegen
3. Über das Mikrofon-Icon bei der Person die Stimme trainieren (ein paar Sätze sprechen)
4. "Zuhören starten" - läuft als Hintergrunddienst weiter
5. Sagt eine trainierte Person "Digga", werden ihr automatisch 50 Cent gutgeschrieben
6. Kontostand jederzeit manuell mit "+50¢" / "−50¢" anpassen

## Technische Hinweise

- Die Spracherkennung nutzt Android's `SpeechRecognizer` - kostenlos, kein Google-Cloud-
  Account nötig. Für beste Ergebnisse und Datenschutz unter Android-Einstellungen →
  System → Sprachen → "Offline-Spracherkennung" das deutsche Sprachpaket installieren,
  dann läuft die Erkennung komplett auf dem Gerät. Ohne Sprachpaket fällt Android auf
  die normale (Internet-)Spracherkennung des Geräts zurück, ebenfalls kostenlos.
- Die Sprechererkennung läuft komplett offline auf dem Gerät (Picovoice Eagle) und nutzt
  dieselbe Audioaufnahme, die der SpeechRecognizer sowieso macht (`onBufferReceived`) -
  es läuft also nur eine Mikrofonaufnahme gleichzeitig.
