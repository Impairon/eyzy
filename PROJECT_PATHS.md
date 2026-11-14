# swizy project paths

The Android module is under `app/`.

| Purpose | Path |
| --- | --- |
| Main activity and navigation | `app/src/main/java/com/abo/avatarblackout/MainActivity.kt` |
| Accessibility masking service | `app/src/main/java/com/abo/avatarblackout/AvatarService.kt` |
| Shared preferences | `app/src/main/java/com/abo/avatarblackout/Prefs.kt` |
| App permissions and service declarations | `app/src/main/AndroidManifest.xml` |
| Accessibility service configuration | `app/src/main/res/xml/accessibility_config.xml` |
| Theme and colors | `app/src/main/res/values/themes.xml`, `app/src/main/res/values/colors.xml` |
| Launcher icon | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |

Build and install from the Android project root:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The current working branch is `pushy`.
