# Onlymusic — Android preview 1.0

An Android music player adapted from the supplied **Onlymusic 3.fig**: warm peach/cream colors, rounded controls, translucent glass surfaces, and a dark mini-player behind a floating search bar. This is a functional Android interpretation of the reference, not a pixel-identical Figma export or Apple's proprietary Liquid Glass renderer.

## Install

1. Copy `Onlymusic-1.0.apk` to your Android phone and open it. If prompted, allow that browser/file manager to install this app.
2. Open Onlymusic. Choose **Find my music** for the Android audio-permission request, or **Choose individual files** for Android's document picker.
3. Tap a song to play it. Tap the bottom player for the full player, seeking, shuffle, repeat, and the queue.
4. Settings includes refresh, imports, notification permission, and Android app settings.

Requires Android 10+ (API 29); targets Android 15 (API 35). Keep Android System WebView updated for the glass effects. This is a development-signed installable preview, not a Play Store release.

## Features implemented

- Audio-library permission requests and MediaStore scanning.
- Multi-file import using the Android document picker with persistent URI access.
- Native MediaPlayer playback in a foreground media service, independent of the WebView UI.
- MediaSession, notification and lock-screen controls; play/pause, previous/next, seek, shuffle, repeat-all and repeat-one.
- Audio focus handling and pause on headphone disconnect.
- Search by title, artist, or album; album browsing, sorting, persistent local favorites, and a play queue.
- Embedded album artwork, with abstract covers for files without images.
- Optional Google Credential Manager integration with Firebase Authentication verification and sign-out.

No music files, commercial album artwork, ads, analytics, or streaming services are bundled. Your music is not uploaded. Google sign-in adds a profile; it does not synchronize files or favorites. Imported file references and favorites persist. The active queue is in memory and does not survive process death. Unavailable, DRM-protected, or unsupported files show an error. Codec support varies by Android device.

## Enable Google login

**The delivered APK cannot sign into Google yet.** The app-owner Firebase/OAuth project was not supplied. Its Google button explicitly explains the missing setup. Offline listening does not require an account.

1. Create/select a Firebase project and register Android package `app.onlymusic.player`.
2. Add the SHA-1 and SHA-256 signing fingerprints from `SIGNING.txt`.
3. Enable the Google provider in Firebase Authentication and set the support email. Configure the associated OAuth consent screen and test users as required for your project.
4. Download the updated Android `google-services.json` into `app/google-services.json`.
5. Rebuild and install the new APK. Gradle automatically applies the Google Services plugin when this file exists. The app uses the generated `default_web_client_id`.
6. Test with a Google account on an Android device with Google Play services.

Send the Android `google-services.json` if you want the APK rebuilt with your project. Do not supply or embed a Firebase Admin service-account private key.

The included `development.keystore` is the preview signing key so your rebuilt debug APK can update this preview. Its store/key password is `android`, alias `androiddebugkey`. Treat it as a development key only. For public distribution, create your own private release signing key and register its fingerprints.

Official references:
- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://developer.android.com/develop/background-work/services/fgs/declare

## Build

Open this folder in Android Studio with **JDK 17**, Android SDK **Platform 35**, and **Build Tools 35.0.0**. Let Android Studio create `local.properties` for your SDK path and sync the project. Initial dependency downloads need internet access.

Linux/macOS:

```sh
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Source

- `app/src/main/assets/index.html`: bundled UI, glass styles, SVG controls, and interaction logic.
- `app/src/main/java/app/onlymusic/player/MainActivity.java`: trusted local WebView, permissions, file picker, library scanning, artwork, Google/Firebase authentication.
- `app/src/main/java/app/onlymusic/player/PlaybackService.java`: native audio, foreground service, notifications, MediaSession, audio focus, and queue.
- `app/src/main/java/app/onlymusic/player/Track.java`: track model.
- `tests/ui-smoke.cjs`: UI interaction checks using synthetic metadata; install Playwright and set `CHROME_PATH` to a Chromium binary to run.

The WebView disables file/content URL access and blocks outside navigation and resources. The native bridge is exposed only to the packaged UI. WebView debugging is enabled only for debuggable builds. Native Firebase network calls are independent of the WebView.

See `VALIDATION.md` for completed checks and untested device behavior.
