# SharkNailTV 30.30 development source

Changes from 30.0 Stable Navigation:
- Home streaming-service text buttons replaced with one Shark Streaming tile.
- Shark Streaming opens a dedicated hub with Netflix, Apple TV+, and Prime Video service tiles.
- Service pages query TMDB discover API with provider IDs 8, 350, 9 and UK watch region; no longer reuse generic trending rows.
- All/Movies/Series/New Releases filters; New Releases uses TMDB release-date sort (not official top-10 rankings).
- Service posters use the existing addon-based stream lookup and internal player.
- Main app text wrapper uses SkyLikeFamily rather than overriding with generic SansSerif.
- Existing stable Home background, original logo image assets, Xtream IPTV and Nuvio account code preserved.

LIMITATIONS:
- Service cards use styled wordmarks, NOT licensed official logo artwork.
- Provider availability is TMDB metadata, not proof of a playable source or subscription access.
- Nuvio account linking requires a functioning backend and user authorization.
- No Android SDK/Gradle build or on-TV tests performed here; source-only development build.
