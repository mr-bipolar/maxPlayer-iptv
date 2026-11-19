# Building and Installing MaxPlayer

This guide covers the verified local build path for MaxPlayer. For tests, see [TESTING.md](TESTING.md); for device and platform claims, see [PLATFORM_SUPPORT.md](PLATFORM_SUPPORT.md).

## 1. Prerequisites

### Android Studio

The repository does **not** declare a minimum Android Studio version. Use a current stable Android Studio that can install the Android 17 / API 37 SDK and work with Android Gradle Plugin (AGP) 9.4.1 and Gradle 9.8.2.

At the time this documentation was verified (2026-11-19), the official stable channel was **Android Studio Rabbit 2 | 2026.2.2 Patch 1**. Treat that as a current recommendation, not a project-enforced minimum. No Android Studio installation was detected on the verification machine, so no maintainer-specific IDE version is claimed here.

### JDK and Gradle

| Item | Verified value | Source |
|---|---|---|
| AGP | `9.4.1` | [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) |
| Gradle wrapper | `9.8.2` | [`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties) |
| Wrapper checksum | SHA-256 pinned | `distributionSha256Sum` in the wrapper properties |
| Gradle daemon JVM request | Java `25` | [`gradle/gradle-daemon-jvm.properties`](../gradle/gradle-daemon-jvm.properties) |
| Java source/target compatibility | Java `11` | [`app/build.gradle.kts`](../app/build.gradle.kts) |
| AGP 9.4 JDK minimum/default | Java `17` / `17` | [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes.md.txt) |

The project compiles Java sources at Java 11 language level. AGP 9.4 requires at least JDK 17. The checked-in daemon configuration asks Gradle for Java 25; install JDK 25 if Android Studio or Gradle requests it. The verification build used Java 21 as the Gradle launcher JVM, so Java 25 is not a source-compatibility requirement.

Use the wrapper; do not rely on a system Gradle installation:

```bash
./gradlew --version
```

On Windows, use `gradlew.bat` wherever this guide shows `./gradlew`.

### Android SDK

Install these components with Android Studio's SDK Manager or `sdkmanager`:

- **SDK Platform 37** for Android 17. In the verified SDK this appears as `platforms/android-37.0`.
- **SDK Build-Tools 36.0.0 or newer**. AGP 9.4 lists 36.0.0 as its minimum and default; the verified SDK has 37.0.0 installed.
- **Android SDK Platform-Tools** for `adb`.
- Accept the Android SDK licenses.

The app has no `externalNativeBuild` block and no CMake or Android makefiles in this repository. You do **not** need to build native code or install an NDK for the normal debug build; native libraries are supplied by the MPV engine AAR.

### Local SDK location

Android Studio normally generates `local.properties`. It is ignored by Git and should not be committed:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Alternatively, set `ANDROID_HOME` or `ANDROID_SDK_ROOT` in your environment.

## 2. Clone the repository

```bash
git clone https://github.com/mr-bipolar/maxPlayer-iptv.git
cd maxPlayer-iptv
```

## 3. Open the project in Android Studio

1. Launch Android Studio.
2. Choose **File > Open…**.
3. Select the repository root — the directory containing `settings.gradle.kts` — not the `app/` subdirectory.
4. Allow the initial Gradle sync to finish.
5. Install any SDK components Android Studio reports as missing.

You can also verify the setup from a terminal:

```bash
./gradlew --version
./gradlew :app:tasks --all | head
```

## 4. MPV engine AAR setup

Two prebuilt AARs are checked into the repository:

- `app/libs/mpvengine-release.aar`
- `app/libs/mpvengine-debug.aar`

[`app/build.gradle.kts`](../app/build.gradle.kts) wires the **release** AAR into every build with a file dependency. The debug AAR is present but is not referenced by Gradle.

No separate download, native build, or manual library copy is required. Do not replace these AARs unless you intentionally want to change the native engine build and its licensing/provenance. See the [MPV Android Engine repository](https://github.com/mr-bipolar/mpv-android-engine) for upstream engine documentation.

## 5. Build the debug APK

```bash
./gradlew :app:assembleDebug
```

This command was executed successfully during documentation verification.

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The task also produces `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` when the Android test sources are packaged.

From Android Studio, use **Build > Build Bundle(s) / APK(s) > Build APK(s)** or run the `app` configuration.

## 6. Install and run

### Emulator

1. In Android Studio, open **Device Manager** and create an emulator image with API level 24 or higher. Use an API 37 image when testing target-level behavior.
2. Start the emulator.
3. Install and launch the APK with `adb`.

### Physical device

1. Enable **Developer options** and **USB debugging**.
2. Connect the device by USB and approve the computer's debugging prompt.
3. Confirm that the device is visible:

   ```bash
   adb devices
   ```

### Install with adb

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If several devices or emulators are connected:

```bash
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app from the device launcher, or:

```bash
adb shell monkey -p com.aaronmaxlab.maxplayer -c android.intent.category.LAUNCHER 1
```

### Install with Gradle

The `installDebug` task is available and builds, installs, and launches no UI by itself:

```bash
./gradlew :app:installDebug
```

To remove the debug build:

```bash
./gradlew :app:uninstallDebug
```

The `adb install` and Gradle install commands were not executed against a device during documentation verification; the Gradle tasks were confirmed to exist and the debug APK build was executed.

## 7. Configuration quick reference

| Setting | Verified value | Source |
|---|---|---|
| Application ID | `com.aaronmaxlab.maxplayer` | [`app/build.gradle.kts`](../app/build.gradle.kts) |
| Namespace | `com.aaronmaxlab.maxplayer` | `app/build.gradle.kts` |
| `minSdk` | `24` | `app/build.gradle.kts` |
| `targetSdk` | `37` | `app/build.gradle.kts` |
| `compileSdk` | `37` | `app/build.gradle.kts` |
| Version | `3.7` (`versionCode` `8027`) | `app/build.gradle.kts` |
| ABIs | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` | `app/build.gradle.kts` |
| View binding | Enabled | `app/build.gradle.kts` |
| Native packaging | `jniLibs.useLegacyPackaging = true` | `app/build.gradle.kts` |
