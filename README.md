# RoadPlay

Local video player for Android with an audio-only Android Auto media experience.
Kotlin, Jetpack Compose, Material 3, Media3 (ExoPlayer + MediaLibraryService), Room, DataStore.

> **Status:** written without access to an Android SDK or network, so it has **never been compiled or run**.
> Expect the first CI run to reveal a few small compile errors. Paste the error log back to Claude to fix them.
> No car testing has been done.

## What works (by design)
- Import videos/folders via the system picker (no storage permission), library, search, sort, favourites, playlists
- Player: seek, ±10 s, next/previous, speed, aspect modes, subtitles (embedded + .srt/.vtt/.ass/.ttml), sleep timer, resume, volume/brightness gestures, error recovery
- Android Auto: browsable audio library; video is never sent to the car. See `docs/ANDROID_AUTO_COMPATIBILITY.md`.

## Folder structure
- `app/src/main/java/com/roadplay/player/` : `data/` (Room, settings, import), `playback/` (service, controller), `safety/`, `ui/` (screens, components, theme)
- `app/src/test` unit tests, `app/src/androidTest` device tests
- `.github/workflows/android.yml` CI, `gradle/libs.versions.toml` dependency versions

Key versions: AGP 8.7.3, Kotlin 2.0.21, Gradle 8.10.2, Compose BOM 2024.10.01, Media3 1.5.1, Room 2.6.1, JDK 17, compileSdk/targetSdk 35, minSdk 26. (Not the newest; verify and bump if you wish.)

## Build locally
1. Install Android Studio (latest stable); it bundles JDK 17 and the SDK manager (install "Android SDK Platform 35").
2. File > Open > select this folder; let Gradle sync. If `gradlew` is missing, run `gradle wrapper --gradle-version 8.10.2` once.
3. Run on a phone (USB debugging on) with the Run button, or `./gradlew testDebugUnitTest assembleDebug`.
4. APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub and APK
1. Create a repository on github.com (branch `main`), then in this folder: `git init`, `git add .`, `git commit -m "RoadPlay"`, `git branch -M main`, `git remote add origin <url>`, `git push -u origin main`.
2. Open the **Actions** tab; enable workflows if asked. The run starts on push, or use **Run workflow**.
3. When green, open the run and download the **roadplay-debug-apk** artifact (zip), unzip it.
4. On your phone: copy `app-debug.apk`, open it, allow "install unknown apps" for your file manager, install. The debug build installs as `com.roadplay.player.debug`.

The debug APK is for testing only. Signed release: create a keystore yourself (`keytool -genkey -v -keystore roadplay.jks -alias roadplay -keyalg RSA -keysize 2048 -validity 10000`), keep it out of git, set env vars `ROADPLAY_KEYSTORE_PATH`, `ROADPLAY_KEYSTORE_PASSWORD`, `ROADPLAY_KEY_ALIAS`, `ROADPLAY_KEY_PASSWORD`, then `./gradlew assembleRelease`. For CI use GitHub Secrets.

## Rename
App name: `app/src/main/res/values/strings.xml`. Package: change `namespace`/`applicationId` in `app/build.gradle.kts`, move the folders under `java/`, and update every `package`/`import com.roadplay.player` line (Android Studio: Refactor > Rename package).

## Testing
1. **Phone:** import a video, play, close the app, reopen (resumes). Try a deleted file (shows "Unavailable"), a subtitle file, the sleep timer. Run `./gradlew connectedDebugAndroidTest` with a device attached.
2. **Desktop Head Unit:** install the DHU via SDK Manager > SDK Tools > Android Auto Desktop Head Unit Emulator; on the phone enable Android Auto developer mode > Start head unit server; run DHU. Open RoadPlay in the media list; browse and play. Unknown-source apps may require enabling "Unknown sources" in Android Auto developer settings.
3. **Real car:** same, over USB/wireless. Expect audio only. Report results; none are claimed here.

## Troubleshooting
- *SDK location not found / missing platform:* install Platform 35 in SDK Manager; create `local.properties` with `sdk.dir=...`.
- *Unsupported class file / Java version:* use JDK 17 (Settings > Build Tools > Gradle > Gradle JDK).
- *Could not resolve dependency:* check internet/proxy, then Sync again; versions are in `gradle/libs.versions.toml`.
- *Video won't play:* the codec may be unsupported by your phone; try H.264/AAC MP4.
- *Not visible in Android Auto:* enable developer mode, "Unknown sources", reconnect; Android Auto only lists apps it recognises as media apps.
- *Notification missing on Android 13+:* allow notifications for the app.

Everything stays on-device: no accounts, ads, analytics or network use.
