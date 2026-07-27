#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

required=(
  .github/workflows/android.yml
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.jar
  scripts/build-release.sh
)
for path in "${required[@]}"; do
  [[ -f "$path" ]] || { echo "Missing required file: $path" >&2; exit 1; }
done
[[ -x gradlew ]] || { echo "gradlew is not executable" >&2; exit 1; }

grep -q 'applicationId = "com.darius.unison"' app/build.gradle.kts
grep -q 'appVersionName = "1.0.0"' gradle/libs.versions.toml
grep -q 'appVersionCode = "1"' gradle/libs.versions.toml
grep -q 'minSdk = "30"' gradle/libs.versions.toml
grep -q 'targetSdk = "36"' gradle/libs.versions.toml
grep -q 'compileSdk = "36"' gradle/libs.versions.toml
grep -q 'JavaVersion.VERSION_17' app/build.gradle.kts
grep -q 'JvmTarget.JVM_17' app/build.gradle.kts
grep -q 'isMinifyEnabled = true' app/build.gradle.kts
grep -q 'isShrinkResources = true' app/build.gradle.kts
grep -q 'assembleRelease' scripts/build-release.sh
! grep -q 'bundleRelease' scripts/build-release.sh
grep -q 'actions/upload-artifact@v7' .github/workflows/android.yml
grep -q 'app/build/outputs/apk/debug/app-debug.apk' .github/workflows/android.yml
grep -q 'app/build/outputs/apk/release/app-release-unsigned.apk' .github/workflows/android.yml

# Runtime architecture and safety invariants.
grep -q 'PagingSource<Int, TrackEntity>' app/src/main/java/com/darius/unison/storage/Database.kt
grep -q 'Pager(' app/src/main/java/com/darius/unison/library/TrackRepository.kt
grep -q 'collectAsLazyPagingItems' app/src/main/java/com/darius/unison/ui/UnisonApp.kt
grep -q 'const val PROTOCOL_VERSION = 1' app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt
grep -q 'MAX_QUEUE_ITEMS = 1_000' app/src/main/java/com/darius/unison/room/RoomReducer.kt
grep -q 'MAX_CONCURRENT_INCOMING = 24' app/src/main/java/com/darius/unison/network/PeerServer.kt
grep -q 'sha256Hex(target) == target.name' app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt
grep -q 'MAX_FILE_BYTES = 4 \* 1024 \* 1024' app/src/main/java/com/darius/unison/library/M3uCodec.kt
grep -q 'NetworkAddressPolicy.parseAllowedIpv4' app/src/main/java/com/darius/unison/ui/MainViewModel.kt
grep -q 'playerWindow(' app/src/main/java/com/darius/unison/room/PlaybackQueuePolicy.kt
grep -q 'capacity = 64' app/src/main/java/com/darius/unison/app/RoomCommandBus.kt
grep -q 'capacity = 128' app/src/main/java/com/darius/unison/room/RoomRuntime.kt

# The installed app is local-only. INTERNET remains necessary for LAN sockets.
if grep -R -n -E 'com\.google\.android\.gms|com\.google\.firebase|play-services|firebase-' \
    app/build.gradle.kts gradle/libs.versions.toml app/src/main/java; then
  echo 'Hosted Google runtime service dependency found.' >&2
  exit 1
fi
if grep -R -n -E 'https?://' app/src/main/java; then
  echo 'Hard-coded remote endpoint found.' >&2
  exit 1
fi
if grep -R -n -E 'billingclient|asset-delivery|feature-delivery|review-ktx|update-ktx' \
    app/build.gradle.kts gradle/libs.versions.toml app/src/main/java; then
  echo 'Store-dependent runtime component found.' >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

for path in Path('app/src/main/res').rglob('*.xml'):
    ET.parse(path)

manifest = ET.parse('app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
application = manifest.find('application')
assert application is not None, 'Application manifest node is missing'
assert application.get(android + 'allowBackup') == 'false', 'Android backup must remain disabled'
assert application.get(android + 'usesCleartextTraffic') == 'false', 'Cleartext traffic must remain disabled'

service = next(
    (node for node in application.findall('service')
     if node.get(android + 'name') == '.playback.UnisonRoomService'),
    None,
)
assert service is not None, 'UnisonRoomService is missing'
assert service.get(android + 'exported') == 'true', 'MediaSessionService must be exported for system controllers'
types = set((service.get(android + 'foregroundServiceType') or '').split('|'))
assert {'mediaPlayback', 'connectedDevice'} <= types, 'Foreground service types are incomplete'
assert any(
    action.get(android + 'name') == 'androidx.media3.session.MediaSessionService'
    for intent_filter in service.findall('intent-filter')
    for action in intent_filter.findall('action')
), 'MediaSessionService action is missing'

version_file = Path('gradle/libs.versions.toml').read_text()
assert len(re.findall(r'appVersionName\s*=\s*"1\.0\.0"', version_file)) == 1, 'Application version is inconsistent'
text_suffixes = {'.kt', '.kts', '.md', '.toml', '.xml', '.sh', '.yml', '.yaml', '.properties'}
assert '0.1.0' not in ''.join(
    p.read_text(errors='ignore')
    for p in Path('.').rglob('*')
    if p.is_file() and not {'.git', '.gradle', '.kotlin', '.idea', 'build'}.intersection(p.parts)
    and p.suffix in text_suffixes and p.as_posix() != 'scripts/check-static.sh'
), 'Legacy application version found'
print('XML, manifest, version, and local-runtime policy checks passed.')
PY

MARKERS_FILE="$(mktemp "${TMPDIR:-/tmp}/unison-static-markers.XXXXXX")"
trap 'rm -f "$MARKERS_FILE"' EXIT
if grep -R -n -E 'TODO([: (]|$)|FIXME([: (]|$)|HACK([: (]|$)|XXX([: (]|$)|NotImplementedError' \
    app/src/main app/src/test scripts docs README.md --exclude='check-static.sh' >"$MARKERS_FILE"; then
  cat "$MARKERS_FILE" >&2
  echo 'Unresolved development marker found.' >&2
  exit 1
fi

echo 'Static repository checks passed.'
