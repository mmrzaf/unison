#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

test -f app/build.gradle.kts
test -f app/src/main/AndroidManifest.xml
test -x gradlew

grep -q 'applicationId = "com.darius.unison"' app/build.gradle.kts
grep -q 'minSdk = 30' app/build.gradle.kts
grep -q 'targetSdk = 36' app/build.gradle.kts
grep -q 'compileSdk = 36' app/build.gradle.kts
grep -q 'versionCode = 10000' app/build.gradle.kts
grep -q 'versionName = "0.1.0"' app/build.gradle.kts
grep -q 'JavaVersion.VERSION_17' app/build.gradle.kts
grep -q 'JvmTarget.JVM_17' app/build.gradle.kts
grep -q 'agp = "8.13.2"' gradle/libs.versions.toml
! grep -q 'android.permission.POST_NOTIFICATIONS' app/src/main/AndroidManifest.xml
! grep -R -n --exclude-dir=.git --exclude-dir=build --exclude='check-static.sh' 'plaincast' .

# The installed app is local-only. Reject hosted Google SDKs and hard-coded web endpoints.
if grep -R -n -E 'com\.google\.android\.gms|com\.google\.firebase|play-services|firebase-' \
    app/build.gradle.kts gradle/libs.versions.toml app/src/main/java; then
  echo 'Hosted Google service dependency found.' >&2
  exit 1
fi
if grep -R -n -E 'https?://' app/src/main/java; then
  echo 'Hard-coded global network endpoint found.' >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

for path in Path('app/src/main/res').rglob('*.xml'):
    ET.parse(path)
manifest_path = Path('app/src/main/AndroidManifest.xml')
tree = ET.parse(manifest_path)
root = tree.getroot()
android = '{http://schemas.android.com/apk/res/android}'
application = root.find('application')
assert application is not None
services = {
    node.get(android + 'name'): node
    for node in application.findall('service')
}
service = services.get('.playback.UnisonRoomService')
assert service is not None, 'UnisonRoomService is missing'
assert service.get(android + 'exported') == 'true', 'MediaSessionService must be exported for system media controllers'
types = set((service.get(android + 'foregroundServiceType') or '').split('|'))
assert {'mediaPlayback', 'connectedDevice'} <= types
assert any(
    action.get(android + 'name') == 'androidx.media3.session.MediaSessionService'
    for intent_filter in service.findall('intent-filter')
    for action in intent_filter.findall('action')
), 'MediaSessionService action is missing'

source = Path('app/src/main/java/com/darius/unison/playback/UnisonRoomService.kt').read_text()
assert '.setCallback(mediaSessionCallback)' in source
assert 'controller.isTrusted' in source
assert 'addAllReadOnlyCommands()' in source
for forbidden in (
    'COMMAND_CHANGE_MEDIA_ITEMS',
    'COMMAND_SET_REPEAT_MODE',
    'COMMAND_SET_SHUFFLE_MODE',
    'COMMAND_SET_SPEED_AND_PITCH',
):
    assert forbidden not in source, f'Unsafe external media command exposed: {forbidden}'

wrapper = Path('app/src/main/java/com/darius/unison/playback/RoomMediaSessionPlayer.kt').read_text()
for command in ('AppCommand.Play', 'AppCommand.Pause', 'AppCommand.SkipNext', 'AppCommand.SkipPrevious'):
    assert command in wrapper, f'System media wrapper is missing {command}'
assert 'SystemMediaCommandPolicy.seek' in wrapper, 'System media seek is not routed through room policy'

print('XML, manifest, and media-session policy checks passed.')
PY

# Reject obvious publication hazards and accidentally committed secrets.
if grep -R -n -E 'TODO([: (]|$)|FIXME([: (]|$)|HACK([: (]|$)|XXX([: (]|$)|NotImplementedError' \
    app/src/main app/src/test scripts docs README.md \
    --exclude='check-static.sh' >/tmp/unison-static-markers.txt; then
  cat /tmp/unison-static-markers.txt >&2
  echo 'Unresolved development marker found.' >&2
  exit 1
fi
rm -f /tmp/unison-static-markers.txt

echo 'Static repository checks passed.'
