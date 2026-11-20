# High-resolution TMDB artwork update

Updated `app/src/main/java/com/example/xtreamtv/MainActivity.kt` to request TMDB's `original` image size rather than `w780` posters, `w1280` backdrops, and `w300` logos. Applies to TMDB discovery, provider catalogue, and title-matching artwork.

## Build
Use the repository's existing GitHub Actions APK workflow or Android Studio to build the modified project. The supplied APK is an existing binary and has not been rebuilt or signed here.

## Important
`original` requests the largest image TMDB serves; it cannot increase the resolution of a low-resolution source poster. It can also increase bandwidth, loading time, and cache usage. Existing cached images may persist until refreshed or the app cache is cleared. Non-TMDB provider artwork is unchanged.
