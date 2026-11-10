#!/usr/bin/env bash
# Builds the KiteAuto APK in Docker (no local JDK/SDK) via the Dockerfile; the same command runs in CI.
#   ./build.sh            debug APK  -> out/debug/app-debug.apk   (DHU testing, KingInstaller)
#   ./build.sh release    release APK -> out/release/KiteAuto-<version>.apk
# VERSION (env) overrides the version; default: next release version from Conventional Commits + "-dev".
# Signing key: KITEAUTO_KEYSTORE_FILE (env, CI) or keystore/debug.keystore (created locally on first run).
set -euo pipefail
cd "$(dirname "$0")"
TYPE="${1:-debug}"
case "$TYPE" in
  debug) TASK=assembleDebug ;;
  release) TASK=assembleRelease ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

if [[ -z "${VERSION:-}" ]]; then
  next="$(scripts/next-version.sh 2>/dev/null || true)"
  VERSION="${next:-$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//' || true)}"
  VERSION="${VERSION:-0.0.0}-dev"
fi

KEY="${KITEAUTO_KEYSTORE_FILE:-keystore/debug.keystore}"
# One-time locally: a persistent signing key, so installs upgrade instead of failing on signature mismatch.
if [[ ! -f "$KEY" && -z "${CI:-}" ]]; then
  mkdir -p "$(dirname "$KEY")"
  docker run --rm -u "$(id -u):$(id -g)" -v "$PWD":/src -w /src ghcr.io/cirruslabs/android-sdk:36 \
    keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
secret=()
[[ -f "$KEY" ]] && secret=(--secret "id=keystore,src=$KEY")

echo "Building $TYPE $VERSION"
rm -rf "out/$TYPE"
docker buildx build --network host --target "$TYPE-out" --build-arg VERSION="$VERSION" --build-arg TASK="$TASK" \
  "${secret[@]}" --output "type=local,dest=out/$TYPE" .

if [[ $TYPE == release ]]; then
  apk="$(ls out/release/*.apk)"
  [[ $apk == *unsigned* ]] && echo "WARNING: no signing key - release APK is unsigned" >&2
  mv "$apk" "out/release/KiteAuto-$VERSION.apk"
fi
ls -l "out/$TYPE"/*.apk
