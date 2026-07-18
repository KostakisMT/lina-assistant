#!/bin/bash
# Lina – Build + Install auf laufenden Emulator
# Voraussetzung: Emulator läuft bereits (run-emulator.sh)

set -e

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")/.."

echo "=== Lina Build & Deploy ==="

# 1. Build
echo "[1/3] Building APK..."
./gradlew assembleDebug

# 2. Warten auf Emulator
echo "[2/3] Warte auf Emulator..."
adb wait-for-device
adb shell getprop sys.boot_completed | grep -q "1" || {
    echo "  Warte auf vollständigen Boot..."
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 2
    done
}

# 3. Install + Start
echo "[3/3] Installiere und starte Lina..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.lina/.ui.launcher.LauncherActivity

echo ""
echo "=== Lina läuft auf dem Emulator ==="
echo "Sprich ins MacBook-Mikrofon um zu testen."
