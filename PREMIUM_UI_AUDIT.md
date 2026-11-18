# SharkNailTV 2.1 — UI audit and source changes

This is a source-level pass, not a validated APK.

## Changed
- Home hero reduced and poster row sizing adjusted for compact TV layouts.
- Shark Streaming provider tiles reduced to 150 x 82 dp, with uncropped (Fit) artwork.
- Square-corner geometry applied across the primary screen's remaining 8/12/14 dp rounded components.
- Consistent yellow focus substituted for cyan/white on navigation, sports and related focus elements.
- Reduced oversized border/padding on reusable focus frame and streaming cards.

## Source review
- Home, Movies, TV Series, Shark Streaming, provider catalogues, Sports Guide, Live TV and Settings are implemented in MainActivity.kt.
- PlaybackSettings.kt, NuvioTvAccount.kt, PenguPlay.kt and Nuvio addon integration remain present and unmodified.

## Unverified / not claimed fixed
- No Android SDK / Gradle runner was available in this environment; compilation and device rendering were not validated.
- Exact TV scaling, image quality, D-pad navigation and all focus paths require device testing.
- Source-level adjustments cannot establish that every screen is visually perfect.
