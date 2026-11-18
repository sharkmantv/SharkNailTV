# SharkNailTV safe upload package

This archive contains the Android source and a repaired GitHub Actions release workflow.

## Repairs
- Fixed the release manifest's versionName to use SHARK_VERSION (the old script referenced an undefined shell variable).
- Refuse to overwrite an existing release tag.
- Expanded .gitignore for local credentials.

## Before publishing
1. Upload the archive contents to the repository root (not the ZIP itself).
2. Configure the original Android signing key via GitHub Actions secrets.
3. Supply TMDB_API_KEY and SPORTS_API_KEY as repository secrets if needed.
4. Check the build in Actions before publishing an APK. No APK build has been verified.
5. Do not upload tmdb.properties, sports.properties, release-config/, keystores, or any other local credentials.

The Android app updater currently reads the GitHub Releases API and requires a public release containing SharkNailTV.apk.
