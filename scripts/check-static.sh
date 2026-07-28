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
grep -q 'collectAsLazyPagingItems' app/src/main/java/com/darius/unison/ui/library/LibraryScreen.kt
grep -q 'const val PROTOCOL_VERSION = 2' app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt
grep -q 'MAX_QUEUE_ITEMS = 1_000' app/src/main/java/com/darius/unison/room/RoomReducer.kt
grep -q 'MAX_CONCURRENT_INCOMING = 24' app/src/main/java/com/darius/unison/network/PeerServer.kt
grep -q 'sha256Hex(target) == target.name' app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt
grep -q 'MAX_FILE_BYTES = 4 \* 1024 \* 1024' app/src/main/java/com/darius/unison/library/M3uCodec.kt
grep -q 'NetworkAddressPolicy.parseAllowedIpv4' app/src/main/java/com/darius/unison/ui/RoomSessionActions.kt
grep -q 'playerWindow(' app/src/main/java/com/darius/unison/room/PlaybackQueuePolicy.kt
grep -q 'capacity = 64' app/src/main/java/com/darius/unison/app/RoomCommandBus.kt
grep -q 'capacity = 128' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'SerializedEventLoop<RoomEvent>' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
! grep -q 'canonicalMutationMutex' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'RoomSnapshotValidator' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'EnvelopeReplayProtector' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'private val guaranteed = Channel<Envelope>' app/src/main/java/com/darius/unison/network/ControlConnection.kt
grep -q 'private val playbackReference = Channel<Envelope>' app/src/main/java/com/darius/unison/network/ControlConnection.kt

# Data, credential, and lifecycle invariants.
! sed -n '/data class RoomSnapshot(/,/^)/p' app/src/main/java/com/darius/unison/model/DomainModels.kt | grep -q 'roomPin'
grep -q 'val localRoomPin: String?' app/src/main/java/com/darius/unison/model/DomainModels.kt
grep -q 'Crypto.reconnectProof' app/src/main/java/com/darius/unison/room/ControlAdmissionController.kt
grep -q 'AdmissionGuard()' app/src/main/java/com/darius/unison/room/ControlAdmissionController.kt
grep -q 'maxTrackedNonces: Int = 1_024' app/src/main/java/com/darius/unison/room/AdmissionGuard.kt
grep -q 'maxTrackedAttempts: Int = 256' app/src/main/java/com/darius/unison/room/AdmissionGuard.kt
grep -q 'maxGlobalFailures: Int = 30' app/src/main/java/com/darius/unison/room/AdmissionGuard.kt
grep -q 'deriveControlSessionKeys' app/src/main/java/com/darius/unison/protocol/Crypto.kt
grep -q 'serverWriteKey = keys.coordinatorToClient' app/src/main/java/com/darius/unison/room/ControlAdmissionController.kt
grep -q 'sha256Hex(file) == trackId.value' app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt
grep -q 'fun acquireLease' app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt
grep -q 'store.isLeased(trackId)' app/src/main/java/com/darius/unison/storage/CacheCleanupWorker.kt
! grep -q 'UnisonDatabase.create' app/src/main/java/com/darius/unison/storage/CacheCleanupWorker.kt
grep -q 'UnisonWorkerFactory' app/src/main/java/com/darius/unison/app/AppContainer.kt
grep -q 'Configuration.Provider' app/src/main/java/com/darius/unison/app/UnisonApplication.kt
grep -q 'TransferCancellationRegistry' app/src/main/java/com/darius/unison/transfer/TransferManager.kt
grep -q 'cancellationRegistry.attachSocket' app/src/main/java/com/darius/unison/transfer/TransferManager.kt
grep -q 'setWakeMode(C.WAKE_MODE_LOCAL)' app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt
grep -q 'android.permission.POST_NOTIFICATIONS' app/src/main/AndroidManifest.xml
grep -q 'resetSynchronizationAfterDiscontinuity' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'val driftMs: Long? = null' app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt
! grep -q 'decision.rawDriftMs ?: 0L' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'suspend fun deleteAll()' app/src/main/java/com/darius/unison/storage/Database.kt
grep -q 'persistence.discardLegacySnapshots()' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
! grep -q 'persistence.save' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'MAX_TRACKED_AUTHORIZATIONS = 512' app/src/main/java/com/darius/unison/transfer/TransferManager.kt
grep -q 'authorizations.clear()' app/src/main/java/com/darius/unison/transfer/TransferManager.kt

