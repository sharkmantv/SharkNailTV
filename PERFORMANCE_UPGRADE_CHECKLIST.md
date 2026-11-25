# SharkNailTV — consolidated performance and UI upgrade checklist

Priority gate: restore OTA publishing first. Do not publish an incomplete upgrade or overwrite known-good releases. Retain all verified fixes, package ID, signing certificate, and increasing version code. Use the original archive plus the reviewed patch chain as build source; do not mix older builds.

## Playback / ExoPlayer
- [ ] Audit current Media3 modules and compatible releases; keep all modules on the same version. Compare beta against stable on the same provider.
- [ ] Build or obtain a license-compliant FFmpeg audio extension matching the chosen Media3 version; verify native ABIs, audio codecs, renderer linkage and runtime compatibility.
- [ ] Audit player lifecycle and reuse across live channel changes, preview and fullscreen transitions; prevent duplicate active streams and release resources when leaving.
- [ ] Measure first-frame time, channel changes, buffering, dropped frames, decoder initialization, memory and recovery; use Media3 analytics / PlaybackStatsListener with no credentials or stream URLs logged.
- [ ] Evaluate decoder prewarming, Media3 PreloadManager and compatible reuse strategies only where measurements justify them and the provider's connection limits permit.
- [ ] Test hardware tunneling on compatible hardware, audio tracks, subtitles, HDR/SDR, frame-rate switching and fallback; retain safe defaults if worse.
- [ ] Compare OkHttp with Cronet where useful, particularly for the user's HTTP provider; do not assume HTTP/3 benefits on HTTP streams.
- [ ] Evaluate software AV1/VP9 extensions only for real compatibility gaps; avoid heavy software video decoding by default.
- [ ] Keep native fullscreen buffer policy and validated preview settings unless controlled tests demonstrate a better policy.

## Xtream and catalogue
- [ ] Evaluate Room/SQLite indexed storage, migrations and account isolation; keep cache-first catalogue refresh and offline/stale behavior clear.
- [ ] Test Paging 3 or equivalent lazy loading, indexed search, bounded memory, request coalescing and cancellation.
- [ ] EPG cache, time handling, bounded background refresh and invalidation.
- [ ] Dead-channel feedback and recovery without endless retry or extra connections.
- [ ] Benchmark against IBO with the same authorized provider, device and channel set.

## UI and app performance
- [ ] Finish and verify the Movies/Series sidebar, vertical poster grid, search, sorting, fixed focus dimensions and D-pad navigation; retain Home layout, branding and current Live TV UI.
- [ ] Audit animations, image decoding, recomposition, scrolling and RAM/CPU/GPU overhead.
- [ ] Evaluate Baseline Profiles / Macrobenchmark startup and navigation; measure release build rather than claim an untested speedup.
- [ ] Review dependencies, obsolete resources, background jobs and cleanup; avoid unnecessary libraries.
- [ ] Verify compilation, signing certificate compatibility, application ID, monotonic version, APK integrity and emulator install/launch. Device playback, performance and UI testing remain separate acceptance gates.
- [ ] Publish one consolidated OTA only after checks; verify the release assets and in-app update path, record remaining device-only tests honestly.

Status at creation: 4.0.8 is latest OTA; 4.0.9 APK compiled, signed and emulator-tested, but release recovery failed HTTP 403. 4.1.0 catalogue UI changes are committed but failed at patch stage. This checklist is not evidence that any pending item has been implemented.
