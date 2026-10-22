# Battery Float

A tiny Android app that keeps your battery level in a small floating bubble on top of every app.

- **Light:** plain Android code, no Flutter, no libraries. The release APK is expected to be a few hundred KB.
- **Frugal:** no timers and no polling. The bubble updates only when Android reports a battery change, and it stops listening while the screen is off.
- **Private:** the app has no internet permission, so it cannot send or receive anything online.
- **Update-safe:** every build is signed with the same permanent key and has a higher version number than the last, so a new APK installs over the old one without uninstalling.

## What the bubble does

| Gesture | Result |
| --- | --- |
| Drag | Move it anywhere (it can never get lost off-screen) |
| Tap | Open the app |
| Pinch with two fingers | Resize |
| Press and hold | Close it |

The ring and the number change colour with the level (green, yellow, orange, red) and a bolt appears while charging. Size, opacity, snap-to-edge, position and "restore after restart" are remembered.

## Get the APK with GitHub (no computer needed)

1. Create a new repository on GitHub (for example `battery-float`). **Private is recommended**, see [Keep the signing key safe](#keep-the-signing-key-safe).
2. Upload **everything in this folder** to the repository root, including the hidden `.github` folder, `keystore`, `gradle` and `app`. (Upload the contents, not the folder that contains them.)
3. Open the **Actions** tab. If GitHub asks, enable workflows. Pick **Build Battery Float APK** and press **Run workflow**. Every later push to `main` builds again by itself.
4. When the run turns green, open the **Releases** page of the repository (right side of the repository home page). Download `BatteryFloat-…apk` from the newest release and open it on your phone. The same file is also kept under the run's **Artifacts** for 30 days. If the run shows a warning that it could not create a release, open **Settings → Actions → General → Workflow permissions**, choose **Read and write permissions**, save and run the workflow again.
5. Open Battery Float, tap **Allow display over other apps**, switch it on for Battery Float, go back and tap **Start bubble**.

### If the first install says "App not installed"

You most likely have an older Battery Float on the phone that was signed with a different, random key (the earlier debug builds got a new key every time). Android never lets a different key replace an app, so **uninstall the old one once**, then install this APK. From then on every update installs over the previous one.

## Updating without uninstalling

1. Change the code or the `appVersionName` in `app/build.gradle.kts`.
2. Commit the change to `main`. The build runs and publishes a new release.
3. Install the new APK over the old one. Settings are kept, and if the bubble was on it comes back by itself after the update.

The build number (`versionCode`) is filled in automatically from the clock, so it always goes up. You never edit it.

## Keep the signing key safe

The key lives in `keystore/` (`battery-float.p12` and `keystore.properties`). It is what lets Android accept an update as coming from you.

- **Never delete or regenerate it.** A different key means uninstalling again and losing the app's settings.
- **Keep a backup copy** of the `keystore` folder somewhere outside GitHub.
- **Private repository:** the key is only visible to you. Fine for personal use.
- **Public repository:** anyone can read the key and could sign an APK that Android would accept as an update to *your* installed app. If the repository must be public, use your own key through repository secrets (below) and remove the `keystore` folder from the repository.

### Using your own key from GitHub secrets

Create a key (once) with Java's `keytool`:

```
keytool -genkeypair -keystore my.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 my.keystore
```

Then add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the output of the `base64` command |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `mykey` |
| `KEY_PASSWORD` | the key password (leave empty if it is the same as the keystore password) |

When `KEYSTORE_BASE64` exists the build uses it automatically instead of the bundled key. Switching from one key to another needs a one-time uninstall.

## Building on your own computer

You need JDK 17 and the Android SDK (platform 35, build-tools 34.0.0).

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Permissions

| Permission | Why |
| --- | --- |
| Display over other apps | Draw the bubble above other apps (granted once in Android settings) |
| Foreground service | Keep the bubble running in the background |
| Notifications (Android 13+) | Show the small "running" notification with a Stop button. The bubble works without it |
| Run at startup | Bring the bubble back after a restart or an update |

There is no internet, storage, contacts, location or camera permission.

## Troubleshooting

- **The bubble disappears after a while.** Some phone makers (Xiaomi, Huawei, Oppo, Vivo, Samsung and others) stop background apps aggressively. In Android settings set Battery Float's battery usage to *Unrestricted* / *No restrictions* and, where offered, allow auto-start.
- **No "running" notification.** On Android 13+ allow notifications for the app (there is a button on the app screen). The bubble itself does not need it.
- **The bubble does not come back after a restart.** Make sure *Restore after restart* is on in the app and that the bubble was running before you restarted.
- **Android says the app is not installed when updating.** The old copy was signed with a different key. Uninstall it once, then install again.

## How it saves power and space

- A single overlay view, drawn in software, redrawn only when the percentage or charging state changes.
- Battery updates come from Android's own broadcast. The receiver is registered only while the screen is on.
- The "running" notification is static and never updated.
- The app screen listens for battery changes only while it is visible.
- Dark interface with near-black surfaces, which switches off most pixels on OLED screens.
- No AndroidX, no Flutter engine, no libraries. Code and resources are shrunk in release builds.

## Project layout

```
app/src/main/kotlin/com/example/battery_float/
  MainActivity.kt            app screen
  FloatingBatteryService.kt  the bubble (overlay window, gestures, notification)
  BatteryGaugeView.kt        ring + number drawing (bubble and app screen)
  BatteryStatus.kt           reads the battery broadcast
  BootReceiver.kt            restores the bubble after restart / update
  AppSettings.kt             saved options
  Geometry.kt, Palette.kt    pure logic (unit tested)
  Ui.kt, Util.kt             small helpers
app/src/main/res/            text, theme, icons
app/src/test/                unit tests
keystore/                    permanent signing key (see above)
.github/workflows/build.yml  the build
```

## Versions

Android Gradle Plugin 8.7.2, Kotlin 2.1.0, Gradle 8.10.2, JDK 17, compileSdk / targetSdk 35, minSdk 21 (Android 5.0).
