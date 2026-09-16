# Digga Counter

Android-App: hört zu, erkennt das Wort "Digga" per Android's eingebauter (kostenloser)
Spracherkennung, ordnet es per einer selbst gebauten, komplett kostenlosen On-Device-
Stimmerkennung einer trainierten Person zu und bucht ihr 50 Cent. Der Kontostand jeder
Person lässt sich zusätzlich manuell in 50-Cent-Schritten per +/- ändern.

Kein externer Account, kein API-Key, keine Cloud-Kosten für Spracherkennung oder
Stimmerkennung nötig - alles läuft direkt auf dem Gerät.

## Einmaliges Setup

**GitHub-Repo für Auto-Update** — bereits erledigt
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
3. Über das Mikrofon-Icon bei der Person die Stimme trainieren (ein paar Sätze sprechen,
   bis der Balken voll ist - ca. 5 Sekunden tatsächliches Sprechen)
4. "Zuhören starten" - läuft als Hintergrunddienst weiter
5. Sagt eine trainierte Person "Digga", werden ihr automatisch 50 Cent gutgeschrieben
6. Kontostand jederzeit manuell mit "+50¢" / "−50¢" anpassen

## Technische Hinweise

- Die Spracherkennung nutzt Android's `SpeechRecognizer` - kostenlos, kein Google-Cloud-
  Account nötig. Für beste Ergebnisse und Datenschutz unter Android-Einstellungen →
  System → Sprachen → "Offline-Spracherkennung" das deutsche Sprachpaket installieren,
  dann läuft die Erkennung komplett auf dem Gerät. Ohne Sprachpaket fällt Android auf
  die normale (Internet-)Spracherkennung des Geräts zurück, ebenfalls kostenlos.
- Die Stimmerkennung ist selbst gebaut (`AudioFeatures.kt`, `SpeakerIdentifier.kt`):
  pro Audio-Frame wird per FFT ein spektrales "Voiceprint"-Merkmal berechnet und beim
  Training zu einem Durchschnittsprofil je Person gemittelt. Zur Laufzeit wird jedes
  Frame per Kosinus-Ähnlichkeit mit allen gespeicherten Profilen verglichen. Das ist
  deutlich einfacher als ein trainiertes neuronales Sprecher-Embedding-Modell und daher
  etwas weniger präzise - für ein paar bekannte Personen im selben Haushalt reicht es
  aber gut aus. Bei Bedarf lässt sich der Schwellwert `matchThreshold` in
  `SpeakerIdentifier.kt` anpassen (höher = strenger, weniger Falscherkennungen; niedriger
  = großzügiger, erkennt leiser/undeutlicher gesprochene Wörter eher).
- Beide laufen über dieselbe Audioaufnahme, die der SpeechRecognizer sowieso macht
  (`onBufferReceived`) - es läuft also nur eine Mikrofonaufnahme gleichzeitig.
