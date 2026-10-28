# Setting up the wall tablet

From a computer with `adb` (Android platform tools) and bash (Linux, macOS, or Git Bash / WSL on Windows).

1. **Factory reset** the tablet. In the first-run setup, **skip every account**
   (Google and Xiaomi): Android refuses Device Owner while any account exists.
   Don't set a screen lock.
2. **Developer options:** Settings → About → tap *OS version* 7 times. Turn on
   *USB debugging*. On HyperOS, also turn on *Install via USB*. If that asks for
   a Xiaomi account, sign in to turn it on, then remove the account again
   (Settings → Accounts) before step 3.
3. Download `CapsuleOS-<version>.apk` from the latest release, plug the tablet in and run:
   ```sh
   ./provision.sh CapsuleOS-<version>.apk
   ```
   This installs the app, makes it the Device Owner (locked in, home screen,
   opens on boot), turns off the lock screen, keeps the screen on while charging,
   turns off animations and grants the camera.
4. In the app: the dashboard address, a **PIN**, and save.
5. **Debloat:** see what would go, then remove it:
   ```sh
   ./provision.sh --debloat           # just lists them
   ./provision.sh --debloat --apply   # removes them
   ```
   The list is [debloat.txt](debloat.txt); edit it before applying if you want to
   keep something. Apps are removed for the user only, so a factory reset or
   `./restore.sh <package>` (or `./restore.sh` for all of them) brings them back.

**WebView updates.** The dashboard runs in Android System WebView. Without an
account the Play Store can't update it, so now and then either sign in briefly to
update it (and remove the account again: Device Owner stays), or install a newer
WebView APK with `adb install -r`.

**Undo everything:** in the app's settings, *Remove Device Owner*, then uninstall
the app or factory reset.

The same commands by hand, if you'd rather not run the script:

```sh
adb install -r CapsuleOS-<version>.apk
adb shell dpm set-device-owner com.capsulecorp.kiosk/.AdminReceiver
adb shell locksettings set-disabled true
adb shell settings put global stay_on_while_plugged_in 7
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell pm uninstall -k --user 0 <package>      # per app in debloat.txt
```
