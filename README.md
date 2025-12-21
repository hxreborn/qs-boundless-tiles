# QS Boundless Tiles

LSPosed module that keeps third-party Quick Settings tiles responsive on Android 13+.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue)
![Android API](https://img.shields.io/badge/API-33%2B-brightgreen)

## Overview

Stock SystemUI limits concurrent tile bindings to 3 (`DEFAULT_MAX_BOUND = 3`). This limit dates back to [Nougat](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d%5E%21/#F5) when phones had 2-4 GB RAM and each bound tile added steady memory overhead.

If you run more than three third-party tiles, cold-start taps often feel dead because SystemUI still enforces that decade-old, hardcoded cap.

With 12 third-party tiles installed:
- 3 tiles bind, 9 get `setBindAllowed(false)` immediately
- Tapping an unbound tile triggers slot competition: request slot → kick another tile → bind → respond
- This happens on every tap to an unbound tile, causing 3-5 second delays

Android 13+ made this worse: the freezer (CachedAppOptimizer) became stricter, so unbound tile processes get frozen. On tap, the OS must unfreeze + rebind.

## How It Works

Hooks into `TileServices.mMaxBound` to raise the limit to your chosen value. With enough slots, all tiles bind when QS opens. No competition, no kicking, instant response.

The module also blocks SystemUI from reducing the limit under memory pressure.

## Requirements

- Android 13+ (API 33+)
- LSPosed framework (JingMatrix fork recommended)

## Compatibility

Tested on Pixel devices running stock Pixel UI and AOSP-based ROMs (Evolution X, LineageOS). Works as expected.

OEM-modified SystemUI (Samsung OneUI, Xiaomi MIUI, etc.) is untested. These vendors often rewrite QS tile handling entirely, and may not even have the binding limit problem—or they've made it worse in creative new ways.

## Trade-offs

**Memory**: Each bound tile process holds RAM (10-30 MB per app). Higher cap = more resident memory. LMKD may kill background apps sooner under pressure.

**Battery**: Idle bound services are cheap. Impact grows if tiles run listeners, timers, or foreground services.

**Stability**: More bindings = more binder connections. Extreme values on low-RAM devices could expose buggy tiles that leak resources.

Recommendation: set limit to 10-20.

## Installation

1. Install [LSPosed](https://github.com/JingMatrix/LSPosed)
2. Download latest APK from [releases](../../releases)
3. Install APK and enable module in LSPosed Manager
4. Add `com.android.systemui` to module scope
5. Restart SystemUI or reboot

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 21 and Gradle 8.13.

## License

![GNU badge](https://img.shields.io/badge/-GNU-555?style=flat&logo=gnu&logoColor=white)
![GPLv3 badge](https://img.shields.io/badge/-GPLv3-c62828?style=flat)

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
