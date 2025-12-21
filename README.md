# QS Boundless Tiles

LSPosed module that keeps third-party Quick Settings tiles responsive on Android 13+.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-blue)
![Android API](https://img.shields.io/badge/API-33%2B-brightgreen)

## Overview

Android stock `SystemUI` [limits concurrent tile bindings to 3](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37), a cap unchanged since Android Nougat (2016). System tiles bypass this limit, but third-party tiles don't just time out, so they're often never allowed to start. With ten tiles installed, seven are strictly forbidden from binding at any time.

The result: tap a tile like Caffeine or Home Assistant, nothing happens for 3-5 seconds. Tapping an unbound tile triggers a [recalculation of bind allowance](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#85). Since Android 13, `CachedAppOptimizer` freezes processes with no active bindings. When a tile is unbound to free a slot, that process freezes. Tapping it later requires unfreeze + rebind.

This module raises the cap, letting all your tiles stay bound. No slot competition, no freezer delay.

<details>
<summary>Example: 10 third-party tiles installed</summary>

- Android picks 3 tiles to bind based on priority (last update time, pending clicks)
- You tap Tasker (unbound) → Android evicts lowest-priority tile, unfreezes Tasker, rebinds
- 3-second delay every time you interact with an unbound tile
</details>

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

## Build from source

```bash
./gradlew assembleDebug
```

Requires JDK 21 and Gradle 8.13.

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
