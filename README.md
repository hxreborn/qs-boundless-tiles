# QS Boundless Tiles

![Android CI](https://github.com/hxreborn/qs-boundless-tiles/actions/workflows/android-ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/API-33%2B-3DDC84?logo=android&logoColor=white)

Xposed module that raises the stock concurrent binding cap for third-party Quick Settings tiles on Android 13+.

## Background

Android limits third-party Quick Settings tiles to [3 concurrent bindings](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37) by default. When the QS panel opens, SystemUI recalculates allowances and unbinds tiles beyond the cap. On many ROMs, unbound services may be frozen, so tapping them can trigger an unfreeze/rebind delay.

Tiles still unbind [~30 seconds](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/external/TileServiceManager.java#53) after the panel closes.

## How it works

Hooks `SystemUI` to raise the binding cap so tiles stay bound while QS is open.

## Requirements

- Android 13+ (API 33+)
- LSPosed (API 100)
- Scope: `com.android.systemui`
- Root on Android 14+ for tile scanning and `Restart SystemUI`

Tested on Pixel and LineageOS (Android 16). Other OEM ROMs may vary.

## System Overhead

- **RAM:** More tiles stay bound while QS is open, so memory use scales with tile count. Once the panel closes and services unbind (~30s), memory returns to stock levels.
- **Battery:** No periodic work, wakelocks, or network. Event logging is a synchronous binder call with no background cost.
- **Stability:** The hook blocks memory-pressure downscaling of the binding limit. Aggressive settings on low-RAM devices may increase jank or OOM kills.

If you encounter issues, please [file an issue on GitHub](https://github.com/hxreborn/qs-boundless-tiles/issues/new/choose).

## Installation & Usage

1. Download the APK:

   <a href="../../releases"><img src=".github/assets/badge_github.png" height="60" alt="Get it on GitHub" /></a>
   <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22eu.hxreborn.qsboundlesstiles%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhxreborn%2Fqs-boundless-tiles%22%2C%22author%22%3A%22rafareborn%22%2C%22name%22%3A%22QS%20Boundless%20Tiles%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D"><img src=".github/assets/badge_obtainium.png" height="60" alt="Get it on Obtainium" /></a>

2. Install and enable the module in [LSPosed](https://github.com/JingMatrix/LSPosed).
3. Scope to `com.android.systemui`.
4. Restart SystemUI or reboot the device.
5. Open the app and adjust the concurrent binding limit slider.

## Build

```bash
git clone --recurse-submodules https://github.com/hxreborn/qs-boundless-tiles.git
cd qs-boundless-tiles
./gradlew buildLibxposed
./gradlew assembleRelease
```

Requires JDK 21 and Android SDK. Configure `local.properties`:

```properties
sdk.dir=/path/to/android/sdk
```

Optional release signing (`signing.properties` or `RELEASE_*` Gradle/env properties).

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
