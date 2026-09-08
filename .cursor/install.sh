#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for the Android AI Automation Agent.
# Installs the JDK 17 toolchain and Android SDK if they are missing, points
# Gradle at the SDK, and warms the build so backend + shared + APK are ready.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JDK17_DIR="/usr/lib/jvm/java-17-openjdk-amd64"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"

# --- JDK 17 (Gradle toolchain target) ---------------------------------------
if [ ! -d "$JDK17_DIR" ]; then
  echo "[install] Installing OpenJDK 17..."
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk unzip
fi

# --- Android SDK (compileSdk 35) --------------------------------------------
SDKMGR="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMGR" ]; then
  echo "[install] Installing Android SDK command-line tools..."
  sudo mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  sudo chown -R "$(id -u):$(id -g)" "$ANDROID_SDK_ROOT"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest" /tmp/cmdline-extract
  unzip -q "$tmp_zip" -d /tmp/cmdline-extract
  mv /tmp/cmdline-extract/cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -f "$tmp_zip"
fi

if [ ! -d "$ANDROID_SDK_ROOT/platforms/android-35" ]; then
  echo "[install] Installing Android platform + build tools..."
  yes | "$SDKMGR" --licenses >/dev/null 2>&1 || true
  "$SDKMGR" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
fi

# --- Gradle SDK location (local.properties is gitignored) -------------------
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

# --- Warm the build so deps are cached and artifacts exist ------------------
chmod +x gradlew
export JAVA_HOME="$JDK17_DIR"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT
./gradlew :shared:assemble :backend:build :android:app:assembleDebug -x test --no-daemon

echo "[install] Done. Backend, shared module, and debug APK are built."