# Library and performance invariants.
grep -q 'releasePersistableUriPermission' app/src/main/java/com/darius/unison/library/PersistedUriPermissionManager.kt
grep -q 'releaseAllUnused' app/src/main/java/com/darius/unison/app/UnisonApplication.kt
grep -q 'PlaylistPathPolicy.evaluate' app/src/main/java/com/darius/unison/library/ImportManager.kt
grep -q 'data class M3uAmbiguousEntry' app/src/main/java/com/darius/unison/library/M3uImportModels.kt
grep -q 'findReferenceCandidates' app/src/main/java/com/darius/unison/storage/Database.kt
! sed -n '/findReferenceCandidates/,/suspend fun get/p' app/src/main/java/com/darius/unison/storage/Database.kt | grep -q 'LIMIT 1'
grep -q 'currentCoroutineContext().ensureActive()' app/src/main/java/com/darius/unison/library/ImportManager.kt
grep -q 'LibraryImportProgress' app/src/main/java/com/darius/unison/ui/LibraryImportCoordinator.kt
grep -q 'withContext(Dispatchers.Default)' app/src/main/java/com/darius/unison/ui/QrCode.kt
grep -q 'CACHE_KIB = 2 \* 1024' app/src/main/java/com/darius/unison/ui/QrCode.kt
grep -q 'DISK_CACHE_BYTES = 64L \* 1024L \* 1024L' app/src/main/java/com/darius/unison/storage/ArtworkStore.kt
grep -q 'ArtworkRetryPolicy.delayMs' app/src/main/java/com/darius/unison/storage/ArtworkStore.kt
[[ -x scripts/benchmark-library-search.py ]]
grep -q 'complete IPv6 endpoint exchange is not' docs/ARCHITECTURE.md
grep -q 'advisory host-side library-search benchmark' docs/VALIDATION.md

# Architecture boundaries.
grep -q 'val structure: StateFlow<RoomStructureState>' app/src/main/java/com/darius/unison/app/RoomStore.kt
grep -q 'val playback: StateFlow<RoomPlaybackTelemetry>' app/src/main/java/com/darius/unison/app/RoomStore.kt
grep -q 'val transfers: StateFlow<RoomTransferTelemetry>' app/src/main/java/com/darius/unison/app/RoomStore.kt
grep -q 'val localPositionMs: Long?' app/src/main/java/com/darius/unison/model/RoomStateModels.kt
grep -q 'PeerRegistry<ControlConnection>' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'RoomMessageRouter(' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'ControlAdmissionController(' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'RoomPersistenceManager(' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
grep -q 'CoordinatorEngine' app/src/main/java/com/darius/unison/room/RoomRoleEngines.kt
grep -q 'ParticipantEngine' app/src/main/java/com/darius/unison/room/RoomRoleEngines.kt
! grep -R -q 'everyoneCanAdd\|everyoneCanControl' app/src/main/java app/src/test
[[ -f app/src/main/java/com/darius/unison/ui/room/RoomScreens.kt ]]
[[ -f app/src/main/java/com/darius/unison/ui/library/LibraryScreen.kt ]]
[[ -f app/src/main/java/com/darius/unison/ui/playlists/PlaylistDetailScreen.kt ]]
[[ -f app/src/main/java/com/darius/unison/ui/components/CommonComponents.kt ]]
grep -q 'class RoomSessionActions' app/src/main/java/com/darius/unison/ui/RoomSessionActions.kt
grep -q 'class LibraryImportCoordinator' app/src/main/java/com/darius/unison/ui/LibraryImportCoordinator.kt
grep -q 'class PlaylistActions' app/src/main/java/com/darius/unison/ui/PlaylistActions.kt
(( $(wc -l < app/src/main/java/com/darius/unison/ui/UnisonApp.kt) < 700 ))
(( $(wc -l < app/src/main/java/com/darius/unison/ui/MainViewModel.kt) < 450 ))

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

./scripts/check-kotlin-source.py

echo 'Static repository checks passed.'
