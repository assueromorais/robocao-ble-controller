#!/usr/bin/env bash
# Baixa o modelo de voz offline (Vosk, português, Apache 2.0) para app/src/main/assets/model-pt.
# O modelo não fica no repositório por ter ~50 MB.
set -euo pipefail

URL="https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
SHA256="6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/model-pt"

if [ -f "$DEST/final.mdl" ]; then
  echo "Modelo já existe em $DEST"
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Baixando modelo de voz…"
curl -fSL -o "$TMP/model.zip" "$URL"
echo "$SHA256  $TMP/model.zip" | sha256sum -c -

unzip -q "$TMP/model.zip" -d "$TMP"
mkdir -p "$(dirname "$DEST")"
mv "$TMP/vosk-model-small-pt-0.3" "$DEST"
echo "Pronto: $DEST"
