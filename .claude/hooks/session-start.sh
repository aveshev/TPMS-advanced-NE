#!/bin/bash
# Installs the Android SDK so Gradle builds, Detekt, lint and unit/Paparazzi tests work in
# Claude Code on the web sessions. Idempotent: every step is skipped once already done, and the
# container state is cached after this hook, so later sessions start with everything in place.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

# 1. Command-line tools (sdkmanager)
if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools in $ANDROID_HOME" >&2
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/tools.zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 2. Licenses, so AGP can also download build-tools it needs on its own
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

# 3. The platform compileSdk points to, read from the version catalog. Recent platforms are
#    published as "android-37.0" rather than "android-37", both are tried.
compile_sdk="$(sed -n 's/^sdk-compile *= *"\(.*\)"/\1/p' "$PROJECT_DIR/gradle/libs.versions.toml")"
if ! ls -d "$ANDROID_HOME/platforms/android-$compile_sdk"* >/dev/null 2>&1; then
  echo "Installing Android platform $compile_sdk" >&2
  "$SDKMANAGER" "platforms;android-$compile_sdk.0" >/dev/null 2>&1 \
    || "$SDKMANAGER" "platforms;android-$compile_sdk" >/dev/null
fi
if [ ! -d "$ANDROID_HOME/platform-tools" ]; then
  "$SDKMANAGER" "platform-tools" >/dev/null
fi

# 4. Point Gradle at the SDK (local.properties is gitignored) and export it to the session
echo "sdk.dir=$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
fi

# 5. Maven Central answers 429 (Too Many Requests) to these containers, so Gradle queries
#    Google's mirror of it first. The plugin portal is moved last since it redirects to Central.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/central-mirror.gradle.kts" <<'KTS'
// Written by .claude/hooks/session-start.sh: Maven Central rate-limits Claude Code on the web
// containers, query Google's mirror of it first
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
settingsEvaluated {
    // An empty plugin repository list implicitly means the portal, which adding the mirror would drop
    if (pluginManagement.repositories.isEmpty()) pluginManagement.repositories.gradlePluginPortal()
    listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach { repos ->
        val repo = repos.maven { url = uri(mirror); name = "CentralMirror" }
        repos.remove(repo)
        repos.addFirst(repo)
        // The portal redirects to the rate-limited Central, only try it last
        repos.filter { it.name == "Gradle Central Plugin Repository" }.forEach { portal ->
            repos.remove(portal)
            repos.addLast(portal)
        }
    }
}
KTS

# 6. Warm up: downloads the Gradle distribution and builds buildSrc, so the cached container
#    doesn't redo it at the first Gradle command
cd "$PROJECT_DIR"
ANDROID_HOME="$ANDROID_HOME" ./gradlew --quiet help >/dev/null
