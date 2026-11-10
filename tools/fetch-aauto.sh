#!/usr/bin/env bash
# Fetches the unofficial Android Auto SDK (aauto.aar, Google's never-published CarActivity SDK) from the
# Fermata repo into app/libs/. Not committed here: it is proprietary Google code. Pinned by SHA-256.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/libs/aauto.aar
SHA=99337c3b591ac9670c12b508da38886aedba61dd494f39f5f166f02580ec584b
URL=https://github.com/AndreyPavlenko/Fermata/raw/master/fermata/lib/auto/aauto.aar
if [[ -f $OUT ]] && echo "$SHA  $OUT" | sha256sum -c --status; then exit 0; fi
mkdir -p app/libs
curl -fsSL -o "$OUT.tmp" "$URL"
echo "$SHA  $OUT.tmp" | sha256sum -c --quiet
mv "$OUT.tmp" "$OUT"
