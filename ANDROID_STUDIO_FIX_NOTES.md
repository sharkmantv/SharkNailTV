# SharkNailTV 30.0 — source repair for Android Studio

## Fixed in this ZIP
- Repaired malformed literal `\n` sequences in Kotlin import statements of NuvioCompatibleAddon.kt. Those were a definite Kotlin syntax error in the previous 30.0 ZIP.
- Repaired coroutine builder calls/imports in the parallel addon search.
- Corrected stale Gradle root project name from 14.4 to 30.0.
- Removed a nested unbounded vertical-scroll container in the service preview screen.

## Important: IDE internal error is separate
Your photo shows an IntelliJ/Android Studio internal Gradle-plugin exception (ProcessCanceledException). This project ZIP cannot guarantee a fix for an IDE crash. Do not disable the Gradle plugin. If it recurs, use File > Invalidate Caches / Restart, and inspect the Build output for actual compiler errors.

## Verification
ZIP structure/integrity and static source checks only. No Android SDK/Gradle build or device testing performed. QR pairing, provider filtering, Nuvio account sync, and playback are not validated. This is not a tested APK.
