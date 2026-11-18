# SharkNailTV GitHub upload and updates

This archive merges the four supplied ZIP files into one repository-root project. The `GitHub_Fixed` version wins when filenames conflict. The older `shark-build.yml` is excluded.

1. Extract this ZIP, then commit its CONTENTS to the root of `sharkmantv/SharkNailTV` on `main` (not as an enclosing directory). Use git/GitHub Desktop for large files.
2. Remove `.github/workflows/shark-build.yml` from GitHub if it exists. Keep `.github/workflows/build-release.yml`.
3. Configure the Android signing secrets listed in `GITHUB_SETUP.md` using the SAME signing keystore as the currently installed APK. Never upload the keystore or passwords.
4. Run GitHub Actions > Build and publish SharkNailTV on `main`; inspect logs if it fails. The workflow publishes a GitHub Release only after build and signature verification pass.
5. Install the signed release APK. Subsequent published releases with greater version codes should be discoverable by the app updater; Android requires installation approval.

NOT VERIFIED: No successful Android build or device update has been executed as part of this merge. The app updater fetches GitHub's latest release and expects an asset named `SharkNailTV.apk`. This is APK application updating, NOT device firmware flashing.
