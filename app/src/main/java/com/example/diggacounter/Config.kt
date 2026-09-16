package com.example.diggacounter

/**
 * Fill these in before building. Both are prototype-only secrets embedded in the app —
 * fine for personal use, but never ship an app with a raw API key like this to the Play
 * Store, since anyone can extract it from the APK. For a public release, proxy Google
 * Speech-to-Text through your own backend instead.
 */
object Config {
    // Create a free AccessKey at https://console.picovoice.ai
    const val PICOVOICE_ACCESS_KEY = "PUT_YOUR_PICOVOICE_ACCESS_KEY_HERE"

    // The word/phrase that triggers a charge. Matched case-insensitively against
    // the Google transcript.
    const val TRIGGER_WORD = "digga"

    // "owner/repo" of the public GitHub repo that .github/workflows/release-apk.yml
    // publishes a new APK release to on every push to main. The app polls this repo's
    // latest release to auto-update itself.
    const val GITHUB_REPO = "benjaminstockey-a11y/digga-counter"
}
