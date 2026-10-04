# Validation and limitations

## Android build checks

- `assembleDebug`, `lintDebug`, and Gradle wrapper generation completed successfully.
- Android lint: **0 errors, 5 warnings**. Warnings concern generic Credential Manager error handling, dynamic optional configuration lookup, intentional JavaScript in a packaged-only WebView, and explicit backup configuration guidance. No lint baseline or error suppression was added.
- APK Signature Scheme v2 verification and ZIP alignment verification passed.
- APK metadata confirms package `app.onlymusic.player`, minimum API 29, target API 35.
- The UI bundled in the APK matches the delivered HTML source byte for byte.

## UI checks

The bundled interface was rendered and inspected in Chromium. Automated checks passed for onboarding, safe metadata rendering, search filtering, favorites, album grouping, selection and playback bridge calls, shuffle/repeat bridge calls, queue display, sign-in/permission/import button calls, and horizontal overflow at 320, 393, and 600 CSS-pixel widths. No JavaScript runtime errors were observed.

These tests use synthetic metadata and a mocked native bridge. They do not prove Android audio output or permission behavior. Test songs are not bundled in the APK.

## Device testing still required

An Android API 35 emulator was attempted earlier in this task, but did not reach a usable ADB-connected state without hardware acceleration. Native runtime tests could not run. The rebuilt version has not been tested on a physical device.

Google login code is integrated but cannot authenticate until the app owner supplies the Firebase/OAuth configuration and rebuilds. The preview shows a clear setup-required dialog. Full Google sign-in is untested.

On the first device, test: permission grant and denial, a local MP3/WAV, document-picker import, pause/resume, seeking, next/previous, background playback, notification controls, lock screen, and headphone disconnect. Samsung/Redmi power management, Bluetooth controls, very large libraries, and uncommon codecs need device testing.

This is a development-signed preview, not a production-certified release. The active play queue is not restored after process death; local favorites and imported file references persist.
