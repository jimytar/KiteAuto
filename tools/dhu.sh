#!/usr/bin/env bash
# Runs Google's Desktop Head Unit (Linux build, shows via WSLg) against the phone over adb.
# Phone first: Android Auto settings → ⋮ → Start head unit server.
# The DHU skips Android Auto's install-source check, so plain `adb install` builds work here (not in the car).
set -euo pipefail
DHU=${DHU:-$HOME/.local/share/android-dhu}
adb forward tcp:5277 tcp:5277
exec "$DHU/desktop-head-unit" -c "$(cd "$(dirname "$0")" && pwd)/dhu-bmw.ini" "$@"
