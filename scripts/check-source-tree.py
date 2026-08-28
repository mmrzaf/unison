#!/usr/bin/env python3
"""Repository-level release invariants for the Unison 1.1 stability release."""
from __future__ import annotations

from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise AssertionError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def text(path: str) -> str:
    value = ROOT / path
    require(value.is_file(), f"Missing required file: {path}")
    return value.read_text(errors="ignore")


def main() -> int:
    try:
        for path in (
            ".github/workflows/android.yml",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt",
            "app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt",
            "app/src/main/java/com/darius/unison/storage/Database.kt",
            "app/src/main/java/com/darius/unison/ui/UnisonApp.kt",
            "app/src/main/java/com/darius/unison/ui/AboutUnisonDialog.kt",
            "app/src/test/java/com/darius/unison/ui/PermissionControllerTest.kt",
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.jar",
            "scripts/build-debug.sh",
            "scripts/build-release.sh",
            "scripts/check-release-quality.sh",
            "scripts/archive.sh",
        ):
            require((ROOT / path).exists(), f"Missing required file: {path}")

        for path in (
            "docs/PHASE2_TWO_SURFACE_UI.md",
            "docs/PHASE3_ARCHITECTURE_AND_QUALIFICATION.md",
            "scripts/check-phase3.sh",
            "app/src/main/java/com/darius/unison/ui/room/RoomScreens.kt",
            "app/src/main/java/com/darius/unison/ui/library/LibraryScreen.kt",
            "app/src/main/java/com/darius/unison/room/RoomPersistenceManager.kt",
            "app/src/main/java/com/darius/unison/ui/room/RoomCodeComponents.kt",
            ".github/lint-ci.xml",
            "app/lint.xml",
            "docs/IMPLEMENTATION_STATUS.md",
            "docs/VALIDATION.md",
            "docs/RELEASE_READINESS_1.0.0.md",
            "docs/unison-technical-specification.md",
        ):
            require(not (ROOT / path).exists(), f"Temporary or obsolete path remains: {path}")

        gitignore = text(".gitignore")
        for generated_path in (".gradle/", ".kotlin/", "build/", "local.properties"):
            require(generated_path in gitignore, f"Generated path is not ignored: {generated_path}")
        for secret_path in ("keystore/", "keystore.properties"):
            require(secret_path in gitignore, f"Signing material is not ignored: {secret_path}")
        archive = text("scripts/archive.sh")
        for excluded in ("./local.properties", "./keystore.properties", "./keystore"):
            require(excluded in archive, f"Source archive does not exclude sensitive path: {excluded}")
        require("Refusing to create archive" in archive, "Source archive lacks fail-closed secret scan")
        for path in (
            "docs/000-index.md",
            "docs/00-governance",
            "docs/10-product",
            "docs/20-engineering",
            "docs/30-experience",
            "docs/70-reference",
            "docs/90-generated",
            "docs/pdf",
            "scripts/dev.sh",
            "scripts/docs.py",
            "scripts/images.sh",
            "scripts/generate-api.sh",
            "scripts/generate-action-contracts.py",
            "scripts/generate-permission-contract.py",
            "scripts/generate-typescript-api.mjs",
            "scripts/generate_typescript_api.py",
            "scripts/typescript-api-http-client.ts",
            "scripts/verify-generated-contracts.sh",
        ):
            require(not (ROOT / path).exists(), f"Unrelated legacy project path remains: {path}")

        versions = text("gradle/libs.versions.toml")
        require(
            re.search(r'appVersionName\s*=\s*"1\.1\.0"', versions) is not None,
            "Version name is not the 1.1.0 stability release",
        )
        require(
            re.search(r'appVersionCode\s*=\s*"[1-9]\d*"', versions) is not None,
            "Version code must be a positive integer",
        )
        readme = text("README.md")
        changelog = text("CHANGELOG.md")
        local_release = text("docs/LOCAL_RELEASE.md")
        physical_qualification = text("docs/PHYSICAL_DEVICE_QUALIFICATION.md")
        require("# Unison 1.1.0" in readme, "README version is not 1.1.0")
        require("versionCode` 3" in readme and "Wire protocol: 2 only" in readme, "README release facts drifted")
        require("## 1.1.0\n" in changelog and "1.1.0 (in development)" not in changelog, "Changelog is not final")
        require("v1.1.0" in local_release, "Local release guide is stale")
        require("bounded reconnection to that coordinator" in physical_qualification, "Coordinator-loss qualification is stale")
        require("Room actions → Room logs" not in physical_qualification, "Diagnostics naming is stale")
        room_code_test = text("app/src/androidTest/java/com/darius/unison/ui/RoomCodeComposeTest.kt")
        require("RoomScreen(" not in room_code_test, "Android room-code test uses removed RoomScreen")
        require(re.search(r'compileSdk\s*=\s*"36"', versions) is not None, "compileSdk changed unexpectedly")
        require(re.search(r'minSdk\s*=\s*"30"', versions) is not None, "minSdk changed unexpectedly")
        require(re.search(r'targetSdk\s*=\s*"33"', versions) is not None, "targetSdk changed unexpectedly")

        protocol = text("app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt")
        protocol_json = text("app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt")
        require("const val PROTOCOL_VERSION = 2" in protocol, "Wire protocol is not 2")
        for marker in (
            "PinClientHello",
            "ReconnectClientHello",
            "FileClientHello",
            "data class FileRequest",
            "data class FileResponseHeader",
        ):
            require(marker in protocol, f"Explicit protocol message missing: {marker}")
        for marker in ("protocolVersions", "acceptedVersion", "reconnectRequested", "fileRequest"):
            require(marker not in protocol, f"Compatibility handshake field remains: {marker}")
        require("ignoreUnknownKeys = false" in protocol_json, "Unknown protocol keys are accepted")
        require("explicitNulls = true" in protocol_json, "Protocol nullability is not explicit")
        require("QueuePreparedSetChanged" not in protocol, "Canonical prepared-set mutation returned")
        require("PlaybackReadinessChanged" in protocol, "Runtime readiness projection is missing")

        domain = text("app/src/main/java/com/darius/unison/model/DomainModels.kt")
        member_snapshot = domain.split("data class MemberSnapshot(", 1)[1].split(")", 1)[0]
        room_snapshot = domain.split("data class RoomSnapshot(", 1)[1].split(") {", 1)[0]
        for transient_field in ("endpoint", "connected", "currentTrackState"):
            require(transient_field not in member_snapshot, f"Transient member field returned to canonical state: {transient_field}")
        require("preparedQueueItemIds" not in room_snapshot, "Playback readiness returned to canonical RoomSnapshot")

        database = text("app/src/main/java/com/darius/unison/storage/Database.kt")
        require("version = 1" in database, "Database is not schema 1")
        require('"unison-1.db"' in database, "Fresh schema does not use its own database file")
        require("room_snapshots" not in database, "Legacy room snapshot table remains")
        require("RoomSnapshotEntity" not in database, "Legacy room snapshot entity remains")
        production = "\n".join(
            path.read_text(errors="ignore")
            for path in (ROOT / "app/src/main/java").rglob("*.kt")
        )
        require(re.search(r"Migration\s*\(", production) is None, "Database migration remains in fresh schema")
        require("addMigrations" not in production, "Database migration registration remains")
        require("fallbackToDestructiveMigration" not in production, "Destructive migration compatibility remains")

        schema_dir = ROOT / "app/schemas/com.darius.unison.storage.UnisonDatabase"
        schemas = sorted(schema_dir.glob("*.json"))
        require([path.name for path in schemas] == ["1.json"], "Exactly schema 1 must be checked in")
        schema = json.loads(schemas[0].read_text())["database"]
        require(schema["version"] == 1, "Exported schema version is not 1")
        tables = {entity["tableName"] for entity in schema["entities"]}
        require(tables == {"tracks", "track_sources", "playlists", "playlist_entries"}, f"Unexpected schema tables: {sorted(tables)}")

        app = text("app/src/main/java/com/darius/unison/ui/UnisonApp.kt")
        require("HomeScreen(" in app and "SharedRoomScreen(" in app, "Two-surface application entry is missing")
        require("NavigationBar" not in app, "Destination tabs were reintroduced")
        home = text("app/src/main/java/com/darius/unison/ui/home/HomeScreen.kt")
        room = text("app/src/main/java/com/darius/unison/ui/room/SharedRoomScreen.kt")
        picker = text("app/src/main/java/com/darius/unison/ui/room/RoomAddMusicSheet.kt")
        require("All Music" in home, "Built-in All Music collection is missing")
        require("TrackRow(" not in home, "Home must remain playlist-only")
        require('key = "room-code"' not in room, "Room code was made permanently visible")
        require("TransportStatusLine(" not in room, "Transient transport text was reintroduced below the player")
        require("RoomQueueToolbar(" in room, "Compact queue toolbar is missing")
        require("stableTracks" in picker, "Add Music picker does not preserve a stable rendered generation")
        require("QueueMusicPickerSection.PLAYLISTS" in picker, "Add Music picker does not expose playlists first")
        require("QueuePlaylistOption.AllMusic" in picker, "All Music is missing from Add to queue")
        require("onSelectAllTracks" in picker and "Select all" in picker, "Add Music picker lacks bulk song selection")
        require("selectedPlaylistIds" in picker and "onAddSelection" in picker, "Add Music picker lacks combined playlist selection")
        require("optimisticAction" in room, "Playback controls lack immediate local feedback")
        ui_policy = text("app/src/main/java/com/darius/unison/ui/RoomPlaybackUiPolicy.kt")
        require("canNavigate = hasCurrentItem" in ui_policy, "Pending navigation blocks reversible Next/Previous controls")
        require("canSelectItem = true" in ui_policy, "Pending navigation blocks queue target replacement")
        require("optimisticAction != null" not in room, "Optimistic UI feedback is still used as a transport lock")
        require("PlaybackTransitionStatus" in room, "Playback-critical preparation state is not surfaced at the player")
        require("connectedListeners = snapshot.members.size" in room, "Room listener count is derived from local socket topology instead of canonical membership")
        require('key = "participants"' not in room and "ParticipantStatus(" not in room, "Healthy participant status row returned to the primary room surface")
        require("backgroundTransfers" not in room and "TransferStatusCard(" not in room, "Background transfer machinery returned to the normal room surface")
        require("RoomListenersSheet(" in room, "Listener details are not presented as a contextual sheet")
        require("AnimatedVisibility" not in room, "Room scroll still uses layout-time visibility animation")
        require("state: MainUiState" not in room, "Room screen still depends on the entire application state")
        require("collectAsLazyPagingItems" not in room, "Room collects the library pager while the picker is closed")
        require("playbackPositionFlow" in room, "Playback position is not isolated from the room composition")
        room_components = text("app/src/main/java/com/darius/unison/ui/room/SharedRoomComponents.kt")
        room_dialogs = text("app/src/main/java/com/darius/unison/ui/room/SharedRoomDialogs.kt")
        require('"Queue",' in room, "Room queue lost its explicit music-first section title")
        require('Text("Clear queue", color = MaterialTheme.colorScheme.error)' in room_components, "Destructive queue clearing is not isolated in queue overflow")
        require("ModalBottomSheet(" in room_dialogs and "RoomListenersSheet" in room_dialogs, "Listeners reverted to a blocking dialog")
        playback_components = text("app/src/main/java/com/darius/unison/ui/room/RoomPlaybackComponents.kt")
        require("collectAsStateWithLifecycle" in playback_components, "Playback position is not collected at the seek control")
        require("animateFloatAsState" not in playback_components, "Room controls still animate normal row/control state")

        room_service = text("app/src/main/java/com/darius/unison/playback/UnisonRoomService.kt")
        require("TRANSPORT_COMMAND_WORKERS" not in room_service, "Concurrent transport worker pool was reintroduced")
        require(room_service.count("transportFlow.collect") == 1, "Transport must have exactly one service ingress collector")

        player_executor = text("app/src/main/java/com/darius/unison/playback/PlayerExecutor.kt")
        media3_adapter = text("app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt")
        room_runtime = text("app/src/main/java/com/darius/unison/room/RoomRuntime.kt")
        room_event = text("app/src/main/java/com/darius/unison/room/RoomEvent.kt")
        require("mutationMutex" in player_executor, "PlayerExecutor lost its single Media3 mutation boundary")
        require("setPauseAtEndOfMediaItems(true)" in media3_adapter, "Media3 may auto-run across canonical item boundaries")
        for obsolete in (
            "PlayerMutationCoordinator",
            "ScheduledPlaybackController",
            "PlayerTransitionCircuitBreaker",
            "PlayerItemTransitionPolicy",
        ):
            require(obsolete not in production, f"Obsolete playback authority remains: {obsolete}")
        require("PlaybackSyncTick" not in room_event, "Local playback synchronization was routed back through the room actor")
        require('"scheduler_delay"' not in room_runtime, "Actor scheduling delay is still treated as a playback discontinuity")
        require("NaturalAdvance" not in production and "NaturalRepeat" not in production, "Media3 automatic transitions still author canonical progression")
        require("beginCoordinatorRecovery" not in room_runtime and "ELECTION_DELAY_MS" not in room_runtime, "Automatic coordinator election returned")
        require("reconcileTransportFromCanonical" not in room_runtime, "Transport watchdog became a second playback repair authority")
        require("preparedQueueItemIds = preparedQueueItemIds" in room_runtime, "Runtime readiness is not passed into playback policy")
        transfer_scheduler = text("app/src/main/java/com/darius/unison/room/TransferDemandScheduler.kt")
        transfer_manager = text("app/src/main/java/com/darius/unison/transfer/TransferManager.kt")
        peer_health = text("app/src/main/java/com/darius/unison/room/PeerPlaybackHealthRegistry.kt")
        require("TransferPriority" in protocol and "neededByCoordinatorNs" in protocol, "Transfer demand lost playback priority/deadline")
        for field in ("TransferFailureStage", "TransferFailureCode", "TransferFailureBlame", "retryable"):
            require(field in protocol, f"Typed transfer failure field is missing: {field}")
        require("PENDING_TRACK_PREPARATION_TIMEOUT" not in room_runtime, "Arbitrary playback preparation timeout returned")
        require("preemptionCandidate" in transfer_scheduler, "Playback-critical transfer preemption is missing")
        require("chooseSource" in transfer_scheduler and "sourceActiveUploads" in transfer_scheduler, "Transfer source scoring is missing")
        require("CATCHING_UP" in peer_health and "contentReady" in peer_health, "Content-aware playback admission is missing")
        require("isClockReady" in peer_health and "wasClockReady" in room_runtime, "Clock acquisition is conflated with playback admission")
        require(
            "preparedQueueItemIds = preparedQueueItemIds" in room_runtime,
            "Coordinator reducer no longer receives runtime playback readiness",
        )
        require(
            "is ProtocolBody.QueueItemPreparationRequested -> applyCanonicalEnvelope" not in room_runtime,
            "Ephemeral preparation request returned to canonical history",
        )
        require(
            "retentionRefreshJob" not in room_runtime and "TEMPORARY_RETENTION_REFRESH_INTERVAL_MS" not in room_runtime,
            "Periodic temporary-track database churn returned",
        )
        require(
            "previousQueueRevision == null || previousQueueRevision != snapshot.queueRevision" in room_runtime,
            "Room queue leases are recomputed for non-queue mutations",
        )
        require("FileInputStream(file)" in transfer_manager and ".channel.position(request.offset)" in transfer_manager, "Resume upload does not seek directly to its offset")
        require("skipFully" not in transfer_manager, "Linear resume skipping returned")

        manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
        manifest = ET.parse(manifest_path).getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        application = manifest.find("application")
        require(application is not None, "Application manifest node is missing")
        require(application.get(android + "allowBackup") == "false", "Android backup must remain disabled")
        require(application.get(android + "usesCleartextTraffic") == "false", "Cleartext traffic must remain disabled")

        permissions = {node.get(android + "name"): node for node in manifest.findall("uses-permission")}
        for permission in (
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "android.permission.NEARBY_WIFI_DEVICES",
        ):
            require(permission in permissions, f"Required Android permission missing: {permission}")
        require(
            permissions["android.permission.NEARBY_WIFI_DEVICES"].get(android + "usesPermissionFlags") == "neverForLocation",
            "Nearby Wi-Fi permission must remain neverForLocation",
        )
        for permission in (
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ):
            require(
                permissions.get(permission) is not None
                and permissions[permission].get(android + "maxSdkVersion") == "32",
                f"Legacy hotspot location permission must stop at API 32: {permission}",
            )
        require(
            "android.permission.ACCESS_LOCAL_NETWORK" not in permissions,
            "Future local-network permission must not be added while targetSdk remains 33",
        )
        service = application.find("service[@android:name='.playback.UnisonRoomService']", {"android": "http://schemas.android.com/apk/res/android"})
        require(service is not None, "UnisonRoomService manifest declaration is missing")
        service_types = set((service.get(android + "foregroundServiceType") or "").split("|"))
        require(
            {"mediaPlayback", "connectedDevice"}.issubset(service_types),
            "Room service must declare mediaPlayback and connectedDevice foreground-service types",
        )

        permission_controller = text("app/src/main/java/com/darius/unison/ui/PermissionController.kt")
        require("localNetworkPermissions" in permission_controller, "Local network permission gate is missing")
        require("Manifest.permission.NEARBY_WIFI_DEVICES" in permission_controller, "Nearby Wi-Fi runtime permission is not gated")
        music_models = text("app/src/main/java/com/darius/unison/ui/MainUiModels.kt")
        import_coordinator = text("app/src/main/java/com/darius/unison/ui/LibraryImportCoordinator.kt")
        music_picker_sheets = text("app/src/main/java/com/darius/unison/ui/library/MusicSelectionSheets.kt")
        playlist_detail = text("app/src/main/java/com/darius/unison/ui/playlists/PlaylistDetailScreen.kt")
        require("ShareDestination" not in production, "Legacy Room/Library/Both import destination returned")
        require("MusicDestination" in music_models and "MusicDestinationSheet" in music_picker_sheets, "Unified music destination flow is missing")
        require("newPlaylistName" in import_coordinator and "New playlist" in music_picker_sheets, "Inline playlist creation is missing from music placement")
        require("TrackPickerSheet" in playlist_detail and "PlaylistPickerSheet" in playlist_detail, "Playlist curation returned to one-off dialogs")
        require("detectDragGesturesAfterLongPress" in playlist_detail and 'Text("Edit order")' in playlist_detail, "Playlist drag reordering is missing")
        all_music = text("app/src/main/java/com/darius/unison/ui/home/AllMusicSheet.kt")
        require("internal fun AllMusicScreen(" in all_music, "All Music is not a persistent navigation surface")
        require("ModalBottomSheet(" not in all_music, "All Music reverted to a modal sheet")
        require("onOpenAllMusic" in home and "allMusicOpen" not in home, "Home still owns All Music modal navigation state")
        require("AllMusicScreen(" in app and "allMusicOpen" in app, "Top-level All Music navigation is missing")
        require("ModalBottomSheet(" not in app, "Persistent playlist/library navigation reverted to an app-level modal")
        require("ScreenTopBar(" in app and "onBack = ::closePlaylistScreen" in app, "Playlist screen is missing real back navigation")
        require("BackHandler(enabled = selectingPlaylist || reordering)" in playlist_detail, "Playlist edit modes do not consume Back before navigation")
        require("roomActive" not in playlist_detail and "onAddToRoom" not in playlist_detail, "Persistent playlist browsing is still coupled to room queue actions")
        visual_components = text("app/src/main/java/com/darius/unison/ui/components/VisualComponents.kt")
        require("internal fun ScreenTopBar(" in visual_components, "Shared persistent-screen top bar styling is missing")
        require("internal fun UnisonSearchField(" in visual_components, "Shared search treatment is missing")
        require("ScreenTopBar(title = \"Unison\")" in home, "Home reverted to a card-style app bar")
        require("UnisonSearchField(" in all_music, "All Music is not using the shared compact search surface")
        require("headlineMedium" in room and '"NOW PLAYING"' in room, "Room player lost the music-first visual hierarchy")
        require("secondaryContainer.copy(alpha = 0.42f)" in playback_components, "Current queue item lacks quiet visual emphasis")
        require("UnisonSearchField(" in room_components, "Room queue search diverged from the shared search treatment")

        about = text("app/src/main/java/com/darius/unison/ui/AboutUnisonDialog.kt")
        require("BuildConfig.VERSION_NAME" in about, "About surface does not expose the app version")
        require("PROTOCOL_VERSION" in about, "About surface does not expose the protocol version")
        strings = text("app/src/main/res/values/strings.xml")
        require("https://github.com/mmrzaf/unison" in strings, "Source repository link is missing")
        qualification = text("docs/PHYSICAL_DEVICE_QUALIFICATION.md")
        for api in ("API 30", "API 33", "API 36"):
            require(api in qualification, f"Physical qualification matrix is missing {api}")

        all_text = "\n".join(
            path.read_text(errors="ignore")
            for path in ROOT.rglob("*")
            if path.is_file()
            and path.resolve() != Path(__file__).resolve()
            and not {".git", ".idea", "build", ".gradle", ".kotlin"}.intersection(path.parts)
            and path.suffix.lower() in {".kt", ".kts", ".md", ".toml", ".xml", ".sh", ".py", ".yml", ".yaml", ".properties"}
        )
        require(re.search(r"\bProtocol 5\b|wire protocol 5|protocol 5", all_text, re.IGNORECASE) is None, "Obsolete protocol documentation remains")
        require(re.search(r"\bPhase [123]\b|PHASE[123]|phase[123]", all_text) is None, "Phase-specific release scaffolding remains")
        require(re.search(r"\bTODO\b|\bFIXME\b|\bHACK\b", production) is None, "Production TODO/FIXME/HACK remains")
        require(re.search(r"https?://", production) is None, "Hard-coded remote endpoint found")
        require(re.search(r"firebase|play-services|billingclient", text("app/build.gradle.kts") + versions, re.IGNORECASE) is None, "Hosted/store runtime dependency found")

        require(len(text("app/src/main/java/com/darius/unison/ui/home/HomeScreen.kt").splitlines()) <= 800, "HomeScreen is oversized")
        require(len(text("app/src/main/java/com/darius/unison/ui/room/SharedRoomScreen.kt").splitlines()) <= 800, "SharedRoomScreen is oversized")

        print("SOURCE_TREE_OK")
        return 0
    except AssertionError as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
