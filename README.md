# Digga Counter

Android-App: hört zu, erkennt das Wort "Digga" per Google Speech-to-Text, ordnet es per
Picovoice Eagle (on-device Sprechererkennung) einer trainierten Person zu und bucht ihr
50 Cent. Der Kontostand jeder Person lässt sich zusätzlich manuell in 50-Cent-Schritten
per +/- ändern.

## Einmaliges Setup

1. **Google Cloud Speech-to-Text API-Key**
   - Projekt in der [Google Cloud Console](https://console.cloud.google.com) anlegen
   - "Cloud Speech-to-Text API" aktivieren
   - Unter *APIs & Services → Anmeldedaten* einen API-Key erstellen
   - In [`app/src/main/java/com/example/diggacounter/Config.kt`](app/src/main/java/com/example/diggacounter/Config.kt) bei `GOOGLE_SPEECH_API_KEY` eintragen

2. **Picovoice AccessKey** (für die Sprechererkennung)
   - Kostenlosen Account auf [console.picovoice.ai](https://console.picovoice.ai) anlegen
   - AccessKey kopieren, in `Config.kt` bei `PICOVOICE_ACCESS_KEY` eintragen

3. **GitHub-Repo für Auto-Update**
   - Dieses Projekt in ein **eigenes, privates** GitHub-Repo pushen (siehe unten)
   - In `Config.kt` bei `GITHUB_REPO` `"deinuser/digga-counter"` eintragen
   - Bei jedem Push auf `main` baut `.github/workflows/release-apk.yml` automatisch eine
     signierte APK und veröffentlicht sie als GitHub Release. Die App prüft beim Start,
     ob eine neuere Version verfügbar ist, und bietet Download + Installation an
     (Android verlangt dafür immer eine Bestätigung durch den Nutzer - kein stilles
     Auto-Install).

   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/DEIN_USER/digga-counter.git
   git push -u origin main
   ```

   Hinweis: `app/digga-release.keystore` ist absichtlich mit eingecheckt, damit jeder
   CI-Build mit demselben Schlüssel signiert wird (sonst schlägt die Update-Installation
   über eine bestehende Version fehl). Das ist nur für ein privates Repo zum
   Eigengebrauch gedacht - kein Ersatz für einen echten Play-Store-Signing-Key.

## Nutzung

1. App installieren, Mikrofon- und Benachrichtigungs-Berechtigung erteilen
2. Person per "+" anlegen
3. Über das Mikrofon-Icon bei der Person die Stimme trainieren (ein paar Sätze sprechen)
4. "Zuhören starten" - läuft als Hintergrunddienst weiter
5. Sagt eine trainierte Person "Digga", werden ihr automatisch 50 Cent gutgeschrieben
6. Kontostand jederzeit manuell mit "+50¢" / "−50¢" anpassen

## Technische Hinweise

- Die Spracherkennung läuft in ~3-Sekunden-Chunks über die Google-REST-API (nicht
  Streaming) - einfacher und robuster auf Mobilgeräten, aber mit spürbarer Latenz von
  ein paar Sekunden bis zur Buchung.
- Die Sprechererkennung läuft komplett offline auf dem Gerät (Picovoice Eagle).
- Der eingebettete Google-API-Key liegt im Klartext in der APK - für den reinen
  Eigengebrauch okay, für eine Veröffentlichung müsste man die Google-Anfrage über einen
  eigenen Server proxyen.
