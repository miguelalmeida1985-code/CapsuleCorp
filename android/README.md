# CapsuleOS app (tablet and TV)

A small Android app that shows the CapsuleOS dashboard full screen. One APK serves
the wall tablet and the TV (it also shows up in an Android TV's launcher).

- **Full screen, always on.** No system bars, the screen never sleeps, and the page
  reloads by itself if the connection drops.
- **Same version everywhere.** The app's version is CapsuleOS's own
  (`panalume/config.yaml`), and it adds `?app_version=` to the address, so the corner
  badge reads e.g. `v0.1.0 · app 0.1.0` on the tablet, the TV and the NUC.
- **Tablet:** opens the dashboard with `?noedit`. For the child lock, add the
  dashboard's **Child lock** card; the app has its own padlock too, off by default
  so there aren't two.
- **Lights up when someone comes near.** After a while with nobody around (2 min
  by default) the screen goes dark, and it lights up again when the front camera
  sees movement or the screen is touched. The camera looks at a tiny picture a few
  times a second, reduced to a 24 × 16 grid of brightness values; nothing is saved
  or sent. The screen never really turns off (that would stop the camera): the
  backlight goes to zero behind a black cover. In the dark only a touch wakes it.
- **TV:** a normal app that the TV's own launcher opens (it never asks to be the
  home screen there, and kiosk mode is off). Opens with `?tv`. The remote's arrows and OK go to the page; Back closes
  pop-ups and never leaves the app.
- **Kiosk mode:** keeps the app on screen (see Device Owner below).

## Install

Every CapsuleOS release on GitHub has `CapsuleOS-<version>.apk` attached. On a
pull request, the APK is under the **Android app** run's artifacts.

```sh
adb install -r CapsuleOS-0.1.0.apk
```

Open **CapsuleOS**, enter the dashboard's address (e.g. `http://192.168.1.10:8099/`,
the add-on's direct port) and save. If the add-on has a *Direct access key*, add it
once: `http://192.168.1.10:8099/?key=…`. The app keeps the cookie it gets back. Leave the mode on **Automatic**: it picks TV on a
TV and tablet otherwise.

**Settings later:** five quick taps in the top-right corner (tablet), or five quick
presses of Back / the Menu key (TV). A PIN can protect them.

## Setting up the tablet

[provision/](provision/README.md) has a script that does the whole setup over ADB:
Device Owner, no lock screen, always on, and an optional debloat of the apps a wall
screen doesn't need.

## Device Owner (full lockdown, optional)

Without it, Android's screen pinning keeps the app on screen, but a grown-up who
knows the gesture can leave. As Device Owner the app is locked in: no status bar,
no home or recents, no Android lock screen, the screen stays on while charging, the
camera is granted without asking, and it is the home screen, so it opens on boot.

Android only allows this on a device with **no accounts** (no Google, no Xiaomi
account), so do it right after a factory reset, skipping the accounts:

1. Turn on Developer options and USB debugging. On Xiaomi, also turn on
   *Install via USB* (it may ask for a Xiaomi account: sign in, install, then
   remove the account again).
2. `adb install CapsuleOS-0.1.0.apk`
3. `adb shell dpm set-device-owner com.capsulecorp.kiosk/.AdminReceiver`

To undo it: settings → **Remove Device Owner**.

## Updates

Install the new APK over the old one (`adb install -r …`). That only works when
both are signed with the same key, so add these repository secrets once
(Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 capsuleos.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore's password |
| `ANDROID_KEY_ALIAS` | the key's alias |
| `ANDROID_KEY_PASSWORD` | the key's password |

```sh
keytool -genkeypair -v -keystore capsuleos.jks -alias capsuleos -keyalg RSA -keysize 2048 -validity 10000
```

Without the secrets the APK is signed with a throwaway debug key, which installs
fine but can't update an APK from another build (uninstall first).

## Build

```sh
cd android && ./gradlew assembleRelease   # needs JDK 17+ and the Android SDK
```
