# SharkNailTV 20.6 development source

Based on 20.5, with original outer logos, Sky Stream logo and no middle sharks.

New code:
- Nuvio TV device link RPC start_device_login_session, poll_tv_login_session
- Approved device token exchange via tv-logins-exchange
- Account refresh-token renewal, explicit sign out
- Profile 1 addon pull from authenticated addons table, enabled/HTTPS filtering
- TMDB Home searches account addon URLs, without manual addon URL entry
- UI displays linking code and verification URL when backend configured

LIMITATIONS:
- Nuvio public backend URL and publishable key are not present in the supplied source and MUST be provided in nuvio.properties before compiling. This package cannot link an account as-is.
- The supplied Nuvio source confirms the RPC names and response structures, but server permission, actual authentication and data access have NOT been tested.
- Account profile ID hardcoded to 1; profile selection is not implemented.
- No TMDB->IMDb ID conversion; addons requiring IMDb IDs may return no results.
- Account tokens stored in Android private SharedPreferences, not encrypted; security hardening needed.
- This is source-only, not Android-compiled or device-tested. Do not present as working playback.
- Only use legally authorized addon sources.
