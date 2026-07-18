#!/usr/bin/env bash
set -euo pipefail

# Downloads OpenWakeWord ONNX models into app assets
# Run this after cloning the repo

DEST="$(dirname "$0")/../app/src/main/assets/openwakeword"
BASE_URL="https://github.com/dscripka/openWakeWord/releases/download/v0.5.1"

mkdir -p "$DEST"

MODELS=(
    "melspectrogram.onnx"
    "embedding_model.onnx"
    "hey_jarvis_v0.1.onnx"
)

for model in "${MODELS[@]}"; do
    if [ -f "$DEST/$model" ]; then
        echo "✓ $model already exists"
    else
        echo "↓ Downloading $model..."
        curl -sL "$BASE_URL/$model" -o "$DEST/$model"
        echo "✓ $model downloaded"
    fi
done

if [ ! -f "$DEST/hey_lina_v1.onnx" ]; then
    echo ""
    echo "⚠ hey_lina_v1.onnx fehlt – Custom-Weckwort-Modell, wird nicht"
    echo "  heruntergeladen, sondern mit training/*.py trainiert"
    echo "  (siehe training/README.md) oder aus einem GitHub-Release kopiert."
fi

echo ""
echo "All models ready in $DEST"
ls -lh "$DEST"/*.onnx

# ---------------------------------------------------------------
# sherpa-onnx: AAR (TTS + STT Runtime) + Piper-Stimme + Whisper
# ---------------------------------------------------------------

SHERPA_VERSION="1.13.3"
LIBS_DIR="$(dirname "$0")/../app/libs"
ASSETS_DIR="$(dirname "$0")/../app/src/main/assets"
AAR="sherpa-onnx-static-link-onnxruntime-${SHERPA_VERSION}.aar"

mkdir -p "$LIBS_DIR"
if [ -f "$LIBS_DIR/$AAR" ]; then
    echo "✓ $AAR already exists"
else
    echo "↓ Downloading $AAR..."
    curl -sL "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/$AAR" -o "$LIBS_DIR/$AAR"
    echo "✓ $AAR downloaded"
fi

# Piper TTS – gewählte Stimme (ADR-016). Für neue A/B-Tests weitere Modelle
# ergänzen (z.B. vits-piper-de_DE-thorsten-medium, -kerstin-low, -miro-high)
# und in PiperTtsEngine.AVAILABLE_VOICES eintragen.
PIPER_MODELS=(
    "vits-piper-de_DE-dii-high"  # Default – OpenVoiceOS, CC BY-NC-SA (nur nicht-kommerziell!)
)
mkdir -p "$ASSETS_DIR/piper"
for PIPER_MODEL in "${PIPER_MODELS[@]}"; do
    if [ -d "$ASSETS_DIR/piper/$PIPER_MODEL" ]; then
        echo "✓ Piper voice $PIPER_MODEL already exists"
    else
        echo "↓ Downloading Piper voice $PIPER_MODEL..."
        curl -sL "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${PIPER_MODEL}.tar.bz2" | tar -xj -C "$ASSETS_DIR/piper"
        echo "✓ $PIPER_MODEL downloaded"
    fi
done

# Whisper STT – multilingual base (nur int8 wird behalten)
WHISPER_MODEL="sherpa-onnx-whisper-base"
if [ -d "$ASSETS_DIR/whisper/$WHISPER_MODEL" ]; then
    echo "✓ Whisper model already exists"
else
    echo "↓ Downloading Whisper model $WHISPER_MODEL (~207MB)..."
    mkdir -p "$ASSETS_DIR/whisper"
    curl -sL "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/${WHISPER_MODEL}.tar.bz2" | tar -xj -C "$ASSETS_DIR/whisper"
    # fp32-Varianten löschen – die App nutzt int8
    rm -f "$ASSETS_DIR/whisper/$WHISPER_MODEL/base-encoder.onnx" \
          "$ASSETS_DIR/whisper/$WHISPER_MODEL/base-decoder.onnx"
    echo "✓ Whisper model downloaded (int8)"
fi

echo ""
echo "Done. AAR in app/libs, Modelle in app/src/main/assets/{piper,whisper}"
