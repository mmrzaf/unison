#!/usr/bin/env python3
"""Repository-level release invariants for the Unison 1.0 release line."""
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

        versions = text("gradle/libs.versions.toml")
        require(
            re.search(r'appVersionName\s*=\s*"1\.0\.\d+"', versions) is not None,
            "Version name is not a supported 1.0 patch release",
        )
        require(
            re.search(r'appVersionCode\s*=\s*"[1-9]\d*"', versions) is not None,
            "Version code must be a positive integer",
        )
        require(re.search(r'compileSdk\s*=\s*"36"', versions) is not None, "compileSdk changed unexpectedly")
        require(re.search(r'minSdk\s*=\s*"30"', versions) is not None, "minSdk changed unexpectedly")
        require(re.search(r'targetSdk\s*=\s*"33"', versions) is not None, "targetSdk changed unexpectedly")

        protocol = text("app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt")
        protocol_json = text("app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt")
        require("const val PROTOCOL_VERSION = 1" in protocol, "Wire protocol is not 1")
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
        require("AnimatedVisibility" not in room, "Room scroll still uses layout-time visibility animation")
        require("state: MainUiState" not in room, "Room screen still depends on the entire application state")
        require("collectAsLazyPagingItems" not in room, "Room collects the library pager while the picker is closed")
        require("playbackPositionFlow" in room, "Playback position is not isolated from the room composition")
        playback_components = text("app/src/main/java/com/darius/unison/ui/room/RoomPlaybackComponents.kt")
        require("collectAsStateWithLifecycle" in playback_components, "Playback position is not collected at the seek control")
        require("animateFloatAsState" not in playback_components, "Room controls still animate normal row/control state")

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

        runtime_lines = len(text("app/src/main/java/com/darius/unison/room/RoomRuntime.kt").splitlines())
        require(runtime_lines <= 4850, f"RoomRuntime exceeded orchestration boundary: {runtime_lines}")
        require(len(text("app/src/main/java/com/darius/unison/ui/home/HomeScreen.kt").splitlines()) <= 800, "HomeScreen is oversized")
        require(len(text("app/src/main/java/com/darius/unison/ui/room/SharedRoomScreen.kt").splitlines()) <= 800, "SharedRoomScreen is oversized")

        print("SOURCE_TREE_OK")
        return 0
    except AssertionError as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
