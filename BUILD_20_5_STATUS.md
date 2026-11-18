# SharkNailTV 20.5 Nuvio integration foundation

Based on 20.0 No Middle Sharks; preserves original logos and intro.

Implemented: account-sync data model, device-link gateway interface (not connected),
multiple-addon stream resolver, individual addon failure isolation, 20.5-dev version.

Not implemented: Nuvio account sign-in, phone addon syncing, QR pairing,
TMDB-to-IMDb mapping, bundled emulator cores, or verified movie/episode playback.

Nuvio source contains RPC names start_device_login_session and poll_tv_login_session,
and profile-scoped addon records; connecting a third-party client requires verified
service configuration, authentication, permission and secure session handling.
No Nuvio credentials or private keys are embedded.

Not compiled or tested on Android TV. Source ZIP only.
