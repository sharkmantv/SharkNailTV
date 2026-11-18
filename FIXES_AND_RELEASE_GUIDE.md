# SharkNailTV repair and release guide

## Included repairs
- Added a **Check for App Update** button in Settings. Manual checks bypass the daily automatic-check limit and report no update or connectivity failures.
- Declared AndroidX Core explicitly for FileProvider.
- Added early signing-secret validation to GitHub Actions.
- Preserved the existing Android application ID, version, and release signing requirement.

## Deploy
1. Extract this ZIP and upload its **contents** to the root of `sharkmantv/SharkNailTV`, preserving `.github/workflows/build-release.yml`. A ZIP uploaded as a single file is not an Android project.
2. In repository Settings > Secrets and variables > Actions set `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_CERT_SHA256` using the **original signing key**. Do not upload your private keystore to the repository.
3. Build using Actions > Build and publish SharkNailTV > Run workflow. A manual run builds an artifact but does not publish a GitHub Release.
4. For an OTA app update, increase `SHARK_VERSION_CODE` and `SHARK_VERSION_NAME` (or their defaults in `app/build.gradle.kts`), then push a matching version tag (`v3.1` for versionName 3.1 / code 30100). This publishes a release asset named `SharkNailTV.apk`.
5. On the Android TV, open Settings > Check for App Update. The Android package installer will ask for permission; this is **not device firmware**.

## Unverified / blocking issues
- This package has not been compiled on an Android SDK/Gradle environment, and no successful GitHub Actions run has been confirmed.
- The original signing keystore is required for in-place upgrades; a new key will not update an app signed with the old key.
- GitHub's repository permissions must allow Actions to create Releases.
- The currently installed app must already contain this updater; otherwise install this repaired build once before the Update button can work.
