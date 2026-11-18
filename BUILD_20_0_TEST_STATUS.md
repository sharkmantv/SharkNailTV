# SharkNailTV 20.0-dev — Nuvio-compatible Home test and retro groundwork

## Implemented
- Preserved original SharkNailTV and Sky Stream artwork and smaller 19.0 middle sharks.
- Added configurable HTTPS addon URL to Home Playback Source settings.
- TMDB Home movie/episode search now requests `/stream/movie/{id}.json` or `/stream/series/{id}:S:E.json` from that addon, using `tmdb:` identifiers.
- Displays playable HTTP(S)/RTSP stream URLs returned by the addon in the existing Shark Player selector.
- Uses no Xtream Codes for Home lookup; separate IPTV sections remain intact.
- Added retro game catalogue model and common extension detection as an integration starting point.
- No Magis integration or separate Nuvio app launcher.

## Important limitations
- This is a minimal Nuvio-compatible addon protocol client, NOT embedded Nuvio account/cloud syncing.
- Addon must accept `tmdb:` IDs. Many addons require IMDb `tt...` IDs, so they may return no streams until proper ID mapping is implemented.
- No bundled emulator cores, game ROMs, game-launching screen, or ready-to-play gaming yet.
- No QR linking or administrator addon auto-update service yet.
- User must supply an authorised addon URL; none are bundled.
- No Android SDK/Gradle compilation or physical TV playback test has been performed.
- Kotlin/Compose integration still requires compilation and runtime verification.

## Manual testing
1. Build and install development source using Android Studio and Android SDK.
2. Open Settings > Home Playback Source, enter an authorised HTTPS addon base URL, Save.
3. Open TMDB Home movie, choose Find movie streams. If a source is returned, select it.
4. Repeat for a series season and episode. Record any lookup or playback error.
5. Test Xtream sections and header to confirm they remain unchanged.
