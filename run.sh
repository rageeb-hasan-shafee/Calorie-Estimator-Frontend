#!/usr/bin/env bash
# Build, install, and launch the app on a connected/USB-debugging phone.
set -e

cd "$(dirname "$0")"
./gradlew installDebug
adb shell monkey -p com.example.capstone -c android.intent.category.LAUNCHER 1
