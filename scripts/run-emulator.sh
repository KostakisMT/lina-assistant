#!/bin/bash
# Lina – Emulator starten (Galaxy Tab A9+ Profil)
# Startet den Emulator mit Mikrofon-Passthrough vom MacBook

set -e

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"

AVD_NAME="LinaTablet"

echo "=== Lina Emulator ==="
echo "Profil: Galaxy Tab A9+ (1920x1200, API 33)"
echo "Mikrofon: MacBook Passthrough aktiv"
echo ""

# Emulator starten
#   -audio coreaudio     → macOS CoreAudio Backend (Pflicht auf M2 Mac)
#   -allow-host-audio    → Echtes Mikrofon-Audio durchreichen (sonst Testton!)
#   -no-snapshot-load    → Clean Boot (kein alter State)
#   -gpu host            → GPU-Beschleunigung via Metal (M2 Max)
#   -no-boot-anim        → Schnellerer Start
emulator -avd "$AVD_NAME" \
    -audio coreaudio \
    -allow-host-audio \
    -no-snapshot-load \
    -gpu host \
    -no-boot-anim \
    -skin 1200x1920 \
    "$@"
