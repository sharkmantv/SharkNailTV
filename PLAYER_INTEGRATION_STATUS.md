# Shark Player integration status

Implemented in this source revision:
- Playback & Display no longer advertises IJKPlayer, VLC, or mpv as uninstalled choices.
- Previously saved engine choices are migrated to Media3 on opening Playback & Display.
- SafePlayer always uses the embedded Media3 player, rather than attempting to open external applications.
- Existing Media3 1.11.1 HLS/DASH/SmoothStreaming/RTSP modules retained.

Not yet implemented or verified:
- Just Player FFmpeg extension (requires native decoder binaries, compatible packaging, and license review).
- Enhanced Just Player/Just+ TV remote and seek UI port.
- Android compilation or real-TV tests.
- Any new movie-link source.

GitHub references: https://github.com/moneytoo/Player ; https://github.com/royal-git/enhanced-just-player ; https://github.com/just-plus-player/just-plus-player
