# SharkNailTV 19.0 development source — verification status

## Implemented
- Based on the SharkNailTV 18.5 Sharks Only source.
- Original outer SharkNailTV branding and intro assets unchanged.
- Original Sky Stream logo asset unchanged.
- Smaller middle sharks (weight 0.48; heights 44/72/94dp) with transparent padding and reduced low-alpha rectangular haze.
- Increased outer brand area (weight 1.18; heights 60/112/136dp).
- Fixed unresolved `SkyLikeFamily` reference by using Compose `FontFamily.SansSerif` (licensed system font).
- Version set to `19.0-dev`.
- Removed obsolete root-level historical build notes; kept current README, licensing, build scripts, resources, source, and dependencies.
- Magis integration not present; no Magis launcher/test button.

## Not implemented / not verified
- Nuvio integration and central addon sync not implemented.
- Direct TMDB Home movie/episode streaming not configured.
- Android compilation, runtime, layout, and remote-control tests NOT performed.
- The archive integrity test checks ZIP structure, NOT Kotlin compilation.

## Build prerequisites
Use a compatible Android SDK/JDK 17/Gradle environment. This source archive does not include a Gradle wrapper executable or wrapper JAR; use an installed Gradle or restore a legitimate wrapper. Do not put credentials into distributed APKs.
