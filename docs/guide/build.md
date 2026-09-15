# Build from Source

This page is for contributors who want to build ChimeraLauncher itself. Native mod build notes live in [Native Mod Quick Start](/guide/developer).

## Prerequisites

- Git.
- Android Studio.
- JDK 21 or later.
- Android SDK for API 28 or later.
- Android NDK and CMake components installed through Android Studio when native code is built.

## Open the Project

```bash
git clone https://github.com/LiteLDev/ChimeraLauncher.git
cd ChimeraLauncher
```

Open the project directory in Android Studio and allow Gradle sync to finish. The project root is named `chimeralauncher` in Gradle settings, while the repository remains `ChimeraLauncher`.

## Build and Run

1. Connect an Android device or start an emulator that matches the supported Android baseline.
2. Select the `app` run configuration.
3. Build and run from Android Studio.
4. Install the official Minecraft app on the test device before testing launcher flows.

## Command Line Build

```bash
./gradlew assembleDebug
```

This builds both ABI flavors. To build one at a time:

```bash
./gradlew :app:assembleAbi64Debug   # arm64-v8a, runs 64-bit Minecraft versions
./gradlew :app:assembleAbi32Debug   # armeabi-v7a, runs 32-bit Minecraft versions
```

Android fixes an app's bitness at install time, so a 32-bit Minecraft version only runs under the
`abi32` build and a 64-bit version only under `abi64`. The two share an application ID, so installing
one replaces the other. Run the unit tests with `./gradlew :app:testAbi64DebugUnitTest`.

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Contributor Checks

Before submitting launcher changes, build the app and test the touched flow on a device. Documentation-only changes should at least pass the VitePress build from `docs/`.
