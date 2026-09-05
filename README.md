# Warm Tiles

Xposed module to make third-party Quick Settings tiles not suck.

Android keeps [three](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37) of them bound at once and unbinds the rest, so tapping one waits on an unfreeze and a rebind. Warm Tiles lifts that cap.

*Tiles still unbind [30 seconds](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/external/TileServiceManager.java#53) after the panel closes.

## License

[![GPL-3.0-only](https://img.shields.io/badge/LICENSE-GPL--3.0--only-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](https://github.com/hxreborn/qs-boundless-tiles/blob/main/LICENSE)
