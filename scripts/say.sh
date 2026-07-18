#!/bin/bash
# Lina Debug – Sprachbefehl simulieren
# Nutzung: ./scripts/say.sh "ruf boris an"

export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [ -z "$1" ]; then
    echo "Nutzung: $0 \"Sprachbefehl\""
    echo "Beispiele:"
    echo "  $0 \"ruf boris an\""
    echo "  $0 \"was gibt es neues\""
    echo "  $0 \"schreib ulla: bin gleich da\""
    echo "  $0 \"spiel hörbuch ab\""
    echo "  $0 \"stopp\""
    exit 1
fi

adb shell "am broadcast -a dev.lina.DEBUG_INPUT --es text '$1'"
