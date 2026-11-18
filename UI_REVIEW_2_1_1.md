# SharkNailTV UI review — 2.1.1 source patch

Actual source changes in `MainActivity.kt`:
- Home TMDB poster width now depends on available horizontal space as well as vertical space, with 76–132 dp bounds. Previously it depended only on height, which could yield inconsistent poster density.
- Shark Streaming provider cards are arranged in one responsive row of six compact landscape tiles (74–136 dp wide).
- Removed the locally bundled pseudo-3D provider-card bitmaps from the provider tile rendering; use crisp branded text on a navy gradient with one square blue frame, gold on focus.
- Home Shark Streaming entry tile is reduced to 142 × 72 dp.
- Sidebar focused border corner radius is now square.
- Shark Streaming heading/spacing tightened.

Unchanged: Nuvio QR account implementation, PenguPlay, source selection and playback logic.

Verification: source was inspected and ZIP can be integrity-tested. No Android SDK or Gradle wrapper was present in this environment, so no compile or on-device visual test was performed. This is NOT a finished APK or comprehensive screen-by-screen visual verification.
