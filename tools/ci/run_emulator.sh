#!/usr/bin/env bash
# Explicit launch supports API 26 as well as newer images. No managed-device minimum.
set -euo pipefail
api="${1:?API level required}"
suite="${2:-critical}"
export ANDROID_SERIAL=emulator-5554
mkdir -p ci-artifacts/device
image_path="system-images;android-${api};google_apis;x86_64"
if [[ "$api" == 37 ]]; then
  image_path='system-images;android-37.2;google_apis_ps16k;x86_64'
fi
printf '%s\n' "$image_path" > ci-artifacts/device/image-package.txt
sdkmanager "platform-tools" "emulator" "$image_path"
printf 'no\n' | avdmanager create avd --force --name "judgement-${api}" --package "$image_path" --device pixel_5
if [[ "$suite" == extended ]]; then
  display_args=(-skin 1600x2560 -dpi-device 320)
else
  display_args=(-skin 1080x1920 -dpi-device 420)
fi
emulator -avd "judgement-${api}" -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect "${display_args[@]}" > ci-artifacts/device/emulator.log 2>&1 &
emulator_pid=$!
cleanup() {
  adb logcat -d -v threadtime > ci-artifacts/device/logcat.txt 2>&1 || true
  adb shell uiautomator dump /sdcard/final-hierarchy.xml >/dev/null 2>&1 || true
  adb pull /sdcard/final-hierarchy.xml ci-artifacts/device/final-hierarchy.xml >/dev/null 2>&1 || true
  adb exec-out screencap -p > ci-artifacts/device/final-screen.png 2>/dev/null || true
  adb pull /sdcard/judgement-test-evidence ci-artifacts/device/test-evidence >/dev/null 2>&1 || true
  adb pull /sdcard/Android/data/android.example.judgement.debug/files ci-artifacts/device/app-evidence >/dev/null 2>&1 || true
  adb emu kill >/dev/null 2>&1 || true
  kill "$emulator_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT
adb wait-for-device
booted=false
for ((attempt = 0; attempt < 180; attempt++)); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; then booted=true; break; fi
  sleep 2
done
[[ "$booted" == true ]] || { echo 'Emulator did not boot in 360 seconds'; exit 1; }
actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$actual_api" == "$api" ]] || { echo "Expected API $api, emulator reports $actual_api"; exit 1; }
image_directory="${image_path//;/\/}"
cp "$ANDROID_HOME/$image_directory/source.properties" ci-artifacts/device/image-source.properties
adb shell input keyevent 82
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$setting" 0
done
adb shell getprop > ci-artifacts/device/system-properties.txt
sdkmanager --list_installed > ci-artifacts/device/sdk-versions.txt
./gradlew --no-daemon --dependency-verification=strict createDebugAndroidTestCoverageReport
if [[ "$suite" == extended ]]; then
  adb shell settings put system font_scale 2.0
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  ./gradlew --no-daemon --dependency-verification=strict -Pandroid.testInstrumentationRunnerArguments.sequenceSeeds=100 createDebugAndroidTestCoverageReport
  python3 tools/ci/process_death.py
  seed=170026
  printf '%s\n' "$seed" > ci-artifacts/device/monkey-seed.txt
  adb logcat -c
  adb shell monkey -p android.example.judgement.debug -s "$seed" --throttle 100 --monitor-native-crashes 3000 > ci-artifacts/device/monkey.log 2>&1
  if grep -Eq 'CRASH:|ANR:|Monkey aborted' ci-artifacts/device/monkey.log; then exit 1; fi
fi

if [[ "$suite" == upgrade ]]; then
  ./gradlew --no-daemon --dependency-verification=strict assembleRelease
  python3 tools/ci/upgrade_install.py app/build/outputs/apk/release/app-release-unsigned.apk
fi
