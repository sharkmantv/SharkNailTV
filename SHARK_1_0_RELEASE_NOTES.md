# SharkNailTV 1.0 — Android TV source candidate

Based on the user's SharkNailTV 40.00 Neon UI project.

- VersionName changed to 1.0, versionCode 10000.
- Unified existing gold/yellow focus outlines into electric cyan in MainActivity and PremiumControls. Existing Compose focus pulse animations remain active on cards, posters, navigation, and sidebars.
- Preserves existing Home composition, Sky Stream branding, and central Shark Streaming entry.
- Existing Netflix, Apple TV+, Prime Video, Disney+, HBO Max, Hulu services remain inside Shark Streaming.
- Existing Nuvio QR linking remains under Settings > Home Playback Source, not on Home. QR requires configured Nuvio backend and an actual verification URL.
- TMDB poster requests and existing responsive poster cards preserved. TMDB API configuration is required for real artwork.

## Verification / limitations
Source package only: not compiled or tested on an Android TV in this environment. Not a signed APK. Screenshots/mockups are design references, not verified runtime captures. Validate Gradle compilation, Android TV D-pad navigation, streaming-service asset resources, QR scan flow, TMDB API, and real IPTV credentials before distribution.
