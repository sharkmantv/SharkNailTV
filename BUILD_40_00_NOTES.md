# SharkNailTV 40.00 development source

Changes from supplied 30.30 source:
- Home TMDB artwork now uses portrait 2:3 poster images rather than tiny 16:9 backdrop thumbnails.
- Home artwork rows use larger cards and the Shark Streaming hero tile is centered.
- Nuvio device-link screen shows a scannable QR encoding the verification_uri_complete returned by the server, with a fallback manual device code.
- Version metadata set to 40.00-dev.

Important: QR sign-in still requires working Nuvio device-link server configuration and successful account approval. The QR does not bypass authentication. TMDB requires a valid key and network access. This is source code, not a tested APK; Android Gradle compilation/device QA is still required.
