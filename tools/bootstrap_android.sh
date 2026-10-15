#!/usr/bin/env bash
# Generate the native platform using the user's installed SDK, not guessed
# Gradle wrapper binaries or incompatible hand-written plugin versions.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v flutter >/dev/null || { echo 'Install Flutter first; see README.md.' >&2; exit 1; }
flutter --version
flutter doctor -v
if [[ -d android ]]; then
  echo 'android/ already exists. Refusing to overwrite native configuration.' >&2
  exit 1
fi
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
flutter create --platforms=android --org=org.visionassist --project-name=vision_assist "$scratch/generated"
cp -R "$scratch/generated/android" ./android
mkdir -p android/app/src/main/kotlin/org/visionassist/vision_assist
cp platform/android/kotlin/MainActivity.kt android/app/src/main/kotlin/org/visionassist/vision_assist/MainActivity.kt
cp platform/android/AndroidManifest.xml android/app/src/main/AndroidManifest.xml
mkdir -p android/app/src/main/res/xml
cp platform/android/res/xml/network_security_config.xml android/app/src/main/res/xml/
# flutter_tts requires Android API 21+. API 23 is the initial project floor.
python3 - <<'PY'
from pathlib import Path
p = Path('android/app/build.gradle.kts')
if not p.exists():
    raise SystemExit('Expected a modern Kotlin DSL Flutter template. Review generated Gradle files manually.')
s = p.read_text()
if 'minSdk = flutter.minSdkVersion' not in s:
    raise SystemExit('Unrecognized Flutter template; inspect minSdk before proceeding.')
s = s.replace('minSdk = flutter.minSdkVersion', 'minSdk = maxOf(23, flutter.minSdkVersion)')
p.write_text(s)
PY
flutter pub get
printf '\nNative project generated. Review Gradle/JDK/SDK compatibility, then run flutter analyze and flutter test.\n'
