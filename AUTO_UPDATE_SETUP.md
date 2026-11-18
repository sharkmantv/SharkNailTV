# SharkNailTV app updater — GitHub Releases

The Android TV app checks the **public** `sharkmantv/SharkNailTV` latest GitHub release at launch, at most once every 24 hours. When a release tag has a higher version code and includes `SharkNailTV.apk`, it asks permission to download and install the update. Android requires user approval to install it. This is NOT TV operating-system firmware updating.

## First-time setup
1. Upload the **contents** of this project directory to the root of `sharkmantv/SharkNailTV` (so `app/`, `settings.gradle.kts`, and `.github/` are at the repository root).
2. In GitHub Settings > Secrets and variables > Actions, configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_CERT_SHA256`. The keystore must be the **same signing identity as the currently installed app** or Android will reject the upgrade. Do not commit secrets or the keystore.
3. Optionally configure `TMDB_API_KEY` and `SPORTS_API_KEY`.
4. The included app is versionName `3.0` and versionCode `30000`. To publish a new update, increase both values in `app/build.gradle.kts` (for example versionName `3.1`, versionCode `30100`), commit, and push a matching tag such as `v3.1`. The tag's version is translated to versionCode using major*10000 + minor*100 + patch. Keep each component between 0 and 99.
5. GitHub Actions will build, sign, verify, and publish the `SharkNailTV.apk` release asset. The `workflow_dispatch` option builds an APK but **does not publish** a release.
6. On Android TV, open SharkNailTV while online; accept the update prompt, and grant the per-app unknown-apps permission if requested. If permission was needed, reopen the app after enabling it.

## Important limitations
- The repo/releases must be publicly accessible for this updater (no GitHub API authentication is embedded in the app).
- Android user confirmation is mandatory; there is no silent installation or TV firmware flashing.
- GitHub release publication requires the repository's Actions permissions to allow writing contents.
- Builds and installation have **not been verified** in this environment. Run GitHub Actions and test on an Android TV before distributing.
- Existing installation signing certificate and package ID `com.example.xtreamtv` must match.
- APK downloads are delivered via GitHub HTTPS and installed by Android's package installer. Only publish trusted signed releases.
