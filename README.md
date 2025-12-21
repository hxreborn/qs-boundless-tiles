# QS Boundless Tiles

LSPosed module that keeps third-party Quick Settings tiles responsive on Android 13+.

![Android CI](https://github.com/hxreborn/qs-boundless-tiles/actions/workflows/android-ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/API-33%2B-3DDC84?logo=android&logoColor=white)

## Overview

Android stock `SystemUI` [limits concurrent tile bindings to 3](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37), a cap unchanged since Android Nougat (2016), when 2-4 GB RAM was standard. It made sense back then, but modern hardware has moved on while this constant stayed behind. System tiles bypass this limit, but for third-party tiles, only 3 are permitted to bind at any given time, with any additional tiles denied entry until a slot is manually freed.

When Android 13 introduced aggressive process freezing via `CachedAppOptimizer`, it turned this legacy binding cap into a modern performance bottleneck. Under these constraints, any tile beyond the allowed limit is actively frozen. Opening the QS panel triggers a bind priority recalculation, and any frozen tile you tap must go through an unfreeze-and-rebind cycle. This results in a 3-5 second delay on hardware that has more than enough RAM to keep every tile warm and responsive.

<details>
<summary>Example: 10 third-party tiles installed</summary>

1. You pull down the QS panel
2. Android calculates bind priority and keeps only 3 tiles warm (e.g., Caffeine, Home Assistant, Shizuku)
3. The other 7 (including Tasker) remain frozen
4. You tap Tasker: Android evicts the lowest-priority bound tile, unfreezes Tasker, rebinds it, 3 to 5 second delay
</details>

## How it works

This module uses the modern Xposed API to hook into this process and modify the logic governing third-party tile lifecycle management. It raises the binding cap, letting all your tiles stay bound. No slot competition, no lag russian roulette.

## Requirements

- LSPosed framework (API 100)
- Android 13+ (API 33+)

## Compatibility

Works on AOSP-based ROMs and Pixel devices. OEM-modified `SystemUI` (Samsung OneUI, Xiaomi MIUI, etc.) is untested.

## Installation

1. Install [LSPosed](https://github.com/JingMatrix/LSPosed) (JingMatrix fork recommended)
2. Download latest APK from [releases](../../releases)
3. Install APK and enable module in LSPosed Manager
4. Add `com.android.systemui` to module scope
5. Reboot or restart `SystemUI` (supported via the app's built-in feature, requires root)

## Usage

Once the module is enabled in LSPosed and you've rebooted (phone or `SystemUI`), simply open the app and select your preferred tile binding limit using the slider.

The **recommended value** is the sweet spot. It's calculated based on your currently active tiles and the maximum theoretical tiles available from all installed apps. You can go higher or lower depending on your needs.

## Trade-offs

**Memory**: Each bound tile process uses 10-30 MB of RAM. Modern Android 13+ devices ship with 6-12 GB RAM minimum, so even with 15-20 tiles bound, the impact is negligible. The app calculates the recommended limit based on your installed tiles.

**Battery**: Idle bound services use minimal power. Impact grows if tiles run listeners, timers, or foreground services.

**Stability**: Raising the cap increases the number of active `ServiceConnection` and `RemoteCallbackList` entries in `SystemUI`. While modern kernels handle this easily, keeping the limit to what you actually use (via the app's recommended slider) is better than maxing it out for no reason. Setting unreasonably high limits on older/budget devices with limited RAM may surface issues with poorly coded tiles that don't handle resources properly.

## Build

1. Install JDK 21, Android SDK

2. Configure SDK path in `local.properties`

   ```properties
   sdk.dir=/path/to/android/sdk
   ```

3. Release signing via `signing.properties` — optional, omit for reproducible builds

   ```properties
   keystore.path=/path/to/your/keystore.jks
   keystore.password=<keystore password>
   key.alias=<key alias>
   key.password=<key password>
   ```

4. Run build command

   ```bash
   ./gradlew assembleRelease
   ```

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
