# SharkNailTV 30.0 — Nuvio integration candidate (SOURCE ONLY)

The official Nuvio self-host project publishes a public client backend URL
https://api.nuvio.tv and publishable key. These are now build defaults, with
nuvio.properties overrides for authorised deployments. This is a public
publishable key, NOT a secret/service-role credential.

Source includes TV device-link start, polling, token exchange, refresh,
profile-1 addon list retrieval and TMDB Home stream lookup.
It retains original outer logos and no middle sharks.

IMPORTANT — NOT FULLY INTEGRATED / NOT TEST READY:
- This source was not compiled or run on an Android TV.
- No live device login, addon sync or playback was verified.
- Nuvio's active profile must be resolved rather than assuming profile 1.
- QR graphic not yet implemented; pairing URL/code UI only.
- Stream addon identifier compatibility (TMDB vs IMDb) incomplete.
- Token storage needs security hardening.
- Nuvio API access permissions and project licensing must be verified.
- No APK is provided.

This checkpoint must not be represented as fully working Nuvio integration.
