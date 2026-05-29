# Warm Tiles

Keeps third-party Quick Settings tiles warm by raising SystemUI's binding cap, so they respond on tap instead of cold-starting.

![Android 13+](https://img.shields.io/badge/Android-13%2B-1B5E20?style=flat-square)
![libxposed API 101](https://img.shields.io/badge/libxposed-API_101-ff69b4?style=flat-square)
![Xposed Repo](https://img.shields.io/github/downloads/Xposed-Modules-Repo/eu.hxreborn.qsboundlesstiles/total?label=Xposed%20Repo&style=flat-square&logo=android&logoColor=white)

## Background

Android limits third-party Quick Settings tiles to [3 concurrent bindings](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37) by default. When the QS panel opens, SystemUI recalculates allowances and unbinds tiles past the cap. On many ROMs the unbound services sit frozen, so tapping one triggers an unfreeze/rebind delay.

Tiles still unbind [~30 seconds](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/external/TileServiceManager.java#53) after the panel closes.

Warm Tiles hooks SystemUI to raise that cap, so more tiles stay bound while the panel is open.

## Requirements

- Android 13+ (API 33+)
- Xposed manager with API 101 support (official LSPosed, LSPosed Irena fork, or Vector's JingMatrix fork)
- Scope: `com.android.systemui`
- Root for tile scanning and `Restart SystemUI`

Tested on Pixel and LineageOS (Android 16). Other OEM ROMs may vary.

## System Overhead

- RAM: more tiles stay bound while QS is open, so memory use scales with tile count. Memory returns to stock once the panel closes and services unbind (~30s).
- Battery: no periodic work, wakelocks, or network. The hook runs only when SystemUI recalculates tile bindings.
- Stability: the hook blocks memory-pressure downscaling of the cap. Aggressive settings on low-RAM devices can raise jank or trigger OOM kills.

Report issues on [GitHub](https://github.com/hxreborn/qs-boundless-tiles/issues/new/choose).

## Installation

1. Grab the APK:

   <a href="../../releases"><img src=".github/assets/badge_github.png" height="60" alt="Get it on GitHub" /></a>
   <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22eu.hxreborn.qsboundlesstiles%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhxreborn%2Fqs-boundless-tiles%22%2C%22author%22%3A%22rafareborn%22%2C%22name%22%3A%22Warm%20Tiles%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D"><img src=".github/assets/badge_obtainium.png" height="60" alt="Get it on Obtainium" /></a>

2. Enable the module in LSPosed.
3. Scope to `com.android.systemui`.
4. Restart SystemUI or reboot.
5. Open the app and adjust the binding limit slider.

## Build

```bash
git clone https://github.com/hxreborn/qs-boundless-tiles.git
cd qs-boundless-tiles
./gradlew assembleRelease
```

Requires JDK 21 and the Android SDK. Set `local.properties`:

```properties
sdk.dir=/path/to/android/sdk
```

Release signing is optional (`signing.properties` or `RELEASE_*` Gradle/env properties).

## License

[![GPLv3](https://img.shields.io/badge/License-GPLv3-blue?style=flat-square)](LICENSE)

GPLv3. See [LICENSE](LICENSE).
