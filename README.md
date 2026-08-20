# Display Fault Simulator

[繁體中文](README.zh-TW.md) | English

Display Fault Simulator is an Android app that recreates common panel-failure visuals in a transparent, system-wide overlay. Lines and damage effects are display-only: taps, swipes, and system gestures continue to reach the app underneath.

- Package: `tw.chehu.displayfaultsimulator`
- Languages: English and Traditional Chinese (`zh-TW`), selected from the Android system or per-app language setting
- Android: 8.0+ (API 26), targeting Android 16 (API 36)

> For demonstrations, UI testing, photography, and harmless pranks only. The app does not diagnose or repair a physical display.

## Screenshots

These screenshots were captured from the current v1.4.1 build running on an Android 16 emulator. The overlay screenshot confirms that the line reaches the physical top and bottom display bounds, including the status and navigation-bar areas.

| Full-screen overlay | Scene editor |
| --- | --- |
| <img src="docs/screenshots/overlay-en.png" width="300" alt="Green OLED line spanning the full Android home screen"> | <img src="docs/screenshots/editor-en.png" width="300" alt="Scene editor with direct line dragging"> |

| English interface | Traditional Chinese interface |
| --- | --- |
| <img src="docs/screenshots/main-en.png" width="300" alt="Display Fault Simulator English interface"> | <img src="docs/screenshots/main-zh-TW.png" width="300" alt="螢幕故障模擬器繁體中文介面"> |

## Highlights

- Up to 12 independent vertical lines per scene
- Per-line color, width, opacity, glow, flicker, position, and timed horizontal movement
- Direct line dragging in the visual scene editor
- Layerable cracked glass, dead/stuck pixels, panel liquid damage, OLED ghosting, and LCD scanlines
- Editable custom scenes with create, duplicate, rename, and delete actions
- Countdown start and automatic stop timers
- Quick Settings tile and notification stop action
- Foreground service, optional boot recovery, and battery-optimization guidance
- Full touch-through overlay that does not capture screen content or intercept input

## Built-in preset library

- Classic OLED green line
- Multiple pink lines
- Impact-cracked panel
- Screen liquid damage
- Heavy dead pixels
- Loose display cable
- Aged LCD scanlines
- Light damage
- Severe damage

Applying a preset creates a new editable scene and leaves existing scenes unchanged.

## Install

1. Download the APK from [GitHub Releases](https://github.com/ahui3c/DisplayFaultSimulator_Android/releases).
2. Allow installation from the browser or file manager when Android asks.
3. Open the app and grant **Display over other apps**.
4. Choose or edit a scene, then tap **Start or schedule display**.
5. Stop the effect from the app, persistent notification, or Quick Settings tile.

The APK attached to the current GitHub release is development-signed for direct sideload testing. A future store or production distribution should use a private production signing key.

## Permissions and privacy

| Permission | Purpose |
| --- | --- |
| Display over other apps | Draw the transparent damage overlay above other apps |
| Notifications | Keep the user-controlled foreground service visible and provide Stop |
| Foreground service | Keep an active or scheduled effect running |
| Boot completed | Optionally restore an unfinished effect after restart |

The app has no internet permission, does not capture screen content, and does not collect or transmit personal data.

## Build from source

Requirements: Android Studio/JDK 17 and Android SDK 36.

```powershell
.\gradlew.bat assembleDebug lintDebug
```

For the optimized unsigned release variant:

```powershell
.\gradlew.bat assembleRelease
```

Sign the release APK with your own private production key before distribution.

## Android limitations

- Manually force-stopping the app prevents Android from restarting it until the user opens it again.
- Some manufacturers require separate auto-start, background-run, or recent-app-lock settings.
- Battery-optimization exemption improves persistence but cannot guarantee that every vendor will keep the process alive.
- Security-sensitive system windows, such as permission dialogs, may appear above the overlay by Android design.
- Since v1.4.0 the package is `tw.chehu.displayfaultsimulator`; it installs separately from the older `tw.chehu.fungreenline` package and does not import its scenes.

## License

MIT License. See [LICENSE](LICENSE).
