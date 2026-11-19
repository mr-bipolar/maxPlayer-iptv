# Android Platform and Architecture Support

This document records only what is established by the repository configuration, manifest, native artifacts, and source code. A successful build is not treated as device compatibility evidence.

## 1. Android version support

| Setting | Verified value | Meaning |
|---|---|---|
| `minSdk` | `24` | Android 7.0 (API 24) is the lowest installable version declared by the app. |
| `targetSdk` | `37` | Android 17 (API 37) is the target platform. |
| `compileSdk` | `37` | The app is compiled against API 37. |
| Maximum SDK | Not declared | No upper bound is encoded in the Gradle configuration. |

The values come from [`app/build.gradle.kts`](../app/build.gradle.kts). API 37 corresponds to [Android 17](https://developer.android.com/about/versions/17).

The repository does **not** contain a tested-device matrix. Compatibility with Android versions above 7.0, including Android 17 behavior, must be verified on real devices or emulators. Building or installing does not by itself prove that every feature works on a given Android release.

## 2. CPU architectures (ABIs)

[`app/build.gradle.kts`](../app/build.gradle.kts) limits packaging to:

- `armeabi-v7a` — 32-bit ARM
- `arm64-v8a` — 64-bit ARM
- `x86` — 32-bit x86
- `x86_64` — 64-bit x86

Both checked-in engine AARs contain native libraries for all four ABIs:

- `app/libs/mpvengine-release.aar`
- `app/libs/mpvengine-debug.aar`

Each ABI directory contains `libavcodec.so`, `libavdevice.so`, `libavfilter.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libmpv.so`, `libplayer.so`, and `libc++_shared.so`. The debug APK was observed to contain all four ABI directories.

The app declares no support for MIPS or other ABIs. On an unsupported device, the MPV engine cannot load its native libraries; [`MpvPlayerAdapter`](../app/src/main/java/com/aaronmaxlab/maxplayer/player/mpv/MpvPlayerAdapter.java) catches `UnsatisfiedLinkError`/`RuntimeException`, and [`PlayerActivity`](../app/src/main/java/com/aaronmaxlab/maxplayer/player/PlayerActivity.java) falls back to Media3 for that session and shows `player_mpv_unavailable`.

## 3. Android TV support

Android TV is explicitly supported in [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml):

- `android.software.leanback` is declared with `android:required="false"`.
- `android.hardware.touchscreen` is declared with `android:required="false"`.
- The application supplies `android:banner="@drawable/tv_banner"`.
- `SplashActivity` includes both `LEANBACK_LAUNCHER` and `LAUNCHER` categories.
- TV-specific activities such as `TvSectionActivity`, `TvPlaylistsActivity`, `TvFavoritesActivity`, `TvSearchActivity`, and `TvCategoriesActivity` are registered.
- TV layouts are provided under [`app/src/main/res/layout-television`](../app/src/main/res/layout-television).
- [`DeviceUtils`](../app/src/main/java/com/aaronmaxlab/maxplayer/utils/DeviceUtils.java) detects television UI mode and the Leanback feature, and those helpers are used throughout the UI code.

Because Leanback and touchscreen are optional features, the APK is not restricted to Android TV devices; it can also be installed on touchscreen devices. D-pad/remote input matters for TV use. `android.hardware.camera` is also declared optional.

## 4. Runtime platform notes

- The app uses `android:usesCleartextTraffic="true"`, so cleartext network streams are allowed by the manifest.
- `WRITE_EXTERNAL_STORAGE` is capped at `maxSdkVersion="28"`; newer Android versions use the platform's scoped-storage/document APIs.
- Media playback foreground services are declared with `foregroundServiceType="mediaPlayback"`.
- Two engines are available: Media3 (ExoPlayer) and MPV. [`MediaTypes`](../app/src/main/java/com/aaronmaxlab/maxplayer/utils/MediaTypes.java) documents the conservative rule for selecting MPV when Media3 is unlikely to play a source, including several streaming protocols and legacy formats.

## 5. What is not established

- No minimum or maximum Android Studio version is declared by the repository.
- No NDK version is pinned in `app/build.gradle.kts`; no native source is built there. The NDK used for the prebuilt MPV engine AAR is a separate upstream concern.
- No device-specific performance, codec, DRM, or renderer compatibility is asserted here.
- No maximum API level is declared beyond the compile/target SDK values.
- Android TV behavior has not been certified on a specific TV device as part of this documentation work.
