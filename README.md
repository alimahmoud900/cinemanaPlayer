# Cinemana Player — native Android (phones & tablets)

Kotlin + Jetpack Compose + Media3 (ExoPlayer) port of `Player_source_.html`.

## Build

1. Install **Android Studio Ladybug (2024.2.1) or newer** (bundles JDK 17).
2. `File ▸ Open…` → select this folder → wait for the Gradle sync (needs internet the first time; Android Studio downloads Gradle 8.9 automatically).
3. Plug in a phone (USB debugging) or start an emulator → **Run ▶**.
4. To get an APK file: `Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)`, or in a terminal:
   ```
   ./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
   ./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (debug-signed, sideload-ready)
   ```
   (If `./gradlew` is missing, run `gradle wrapper` once, or just use the Android Studio menu.)

minSdk 26 (Android 8.0), target/compile 35. Phones and tablets share one APK.

## HTML → Android map

| HTML feature | Android implementation |
|---|---|
| `fetch` + 5-min response cache | `data/CinemanaApi.kt` (OkHttp + kotlinx-serialization JSON tree, same endpoints & cache TTL) |
| Fuzzy title match (SequenceMatcher, IMDb/MAL/year scoring) | `data/Matching.kt` (same algorithm & thresholds) |
| Quality normalisation (360p–4K) | `data/Quality.kt` |
| SRT/VTT parser, language detection | `data/Subtitles.kt` |
| `localStorage` settings + resume positions | `data/SettingsStore.kt` (DataStore; resume list capped at 300) |
| `<video>` + `timeupdate` logic (skip intro, Safe Watch, next-up, progress) | `ui/AppViewModel.kt` (ExoPlayer + 250 ms ticker) |
| Custom controls, tap zones, double-tap ±10 s, long-press 2×, seek bar | `ui/PlayerScreen.kt` |
| Multi-track subtitle renderer (font/size/position/colour/background/shade) | `ui/SubtitleOverlay.kt`, `ui/SettingsPanel.kt` |
| Season tabs, episode strip, episode drawer | `ui/HomeScreen.kt`, `ui/EpisodesPanel.kt` |
| Fullscreen / PiP | immersive landscape + `enterPictureInPictureMode` (`MainActivity.kt`) |
| Responsive layout | `ui/App.kt` — phone: pinned player + scrolling content; ≥ 840 dp wide: two panes with permanent episode list |

## Intentional differences

- **Volume/mute** use the phone's hardware volume (no in-app slider).
- **Subtitle fonts** use Android system families (Sans / Serif / Mono / Handwriting). To restore Cairo, Tajawal, etc., drop the `.ttf` files in `res/font/` and add entries in `ui/SubtitleFonts.kt` (instructions inside).
- **Subtitle colour**: 8 swatches instead of a free colour picker.
- Resume positions use DataStore instead of Room (fewer moving parts, same behaviour).
- The app stops playback when you leave it, except when it moves into Picture-in-Picture.
- Not included yet: background audio / lock-screen notification (MediaSession), Chromecast, downloads.

## Notes

- The app talks to an **unofficial, undocumented** API (`cinemana.shabakaty.cc/api/android`). It can change without notice.
  Check the terms of the service before distributing the app publicly.
- `usesCleartextTraffic` is enabled in the manifest in case the CDN returns `http://` stream URLs; set it to `false` if everything you see is https.
- This source was written and cross-checked by hand but **was not compiled** in the environment that produced it
  (no Android SDK / network there). If Gradle reports an error on first sync, it should be a small import or API-name fix.
