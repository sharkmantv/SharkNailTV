# GitHub auto-build and Android TV update setup (fixed)

1. Upload the CONTENTS of this ZIP to the root of the public `sharkmantv/SharkNailTV` GitHub repository, including the hidden `.github` directory. Remove any old duplicate release workflows.
2. Configure repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_CERT_SHA256`. Use the ORIGINAL app signing key; a different key cannot upgrade existing installs. Never upload the keystore itself.
3. Under Settings > Actions > General, permit workflows and give `GITHUB_TOKEN` read/write contents permissions.
4. Push changes to `main` affecting `app/`, `gradle/`, root Gradle files or the workflow, or manually run the workflow. Each successful run builds, signs, verifies and publishes `SharkNailTV.apk` and `update.json` to a public GitHub Release.
5. The app checks GitHub's latest release once per 24 hours when launched and prompts users to install newer APKs. Android installation requires user approval.

## Versioning repair
GitHub run number N (1..9999) is encoded as versionCode 30000+N and release tag `v3.(N/100).(N%100)` (e.g. run 100 => `v3.1.0`, code 30100). This matches the app's existing version parser, which only accepts version components 0..99.

## Important
This ZIP is source code, not a built APK. GitHub builds cannot succeed without your signing secrets and any other required project dependencies. The repository must be public for the app's unauthenticated release lookup. Existing installations must have a lower versionCode and the same package name/signing certificate. No live GitHub build or TV installation was tested here.
