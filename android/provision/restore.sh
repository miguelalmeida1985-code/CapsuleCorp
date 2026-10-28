#!/usr/bin/env bash
# Puts back an app removed by the debloat:  ./restore.sh com.miui.notes
# With no argument, puts back everything in debloat.txt.
set -euo pipefail
cd "$(dirname "$0")"
if [ $# -gt 0 ]; then list=("$@"); else mapfile -t list < <(sed 's/#.*//; s/ //g; /^$/d' debloat.txt); fi
for p in "${list[@]}"; do
  printf '%-50s ' "$p"; adb shell cmd package install-existing --user 0 "$p" 2>&1 | tr -d '\r' | head -1
done
