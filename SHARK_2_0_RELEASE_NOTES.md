# SharkNailTV 2.0 — Source update

Based on the supplied SharkNailTV 1.0 source.

- Bright yellow/gold pulsing focus on shared Compose focus frames, navigation, Settings and premium buttons; steady cyan for unfocused frames.
- Smaller 112dp streaming-service catalogue poster cards (formerly 175dp); square-corner poster cards retained.
- Higher decode target of 600x900 for portrait posters; actual sharpness still depends on source image and device.
- Use Compose SansSerif font family to avoid platform-specific Typeface adapter issues; upright text retained.
- Preserve Home structure, Sky Stream branding, streaming service list, and Nuvio QR account flow.
- Android versionName 2.0 and versionCode 20000.

## Verification
ZIP integrity verified. Not compiled, installed or tested on a TV. Build with Android Studio and test DPAD focus, poster rows, 1080p/4K scaling and QR account connection before release.
