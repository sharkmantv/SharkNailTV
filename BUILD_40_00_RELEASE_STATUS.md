# SharkNailTV Build 40.00 — source package

Implemented in the Android TV source:
- One centered Shark Streaming entry on Home; six services only inside its service browser.
- Netflix, Apple TV, Prime Video, Disney+, HBO Max and Hulu provider catalogue tiles. Three previously missing provider image resources are now included.
- Larger 2:3 portrait TMDB artwork on Home, with TV remote focus outlines.
- Nuvio QR code is in Settings → Home Playback Source → Nuvio Account, never Home. It encodes the real HTTPS verification URL returned by the Nuvio device-link service and retains the manual code fallback.
- Existing TV, Movies, Series and Sports Guide navigation retained.

## Build / runtime requirements
Open in Android Studio with Android SDK 36, then build the debug APK with `gradle assembleDebug` (or Android Studio Build APK). No compiled APK is included: this environment has no Android SDK/Gradle installed. Configure `tmdb.properties` with a valid TMDB API key for posters. Configure `nuvio.properties` with authorized public backend details for account linking; without these, Nuvio linking cannot operate. Streaming provider tiles browse TMDB catalogue availability and do not grant subscriptions or bypass DRM. Test on actual Android TV before treating as a production release.
