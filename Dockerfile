# syntax=docker/dockerfile:1
# Builds the KiteAuto APK. Used by ./build.sh (locally and in CI) via `docker buildx build --output`, so it works
# on runners that reach Docker through a socket (no bind mounts of the workspace).
#   targets: debug-out (app-debug.apk), release-out (KiteAuto-<version>.apk)
#   secret:  id=keystore (signing key; optional - without it the release APK is unsigned)
FROM ghcr.io/cirruslabs/android-sdk:36@sha256:f9b3ea9ed2b5fc9522adae82c7b4622ab7aa54207ef532c8e615a347dca08f31 AS build
WORKDIR /src
COPY . .
RUN ./tools/fetch-aauto.sh
ARG VERSION=0.0.0-dev
ARG TASK=assembleDebug
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=keystore,target=/run/secrets/keystore \
    if [ -s /run/secrets/keystore ]; then export KITEAUTO_KEYSTORE=/run/secrets/keystore; fi; \
    ./gradlew --no-daemon -PappVersion="$VERSION" "$TASK"

FROM scratch AS debug-out
COPY --from=build /src/app/build/outputs/apk/debug/*.apk /

FROM scratch AS release-out
COPY --from=build /src/app/build/outputs/apk/release/*.apk /
