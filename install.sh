#!/usr/bin/env bash
# Copies an APK (default: the debug build; or pass a path) to the phone's Download folder for installing with KingInstaller.
#
# Android Auto only lists sideloaded Car App Library apps whose install record says
# "initiated by the package installer, installed by the Play Store"; "Unknown sources" does not cover them.
# Plain `adb install` (even with -i com.android.vending) records the shell and the app disappears from AA.
# On Samsung One UI the installer also strips the Play Store flag from shell-sent install intents, so
# install from the phone: KingInstaller (classic method, all switches off) → Download/kiteauto.apk.
#
# Wireless adb: phone → Developer options → Wireless debugging → "Pair device with pairing code":
#   adb pair <ip>:<pair-port> <code>;  adb connect <ip>:<port>
set -euo pipefail
cd "$(dirname "$0")"
adb push "${1:-out/debug/app-debug.apk}" /sdcard/Download/kiteauto.apk
echo "Now install Download/kiteauto.apk with KingInstaller (classic method) on the phone."
echo "Check: adb shell dumpsys package com.jimytar.kiteauto | grep -E 'installerPackageName|initiatingPackageName'"
echo "  → installerPackageName=com.android.vending, initiatingPackageName=com.google.android.packageinstaller"
