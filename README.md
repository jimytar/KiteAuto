<p align="center"><img src="docs/icon.png" alt="KiteAuto" width="160"></p>

# KiteAuto

Watch YouTube and other video sites on your car's **Android Auto** screen.

KiteAuto is a lightweight web browser for Android Auto. It runs full screen next to your navigation app
(Waze or Google Maps keep navigating), works with the touch screen and the iDrive-style knob, and lets you
type on your phone instead of the car screen.

It is not on the Play Store and has to be installed manually.

## Install

You need an Android phone with Android Auto, and about five minutes.

1. **Download KiteAuto.** On your phone, open the
   [latest release](https://github.com/jimytar/KiteAuto/releases/latest) and download `KiteAuto-<version>.apk`.
2. **Install KingInstaller.** Download the latest APK from
   [KingInstaller releases](https://github.com/fcaronte/KingInstaller/releases) and install it (allow
   "install unknown apps" for your browser when asked).
3. **Install KiteAuto with KingInstaller.** Open KingInstaller, leave all switches off, select the KiteAuto
   APK from your Downloads and tap **Install**.
   *Don't install the APK by tapping it directly: Android Auto would then hide KiteAuto.*
4. **Show it in Android Auto.** Open the Android Auto settings on your phone → **Customize launcher** → enable
   **KiteAuto**.
5. **Connect to your car** and open KiteAuto from the Android Auto app launcher.

**Updating:** download the new APK from the latest release and install it with KingInstaller again (step 3).

## First-time setup (on the phone)

Open the KiteAuto app on your phone:

- Allow **notifications** and the **microphone** when asked (used for typing and voice search).
- Tap **Allow keyboard pop-up for the car** and turn on *Display over other apps*, so the phone keyboard opens by
  itself when you tap a search box in the car.
- Your **services** (YouTube is there by default) are listed here. Tap **Add service** to add more sites, for
  example your Jellyfin server.
- Tap **Open** next to a service to sign in on the phone. The car uses the same login.
- **Car page zoom** makes pages on the car screen smaller or larger.

## Using it in the car

- **Touch** the screen as on a tablet.
- **Swipe from the left edge** (or tap it) to show the toolbar: Back, Services, Search, Voice, Reload.
- **Typing:** tap a search box in the car and the keyboard opens on your phone. Type and press **Go**.
- **Knob:** tilt to move the pointer, hold the tilt to glide, press to click, turn to scroll.
- **Media buttons** on the steering wheel play/pause the video, and the Android Auto media card shows what's
  playing.

## Build from source

Requires Docker (with buildx).

```sh
./build.sh           # debug APK   → out/debug/
./build.sh release   # release APK → out/release/
```

The first build creates a signing key in `keystore/`. Keep it: updates must be signed with the same key.
Pushing to `main` builds and publishes a new GitHub release automatically.
