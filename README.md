# QS Boundless Tiles

LSPosed module that keeps third-party Quick Settings tiles responsive on Android 13+.

![Android CI](https://github.com/hxreborn/qs-boundless-tiles/actions/workflows/android-ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/API-33%2B-3DDC84?logo=android&logoColor=white)

## Background

Since Nougat (2016) Android has limited third party Quick Settings to [3 active tiles](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37). SystemUI manages tile bindings via a visibility-based priority queue, evicting services for non-visible tiles once the cap is reached. The Android 13 optimizer then freezes these evicted services which causes a ~3-5 second delay when they are eventually tapped.

<details>
<summary>Example: 10 third-party tiles installed</summary>

1. You pull down the QS panel
2. SystemUI binds the 3 most visible tiles using its visibility-based priority queue
3. The remaining 7 are evicted and frozen by CachedAppOptimizer
4. You tap an inactive tile and wait 3-5 seconds for the unfreeze-and-rebind cycle to complete
</details>

## How it works

This module uses the modern Xposed API to hook into `SystemUI` and raise the binding cap, allowing all your tiles to stay warm and responsive without the lag russian roulette.

## Requirements

- Android 13+ (API 33+)
- LSPosed (API 100)
- Scope: `com.android.systemui`

Tested on Pixel and LineageOS (Android 16). OEM ROMs (Samsung, Xiaomi, etc.) untested. Root required on Android 14+ for tile scanning and SystemUI restart.

## System Overhead

**Memory Footprint**: Each tile uses ~10-30 MB. Even with 20+ tiles active, the total RAM usage is virtually imperceptible on any 6GB+ device.

**Battery & Wakeclocks**: No idle drain or unnecessary wakeups. Power consumption depends entirely on what your active tiles do.

**Stability**: Higher binding limits increase active connections in SystemUI. Poorly coded tiles may cause issues on budget devices at extremely high limits.

If you encounter issues, please [file an issue on GitHub](https://github.com/hxreborn/qs-boundless-tiles/issues/new/choose).

## Installation & Usage

1. Download the APK:

   <a href="../../releases"><img src=".github/assets/badge_github.png" height="60" alt="Get it on GitHub" /></a>
   <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22eu.hxreborn.qsboundlesstiles%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhxreborn%2Fqs-boundless-tiles%22%2C%22author%22%3A%22rafareborn%22%2C%22name%22%3A%22QS%20Boundless%20Tiles%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D"><img src=".github/assets/badge_obtainium.png" height="60" alt="Get it on Obtainium" /></a>

2. Install and enable the module in [LSPosed](https://github.com/JingMatrix/LSPosed)
3. Scope to `com.android.systemui`
4. Restart SystemUI or reboot the device
5. Open the app and adjust the slider (grant root for auto-calculated optimal limit)

## Build

1. Install JDK 21, Android SDK

2. Configure SDK path in `local.properties`

   ```properties
   sdk.dir=/path/to/android/sdk
   ```

3. Build APK

   ```bash
   ./gradlew assembleRelease
   ```

4. (Optional) Sign release builds via `signing.properties` or environment variables

   ```properties
   keystore.path=/path/to/your/keystore.jks
   keystore.password=<keystore password>
   key.alias=<key alias>
   key.password=<key password>
   ```

   Unsigned builds remain reproducible.

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
