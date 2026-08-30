#!/usr/bin/env python3
"""Repository-level release invariants for the current Unison source tree/package."""
from __future__ import annotations

from pathlib import Path
import json
import re
import sys
import subprocess
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


def repository_files() -> list[str]:
    """Return source inventory in either a Git checkout or an exported source package."""
    try:
        inside = subprocess.check_output(
            ["git", "rev-parse", "--is-inside-work-tree"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        if inside == "true":
            return subprocess.check_output(
                ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
                cwd=ROOT,
                text=True,
            ).splitlines()
    except (FileNotFoundError, subprocess.CalledProcessError):
        pass

    excluded_parts = {
        ".git", ".gradle", ".kotlin", ".idea", ".vscode", "build", "dist",
        "captures", "keystore", "signing", "__pycache__",
    }
    files: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if excluded_parts.intersection(relative.parts):
            continue
        files.append(relative.as_posix())
    return sorted(files)


def version_value(versions: str, key: str) -> str:
    match = re.search(rf'^{re.escape(key)}\s*=\s*"([^"]+)"\s*$', versions, re.MULTILINE)
    require(match is not None, f"Missing {key} in version catalog")
    return match.group(1)


def main() -> int:
    try:
        for path in (
            ".github/workflows/android.yml",
            ".github/workflows/verify.yml",
            ".github/workflows/release.yml",
            ".github/workflows/codeql.yml",
            ".github/dependabot.yml",
            ".github/SECURITY.md",
            ".github/ISSUE_TEMPLATE/bug.yml",
            ".github/ISSUE_TEMPLATE/feature.yml",
            ".github/ISSUE_TEMPLATE/config.yml",
            ".github/PULL_REQUEST_TEMPLATE.md",
            "LICENSE",
            "CODE_OF_CONDUCT.md",
            "SUPPORT.md",
            "THIRD_PARTY_NOTICES.md",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt",
            "app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt",
            "app/src/main/java/com/darius/unison/protocol/Srp6aCore.kt",
            "app/src/test/java/com/darius/unison/protocol/Srp6aCoreRfc5054Test.kt",
            "app/src/test/java/com/darius/unison/network/ControlConnectionPriorityTest.kt",
            "app/src/test/java/com/darius/unison/room/RoomLifecycleSeamRegressionTest.kt",
            "app/src/main/java/com/darius/unison/storage/Database.kt",
            "app/src/main/java/com/darius/unison/ui/UnisonApp.kt",
            "app/src/main/java/com/darius/unison/ui/AboutUnisonDialog.kt",
            "app/src/test/java/com/darius/unison/ui/PermissionControllerTest.kt",
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.jar",
            "scripts/build-debug.sh",
            "scripts/build-release.sh",
            "scripts/check-release-quality.sh",
            "scripts/check-hardening-kotlin.sh",
            "docs/SRP_REVIEW_1.2.md",
            "scripts/archive.sh",
            "scripts/package-source.sh",
            "scripts/check-source-package.py",
            "scripts/extract-release-notes.py",
            "scripts/bootstrap-dev.sh",
            "scripts/refresh-dependency-verification.sh",
            "scripts/check-dependency-verification.py",
            "scripts/check-tooling.sh",
            "CONTRIBUTING.md",
            "docs/DEVELOPMENT.md",
            "docs/GITHUB_SETUP.md",
            "docs/ROADMAP.md",
            "docs/release-evidence/README.md",
            "docs/release-evidence/TEMPLATE.md",
            "docs/decisions/README.md",
            "docs/INVARIANTS.md",
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
        for generated_path in (".gradle/", ".kotlin/", "build/", "local.properties", ".unison-overlay/"):
            require(generated_path in gitignore, f"Generated path is not ignored: {generated_path}")
        for secret_path in ("keystore/", "keystore.properties"):
            require(secret_path in gitignore, f"Signing material is not ignored: {secret_path}")
        archive = text("scripts/archive.sh")
        for excluded in ("./local.properties", "./keystore.properties", "./keystore"):
            require(excluded in archive, f"Source archive does not exclude sensitive path: {excluded}")
        require("Refusing to create archive" in archive, "Source archive lacks fail-closed secret scan")

        tracked_files = repository_files()
        sensitive_suffixes = (".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".base64")
        for tracked in tracked_files:
            lower = tracked.lower()
            require(tracked not in {"keystore.properties", "local.properties"}, f"Sensitive/local file is tracked: {tracked}")
            require(not lower.startswith(("keystore/", "signing/")), f"Signing directory is tracked: {tracked}")
            require(not lower.endswith(sensitive_suffixes), f"Sensitive key/archive is tracked: {tracked}")
            if tracked != "keystore.properties.example":
                value = (ROOT / tracked)
                if value.is_file() and value.stat().st_size <= 2_000_000:
                    content = value.read_text(errors="ignore")
                    private_key_marker = "-----BEGIN " + "PRIVATE KEY-----"
                    encrypted_private_key_marker = "-----BEGIN " + "ENCRYPTED PRIVATE KEY-----"
                    require(private_key_marker not in content and encrypted_private_key_marker not in content, f"Private key material is tracked: {tracked}")
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
        version_name = version_value(versions, "appVersionName")
        version_code = version_value(versions, "appVersionCode")
        require(re.fullmatch(r"1\.2\.0(?:-(?:alpha|beta|rc)\.[1-9][0-9]*)?", version_name) is not None,
                f"Unexpected 1.2 release-line version: {version_name}")
        require(version_code.isdigit() and int(version_code) >= 4, "1.2 alpha/beta/stable versionCode must be >= 4")

        readme = text("README.md")
        changelog = text("CHANGELOG.md")
        local_release = text("docs/LOCAL_RELEASE.md")
        physical_qualification = text("docs/PHYSICAL_DEVICE_QUALIFICATION.md")
        release_quality = text("scripts/check-release-quality.sh")
        security_doc = text("docs/SECURITY.md")
        srp_review = text("docs/SRP_REVIEW_1.2.md")
        require(f"Version: `{version_name}` (`versionCode` {version_code})" in readme, "README release facts drifted")
        require("Wire protocol: **2 only**" in readme, "README wire protocol fact drifted")
        require(f"## {version_name}\n" in changelog, f"Changelog has no section for {version_name}")
        require("Publication is tag-triggered only" in local_release, "Local release guide is stale")
        require("bounded recovery" in physical_qualification and "zombie room UI" in physical_qualification, "Coordinator-loss qualification is stale")
        require("Room actions → Room logs" not in physical_qualification, "Diagnostics naming is stale")
        require("./scripts/check-hardening-kotlin.sh" in release_quality, "Release quality gate omits Milestone-5 hardening tests")
        require("Srp6aCoreRfc5054Test" in srp_review and "BigInteger.modPow" in srp_review, "SRP 1.2 review is incomplete")
        require("SRP_REVIEW_1.2.md" in security_doc, "Security model does not link the SRP 1.2 review")
        require("room A" in physical_qualification, "Cross-room admission qualification is missing")

        app_build = text("app/build.gradle.kts")
        require('testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"' in app_build,
                "AndroidJUnitRunner is not configured explicitly")
        require("androidx.test.runner" in app_build and "androidx.test.ext.junit" in app_build,
                "Explicit Android instrumentation dependencies are missing")

        android_ci = text(".github/workflows/android.yml")
        release_ci = text(".github/workflows/release.yml")
        verify_ci = text(".github/workflows/verify.yml")
        require("connectedDebugAndroidTest" in android_ci and "api-level: 33" in android_ci,
                "Normal CI does not execute Android instrumentation on API 33")
        require("api: [30, 33, 36]" in release_ci and "connectedDebugAndroidTest" in release_ci,
                "Release CI does not execute the required API 30/33/36 instrumentation matrix")
        require("workflow_dispatch" not in release_ci, "Release publication must be tag-triggered only")
        require("--clobber" not in release_ci, "Release assets must not be replaceable with --clobber")
        require("--prerelease" in release_ci and "--latest" in release_ci,
                "Release workflow does not distinguish prerelease and stable versions")
        require("app-debug.apk" not in release_ci and "Unison-debug" not in release_ci,
                "Debug APK must not be a public release asset")
        require("persist-credentials: false" in verify_ci and "actions/checkout@" in verify_ci,
                "Reusable verification workflow lacks hardened checkout configuration")
        require("spotlessCheck" in verify_ci, "CI does not enforce the configured formatting baseline")
        action_ref = re.compile(r"uses:\s+[^\s@]+@([0-9a-f]{40})(?:\s|$)")
        for workflow_path in (".github/workflows/verify.yml", ".github/workflows/android.yml",
                              ".github/workflows/release.yml", ".github/workflows/codeql.yml"):
            workflow_text = text(workflow_path)
            for line in workflow_text.splitlines():
                if "uses:" in line and not "uses: ./" in line:
                    require(action_ref.search(line) is not None, f"Unpinned GitHub Action in {workflow_path}: {line.strip()}")

        gradle_properties = text("gradle.properties")
        require("useIranMirrors=true" not in gradle_properties, "Regional Maven mirrors must be opt-in, not public default")
        require("Apache License" in text("LICENSE"), "Project license is not Apache-2.0 text")
        evidence_path = f"docs/release-evidence/{version_name}.md"
        require((ROOT / evidence_path).is_file(), f"Missing release evidence record for current version: {evidence_path}")

        room_code_test = text("app/src/androidTest/java/com/darius/unison/ui/RoomCodeComposeTest.kt")
        require("RoomScreen(" not in room_code_test, "Android room-code test uses removed RoomScreen")
        require(re.search(r'compileSdk\s*=\s*"36"', versions) is not None, "compileSdk changed unexpectedly")
        require(re.search(r'minSdk\s*=\s*"30"', versions) is not None, "minSdk changed unexpectedly")
        require(re.search(r'targetSdk\s*=\s*"33"', versions) is not None, "targetSdk changed unexpectedly")

        protocol = text("app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt")
        protocol_json = text("app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt")
        require("const val PROTOCOL_VERSION = 2" in protocol, "Wire protocol is not 2")
        protocol_doc = text("docs/PROTOCOL.md")
        require("Unison 1.2 release line's only wire contract" in protocol_doc, "Protocol documentation is stale")
        require("protocol value other than `2` are rejected" in protocol_doc, "Protocol documentation has the wrong strict version")
        require("Protocol 3 was deliberately **not** introduced" in protocol_doc, "1.2 protocol decision is undocumented")
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
        srp_core = text("app/src/main/java/com/darius/unison/protocol/Srp6aCore.kt")
        srp_vector_test = text("app/src/test/java/com/darius/unison/protocol/Srp6aCoreRfc5054Test.kt")
        priority_test = text("app/src/test/java/com/darius/unison/network/ControlConnectionPriorityTest.kt")
        lifecycle_seam_test = text("app/src/test/java/com/darius/unison/room/RoomLifecycleSeamRegressionTest.kt")
        require("BigInteger.modPow" in srp_core, "SRP arithmetic core no longer documents JVM modular exponentiation")
        require("RFC 5054 Appendix B" in srp_vector_test, "Published SRP conformance vector test is missing")
        require("repeat(2_000)" in priority_test, "Control priority no-starvation stress coverage is missing")
        require("obsoleteRoomAdmissionAndSupersededSocketAreRejectedAtConsumeTime" in lifecycle_seam_test, "Lifecycle seam regression coverage is missing")

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
        require("room.event.unexpected_handler_cancellation" in room_runtime, "Unexpected actor-handler cancellation diagnostic is missing")
        require("room.event.stale_session" in room_runtime, "Stale session diagnostic is missing")
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
        transfer_coordinator = text("app/src/main/java/com/darius/unison/room/TransferCoordinator.kt")
        transfer_capacity = text("app/src/main/java/com/darius/unison/transfer/TransferCapacityPolicy.kt")
        transfer_manager = text("app/src/main/java/com/darius/unison/transfer/TransferManager.kt")
        peer_health = text("app/src/main/java/com/darius/unison/room/PeerPlaybackHealthRegistry.kt")
        require("TransferPriority" in protocol and "neededByCoordinatorNs" in protocol, "Transfer demand lost playback priority/deadline")
        for field in ("TransferFailureStage", "TransferFailureCode", "TransferFailureBlame", "retryable"):
            require(field in protocol, f"Typed transfer failure field is missing: {field}")
        require("PENDING_TRACK_PREPARATION_TIMEOUT" not in room_runtime, "Arbitrary playback preparation timeout returned")
        require("preemptionCandidate" not in transfer_coordinator, "Blind active-transfer preemption returned")
        track_prefetch = text("app/src/main/java/com/darius/unison/room/TrackPrefetchPolicy.kt")
        require("desiredPrefetchTrackIds" not in room_runtime, "Duplicated prefetch desired-set state returned")
        require("obsoleteTracks" not in track_prefetch and "prioritizedDesiredItems" not in track_prefetch, "Dead 1.1 prefetch helper API remains")
        require(
            all(marker in transfer_capacity for marker in (
                "maxInboundPerDestination",
                "maxOutboundPerSource",
                "maxPerSourceDestinationPair",
            )),
            "Shared transfer capacity policy is incomplete",
        )
        require(
            "canAdmit" in transfer_coordinator and "activeSourceCount" in transfer_coordinator,
            "Coordinator transfer admission does not enforce source/destination capacity",
        )
        require(
            "capacityPolicy.maxOutboundPerSource" in transfer_manager
            and "capacityPolicy.maxInboundPerDestination" in transfer_manager
            and "capacityPolicy.maxPerSourceDestinationPair" in transfer_manager,
            "Transport guards drifted from the shared transfer capacity policy",
        )
        require("transfer.download.duplicate_ignored" in transfer_manager, "Duplicate download assignment guard is missing")
        require("currentCoroutineContext().ensureActive()" in transfer_manager, "Cancellation can be misclassified as transfer failure")
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
        readiness_policy = text("app/src/main/java/com/darius/unison/room/RoomMediaReadinessPolicy.kt")
        transport_target_policy = text("app/src/main/java/com/darius/unison/room/TransportTargetPolicy.kt")
        commands = text("app/src/main/java/com/darius/unison/model/Commands.kt")
        require("enum class RoomMediaReadiness" in domain, "Room media readiness model is missing")
        require("NEEDS_PREPARATION" in readiness_policy and "PREPARING" in readiness_policy and "READY" in readiness_policy, "Room media readiness states drifted")
        require("locallyAvailableTrackIds" in readiness_policy, "READY no longer requires verified local availability")
        require("data class PrepareQueueItem" in commands, "Explicit Prepare command is missing")
        require(
            "Prepare this song before playing it" in transport_target_policy,
            "Arbitrary unready selection no longer requires explicit preparation",
        )
        require(
            "waitForPreparationQueueItemId" in transport_target_policy and
            "PendingSuccessor" in room_runtime and
            "PendingSuccessorReason.USER_NEXT" in room_runtime,
            "Sequential Next no longer converges through one pending successor",
        )
        media3_adapter = text("app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt")
        player_state_event_policy = text("app/src/main/java/com/darius/unison/playback/PlayerStateEventPolicy.kt")
        require(
            "PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM" in media3_adapter and
            "recordNaturalBoundary" in media3_adapter and
            "itemBoundaryRevision = state.itemBoundaryRevision" in player_state_event_policy,
            "Natural Media3 boundaries are no longer guaranteed to reach the room actor",
        )
        require("PendingTrackTransition" not in production, "Generic deferred prepare-then-play machinery returned")
        require("pendingTarget" not in transport_target_policy, "Deferred navigation target returned to target policy")
        require("playback.execution.waiting_for_media" in room_runtime, "Local execution no longer gates unavailable media")
        require("playbackExecutable" in text("app/src/main/java/com/darius/unison/room/PlaybackConvergencePolicy.kt"), "Coordinator convergence no longer gates unavailable media")
        room_service = text("app/src/main/java/com/darius/unison/playback/UnisonRoomService.kt")
        player_executor = text("app/src/main/java/com/darius/unison/playback/PlayerExecutor.kt")
        room_issue = text("app/src/main/java/com/darius/unison/model/RoomIssue.kt")
        reconnect_policy = text("app/src/main/java/com/darius/unison/room/RoomReconnectPolicy.kt")
        require("ROOM_ENDED" in room_issue and "setRoomEnded(" in room_runtime, "Terminal room-ended semantics are missing")
        require("runtime.handle(AppCommand.LeaveRoom)" in room_service, "Removing the app task no longer exits the live room")
        on_task_removed = re.search(r"override fun onTaskRemoved\([^)]*\) \{(?P<body>.*?)\n    \}", room_service, re.DOTALL)
        require(on_task_removed is not None, "Room service task-removal handler is missing")
        require("scheduleStopWhenIdle()" not in on_task_removed.group("body"), "Task removal reverted to background room survival")
        require("LOCAL_NETWORK_GRACE_MS" in reconnect_policy and "PEER_DISCONNECT_GRACE_MS" in reconnect_policy, "Bounded room/peer recovery windows are missing")
        require("PlaybackPauseCause.CONNECTION_INTERRUPTION" in room_runtime, "Connectivity loss no longer pauses local playback truthfully")
        require("room.peer.removed_after_disconnect" in room_runtime, "Disconnected peers no longer converge out of canonical membership")
        require('setRoomEnded("The room network is unavailable")' in room_runtime, "Coordinator local-network loss no longer ends after bounded recovery")
        require("minOf(" in player_executor and "CLOCK_RECHECK_INTERVAL_MS" in player_executor, "Scheduled playback can oversleep against a stale clock mapping")
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
        require('contains("offset"' not in transfer_manager, "Transfer behavior depends on English offset text")
        require("message.lowercase()" not in transfer_manager, "Transfer failure classification depends on user-facing text")
        require("FileResponseStatus.NOT_FOUND" in transfer_manager, "Typed source-unavailable response is missing")
        require("127.0.0.1" not in room_runtime, "Room peer endpoint must never synthesize loopback")
        require("transferRetryJobs" not in room_runtime, "Per-route transfer retry jobs returned")

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
        require("ScreenTopBar(title = \"Unison\")" in home, "Home reverted to a card-style app bar")

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
        require(re.search(r"\bTODO\b|\bFIXME\b|\bHACK\b", production) is None, "Production TODO/FIXME/HACK remains")
        require(re.search(r"https?://", production) is None, "Hard-coded remote endpoint found")
        require(re.search(r"firebase|play-services|billingclient", text("app/build.gradle.kts") + versions, re.IGNORECASE) is None, "Hosted/store runtime dependency found")


        print("SOURCE_TREE_OK")
        return 0
    except AssertionError as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
