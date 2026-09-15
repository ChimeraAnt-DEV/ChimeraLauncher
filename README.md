<p align="center">
  <img src="https://raw.githubusercontent.com/LuauScripts-usr/Logoz/main/ShidoreAv.webp" width="150" height="150" alt="Profile Avatar">
</p>


# Chimera Launcher


**A lightweight Android launcher for Minecraft: Bedrock Edition**

[![GitHub Release](https://img.shields.io/github/v/release/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=blue)](https://github.com/LuauScripts-usr/Chimera-Launcherr/releases)
[![License: Apache 2.0](https://img.shields.io/github/license/LuauScripts-usr/Chimera-Launcherr)](https://github.com/LuauScripts-usr/Chimera-Launcherr/blob/main/LICENSE)
[![Issues](https://img.shields.io/github/issues/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=red)](https://github.com/LuauScripts-usr/Chimera-Launcherr/issues)
[![Stars](https://img.shields.io/github/stars/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=yellow)](https://github.com/LuauScripts-usr/Chimera-Launcherr)
[![Downloads](https://img.shields.io/github/downloads/LuauScripts-usr/Chimera-Launcherr/total.svg)](https://github.com/LuauScripts-usr/Chimera-Launcherr/releases)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)

---

## Introduction

Chimera Launcher is a lightweight, open-source Android launcher specifically designed for legitimate players of Minecraft: Bedrock Edition (MCBE). It provides a flexible and user-friendly alternative to the standard Google Play installation, allowing you to manage multiple game versionsand extend functionality with external modules.

Chimera Launcher enables you to import your official Minecraft APK and run it directly without requiring system installation. The launcher supports loading external native modules to enhance gameplay, provides robust multi-version management with complete isolation between installations,and includes built-in tools for managing worlds, resource packs, skin packs, accounts, controllers,and launcher settings. Whether you're looking to organize different game versions, test modifications, or optimize your gaming experience, Chimera Launcher offers the flexibility you need. The repository also ships a full user guide(English and 简体中文, developer documentation, and Preloader API reference under `docs/`.

> **Project lineage:** This repository is a **fork of [LeviLauncher](https://github.com/LiteLDev/LeviLauncher)** (also known as LeviLaunchroid, the Android companion in the [LeviMC](https://levimc.org) open-source Bedrock modding ecosystem). It inherits the LeviLauncher Preloader/native-mod architecture and `.levipack` packaging,and continues development as Chimera Launcher with its own branding, features,and maintenance..



### Key Features

- **APK Import & Installation-Free Launching** – Import your official Minecraft APK(XAPK or composed APK supported)and run it directly without system installation
- **Native Mod System (LeviMC Preloader)** – Load external native SO modules packaged as `.levipack`,with a Preloader input/output pipeline, hook installation, patches,and keyboard/gamepad input callbacks for developers(see `docs/api` and `examples/full-cpp-mod`)
- **Multi-Version & Instance Management** – Manage multiple Minecraft versions independently,with complete isolation between configurations,game data,and worlds
- **Built-In Mod Manager** – Toggle installed mods on/off,a native"mod menu" switch,and per-mod configuration from inside the launcher

- **Mod Sourcing** – Import mods from **CurseForge**(user-provided API key)and **Modrinth**(open API), install `.mcpack`/`.mcaddon` files,or pick local files

- **Multiple Xbox Account Management** – Add,and switch multiple Microsoft/Xbox accounts in the launcher,with in-app MSAL login,and device-code flows,so the game always launches with the expected identity
- **Controller Support & Input Mapping** – Up to 5 profiles per controller type(Xbox,DualShock 4,DualSense,with button remapping,stick dead-zone tuning,sensitivity,and vibration toggle,applied live to gameplay
- **Content Management & Skin Packs** – Import,export,and back up worlds,resource packs,skin packs,and launcher data from one place,plus skin-pack management outside the game**
- **Custom Flat Worlds** – Craft preset or fully customized superflat worlds before the game even opens**
- **Options Editing & Quick Launch** – Edit per-version `options.txt`-style settings,and use Minecraft URI quick-launch actions to open screens,connect to servers,add servers,join Realms,load worlds,or run commands**
- **Java Edition Support (Experimental)** – Launch Java Edition with a user-provided runtime via `JavaEditionModManager`
- **News, Updates,& Crash Reporting** – In-app news feed with notifications,a crash-reporting screen surfacing logs,and automated release-content tooling(`docs/RELEASE_CONTENT.md`
- **Personalization & Polish** – User-configurable accent color,glass-card/compact-mode theming,animations,empty states,and a one-tap"last played" home hero card**

---

## System Requirements

Before installing Chimera Launcher, ensure your device meets the following minimum specifications:

- **Operating System:** Android 9.0 (API 28) or higher
- **Device Architecture:** 64-bit (arm64-v8a) only — 32-bit (armeabi-v7a) instances and devices are not supported
- **RAM:** Minimum 1 GB available RAM (2 GB or more recommended)
- **Storage:** At least 2 GB of available storage for Minecraft and game data
- **License Requirement:** You must have Minecraft installed on you're device for Chimera Launcher to work

> **Note:** For optimal performance,and stability, we recommend Android 9.0 or higher with at least 3 GB of available RAM and 5 GB of free storage.

---

## Installation

### Prerequisites

Before proceeding with Chimera Launcher installation, ensure that you have the official Minecraft Bedrock Edition app installed on your device from Google Play. This is required for Chimera Launcher to function properly.

### Installation Steps

1. Visit the [Releases Page](https://github.com/LuauScripts-usr/Chimera-Launcherr/releases)and download the latest Apk/Zip build (extract it)
2. Open your device Settingsand navigate to Security or Applications
3. Enable"Unknown Sources" or "Allow installation from unknown sources" to permit APK installation
4. Locate the downloaded APK file using your file manager,and tap to install
5. Grant the necessary permissions when prompted during installation
6. Once installed, open Chimera Launcher from your application drawer

> **Important:** Chimera Launcher requires a legitimate, licensed copy of Minecraft Bedrock Edition. Do not use this launcherwith pirated or unauthorized versions of the game. Ensure your Minecraft license is validand properly linked to your Microsoft account..

---

## Development Setup

If you want to build Chimera Launcher from source or contribute to development, follow these steps to set up your development environment:

### Prerequisites

- Git installed on your system
- Android Studio (latest version recommended)
- Java Development Kit (JDK) 21 or higher
- Android SDK with API level 28 or higher

### Setup Instructions

1. Clone the Chimera Launcher repository:


   ```bash
   git clone https://github.com/LuauScripts-usr/Chimera-Launcherr.git
   ```

2. Open the project directory in Android Studio
3. Allow Android Studio to download,and install required dependencies,and build tools
4. Wait for Gradle to complete the initial sync process..
5. Connect your Android device or start an emulator (API 28+)
6. Click the"Run" button in Android Studio to build,and deploy to your device
7. The app will launch automatically on successful build completion

> **Build Tip:** For faster builds during development, use `Build > Make Project` to compile incrementally instead of full rebuilds..

---

### Continuous Integration

Every push to `main` and every pull request automatically triggers a CI workflow (`.github/workflows/build.yml`) that:

- Checks out the repository **with submodules** (the native Preloader/LibHttpClient code is fetched,so the full native build can run)
- Builds debug APKs for **both ABIs** with JDK 21 and Gradle 8.13 (via the existing wrapper,`./gradlew assembleDebug`)
- Runs the unit test suite (`./gradlew :app:testAbi64DebugUnitTest`)
- **Fails the run (and blocks/red-flags the PR)** if the build breaks — it never silently swallows errors
- Uploads both APKs as workflow artifacts, downloadable straight from the **Actions** tab

To grab the APKs from a green run:

1. Open the **Actions** tab on the repository
2. Click the latest green **Build Debug APK** run
3. Scroll to the bottom and download **app-debug-apk-abi64** (for 64-bit Minecraft versions) and/or **app-debug-apk-abi32** (for 32-bit versions — see below)
4. Install it on your device (Android 9.0+/API 28+,

### Which APK do I need?

Android locks an app to 64-bit or 32-bit when it is installed, and a Minecraft version's native
libraries only load in a process of the matching bitness. So there are two builds:

| Build | ABI shipped | Runs |
| --- | --- | --- |
| `abi64` | `arm64-v8a` | 64-bit Minecraft versions (the usual case) |
| `abi32` | `armeabi-v7a` | 32-bit-only Minecraft versions |

Install only one at a time — they share the same application ID, so the second install replaces the
first. Install `abi64` unless you specifically need a 32-bit version; `abi32` also omits the
closed-source `gxcore` verification, the inbuilt mod menu and materialbin autofix, which have no
32-bit builds available.

If you launch a version whose bitness does not match the installed build, the launcher tells you
before starting rather than failing part-way through with a `dlopen` error.

---

## Contribution Guidelines

We welcome contributions from the community to improve Chimera Launcher. To ensure a high-quality codebase,and smooth collaboration, please adhere to the following guidelines:

### Code Quality

Write clean, modular code with descriptive variable names,and consistent formatting. Follow Kotlin,and Java style guidelines established in the project. Ensure your code is readable,and well-structured for future maintainers..

### Commit Structure

Use small, focused commits with clear,and descriptive messages. Each commit should address a single feature or bug fix. Example:"Fixed memory leak in version manager" or"Added support for ARM32 architecture".

### Documentation

Add comments for complex logic,and update relevant documentation in the repository. If you add new features, update the README,and any related documentation files..

### Performance

Optimize all additions to maintain low latency,and smooth performance. Test your changes thoroughly to ensure they don't introduce lag or performance regressions..

### Testing

Test all changes on multiple devices,and Android versions to ensure compatibility,and stability..

### Pull Requests

Submit PRs with a detailed description of changes, including the problem solved or feature added. Reference any related issues,and provide screenshots or videos if your changes affect the UI..

### Community Standards

Follow our Code of Conduct to maintain a respectful,and inclusive environment. Be constructive in feedback, respect others' work,and communicate professionally with all contributors..

**Before Submitting:** Run a full build cycle,and test on at least one device to minimize errors.. We review all contributions promptly,and appreciate your efforts to enhance Chimera Launcher..

---

## Usage Guidelines

Chimera Launcheris designed for legitimate players of Minecraft Bedrock Edition. Please respect the following guidelines,and terms of use:

### Permitted Uses

- Modify Chimera Launcher for personal gameplay,and to test new features
- Create educational content(videos, tutorials, blog posts) showcasing Chimera Launcher's capabilities
- Fork the repository for learning purposes or to create derivative projects, provided you comply with the Apache License 2.0
- Share your modified versions with others as long as you comply with the Apache License 2.0 terms

### Prohibited Uses

- Do not claim Chimera Launcheras your own without crediting the Chimera Team,and its contributors
- Do not use Chimera Launcherto violate Mojang or Microsoft's user agreements

> **Disclaimer:** The authors,and contributors of Chimera Launcher are not responsible for bans, damages, or issues arising from the use of this software. Use it at your own risk,and in accordance with Minecraft's terms of service..

For full legal details, see the LICENSE file in the repository..

---

## Acknowledgements

Chimera Launcher owes its foundation to the **LeviMC / LeviLauncher community** — the Preloader architecture, `.levipack` tooling,and native-mod ecosystem this project builds on. It would not be possible without the contributions of many talented individuals,and organizations:

### Special Thanks To

- **Chimera Team** – For maintaining the Chimera Launcher project,and providing infrastructure support
- **LeviMC Team** – For the original LeviLauncher(LeviLaunchroid, Preloader,input pipeline,and `.levipack` ecosystem that Chimera forks)
- **Android Community** – For excellent documentation, libraries,and tools that made this launcher possible
- **Open Source Community** – For all the libraries, frameworks,and tools that power this project
- **Contributors** – A heartfelt thank you to all [contributors](https://github.com/LuauScripts-usr/Chimera-Launcherr/graphs/contributors) who have continuously improved,and maintained Chimera Launcher through their time,and expertise

---

## Contact & Support

**Author / Team:** Chimera Team

**Project Repository:** [https://github.com/LuauScripts-usr/Chimera-Launcherr](https://github.com/LuauScripts-usr/Chimera-Launcherr)

**Report Issues:** [GitHub Issues Page](https://github.com/LuauScripts-usr/Chimera-Launcherr/issues)

**For support,and questions:** Please create an issue on the GitHub repository.

---

<div align="center">

[![GitHub Release](https://img.shields.io/github/v/release/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=blue)](https://github.com/LuauScripts-usr/Chimera-Launcherr/releases)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Issues](https://img.shields.io/github/issues/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=red)](https://github.com/LuauScripts-usr/Chimera-Launcherr/issues)
[![Stars](https://img.shields.io/github/stars/LuauScripts-usr/Chimera-Launcherr?style=flat-square&color=yellow)](https://github.com/LuauScripts-usr/Chimera-Launcherr)
[![Downloads](https://img.shields.io/github/downloads/LuauScripts-usr/Chimera-Launcherr/total.svg)](https://github.com/LuauScripts-usr/Chimera-Launcherr/releases)
[![Android](https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android)](https://www.android.com/)

**Made with ❤️ by the Chimera Team Community**

</div>
