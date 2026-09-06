#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21)
    export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_HOME
fi
if [ "$#" -eq 0 ]; then
    set -- :app:assembleDebug :spotify-module:assembleRelease
fi
exec ./gradlew "$@"
