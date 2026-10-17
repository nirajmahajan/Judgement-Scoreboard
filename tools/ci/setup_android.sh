#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/23.0/bin/sdkmanager" ]]; then
  archive="$(mktemp)"
  trap 'rm -f "$archive"' EXIT
  curl --fail --location --retry 2 --output "$archive" https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
  printf '%s  %s\n' 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8 "$archive" | sha256sum --check -
  unzip -q "$archive" -d "$ANDROID_HOME/cmdline-tools/staging"
  mv "$ANDROID_HOME/cmdline-tools/staging/cmdline-tools" "$ANDROID_HOME/cmdline-tools/23.0"
  rmdir "$ANDROID_HOME/cmdline-tools/staging"
fi
export PATH="$ANDROID_HOME/cmdline-tools/23.0/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$ANDROID_HOME" "$ANDROID_HOME" >> "$GITHUB_ENV"
  printf '%s\n%s\n%s\n' "$ANDROID_HOME/cmdline-tools/23.0/bin" "$ANDROID_HOME/platform-tools" "$ANDROID_HOME/emulator" >> "$GITHUB_PATH"
fi
