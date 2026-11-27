# Swizy

Swizy is an Android privacy utility with Lowery avatar masking, manual screen
regions, per-app layouts, a Sibha counter, Qibla compass, and cached prayer times.

## Lowery

Enable Swizy's accessibility service, then turn Lowery on in the app. Automatic
avatar masking currently supports WhatsApp, WhatsApp Business, LinkedIn, and
Telegram variants. Choose a simple privacy line, a dynamic circle, or **Do
nothing (manual only)** in Settings.

To create a manual region, open Lowery and summon the floating control. Choose
**Add hidden area**, then drag over the region to cover. Multiple regions can
be saved independently for each app profile and screen orientation. Profiles
can be added or edited from the floating control and managed in Settings.

The floating control can add, move, resize, hide/show, or remove regions. Drag
it to the bottom close target to dismiss the current control session.

## Other features

- Arabic and English interface
- Optional app lock using device biometrics or screen credentials
- Quick Settings tile for pausing/resuming protection
- Optional battery-saver scan interval and privacy indicator
- Mask color, opacity, line thickness, and circle size controls
- JSON layout export/import
- Sibha counter with optional sound and vibration reminders
- Qibla compass and prayer times with a cached schedule for offline viewing
- Optional headset long-press time announcement
- Local crash log; logs are not uploaded automatically

## Build

The project uses Gradle and JDK 17. Build a debug APK with:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The app's artwork is stored as two source images in the repository root and
optimized Android drawable resources are generated during the build.
