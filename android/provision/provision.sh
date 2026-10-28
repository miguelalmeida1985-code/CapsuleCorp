#!/usr/bin/env bash
# Sets up a freshly reset tablet as a CapsuleOS wall screen, over ADB from a computer.
#
#   ./provision.sh CapsuleOS-0.3.0.apk            # install, Device Owner, no lock screen, always on
#   ./provision.sh --debloat                      # list the apps debloat would remove (changes nothing)
#   ./provision.sh --debloat --apply              # remove them (for this user only; see restore.sh)
#
# Needs: adb, USB debugging on, and NO accounts on the tablet (Google or Xiaomi),
# otherwise Android refuses Device Owner. See README.md in this folder.
set -euo pipefail
cd "$(dirname "$0")"

PKG=com.capsulecorp.kiosk
APK=""
DEBLOAT=0
APPLY=0
for a in "$@"; do
  case "$a" in
    --debloat) DEBLOAT=1 ;;
    --apply) APPLY=1 ;;
    *.apk) APK="$a" ;;
    *) echo "Unknown option: $a" >&2; exit 2 ;;
  esac
done

adb get-state >/dev/null || { echo "No device: plug the tablet in and allow USB debugging." >&2; exit 1; }
ash() { adb shell "$@"; }

if [ "$DEBLOAT" = 1 ]; then
  installed=$(ash pm list packages --user 0 | sed 's/^package://' | tr -d '\r')
  n=0
  while read -r p; do
    p=${p%%#*}; p=${p// /}
    [ -z "$p" ] && continue
    grep -qx "$p" <<<"$installed" || continue
    n=$((n + 1))
    if [ "$APPLY" = 1 ]; then
      printf '%-50s ' "$p"; ash pm uninstall -k --user 0 "$p" | tr -d '\r'
    else
      echo "would remove  $p"
    fi
  done < debloat.txt
  [ "$APPLY" = 1 ] || echo "$n apps would be removed. Run again with --apply to remove them."
  exit 0
fi

if [ -n "$APK" ]; then
  echo "Installing $APK"
  adb install -r "$APK"
fi

echo "Making CapsuleOS the Device Owner"
ash dpm set-device-owner "$PKG/.AdminReceiver" || {
  echo "Refused: remove every account (Settings > Accounts) and try again." >&2
  exit 1
}

echo "No lock screen, screen on while charging, no animations"
ash locksettings set-disabled true || echo "  (a PIN or pattern is set: remove it in Settings > Lock screen)"
ash settings put global stay_on_while_plugged_in 7
ash settings put global window_animation_scale 0
ash settings put global transition_animation_scale 0
ash settings put global animator_duration_scale 0
ash pm grant "$PKG" android.permission.CAMERA || true

echo "Opening CapsuleOS"
ash am start -n "$PKG/.MainActivity" >/dev/null
echo "Done. Enter the dashboard address in the app, set a PIN, then run: ./provision.sh --debloat"
