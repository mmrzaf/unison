#!/usr/bin/env python3
"""Small dependency-free Kotlin source sanity checks.

This is not a compiler. It catches high-value mistakes that the offline core harness does not
compile, especially duplicate named arguments in Android/Compose source.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src/main/java"


def sanitize(text: str) -> str:
    out = list(text)
    i = 0
    state = "normal"
    block_depth = 0
    while i < len(text):
        if state == "normal":
            if text.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line"
            elif text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block"
                block_depth = 1
            elif text.startswith('"""', i):
                out[i:i + 3] = "   "
                i += 3
                state = "triple"
            elif text[i] == '"':
                out[i] = " "
                i += 1
                state = "string"
            elif text[i] == "'":
                out[i] = " "
                i += 1
                state = "char"
            else:
                i += 1
        elif state == "line":
            if text[i] == "\n":
                state = "normal"
            else:
                out[i] = " "
            i += 1
        elif state == "block":
            if text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                block_depth += 1
                i += 2
            elif text.startswith("*/", i):
                out[i] = out[i + 1] = " "
                block_depth -= 1
                i += 2
                if block_depth == 0:
                    state = "normal"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        elif state == "triple":
            if text.startswith('"""', i):
                out[i:i + 3] = "   "
                i += 3
                state = "normal"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        else:  # normal string or char
            quote = '"' if state == "string" else "'"
            if text[i] == "\\":
                out[i] = " "
                if i + 1 < len(text):
                    if text[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                else:
                    i += 1
            elif text[i] == quote:
                out[i] = " "
                i += 1
                state = "normal"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def delimiter_problems(path: Path) -> list[str]:
    text = sanitize(path.read_text(errors="ignore"))
    pairs = {")": "(", "]": "[", "}": "{"}
    stack: list[tuple[str, int]] = []
    problems: list[str] = []
    for index, ch in enumerate(text):
        if ch in "([{":
            stack.append((ch, index))
        elif ch in ")]}":
            line = text.count("\n", 0, index) + 1
            if not stack or stack[-1][0] != pairs[ch]:
                problems.append(f"{path.relative_to(ROOT)}:{line}: unmatched '{ch}'")
                continue
            stack.pop()
    for ch, index in stack:
        line = text.count("\n", 0, index) + 1
        problems.append(f"{path.relative_to(ROOT)}:{line}: unclosed '{ch}'")
    return problems


def duplicate_named_arguments(path: Path) -> list[str]:
    raw = path.read_text(errors="ignore")
    text = sanitize(raw)
    stack: list[dict[str, int]] = []
    problems: list[str] = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "(":
            stack.append({})
            i += 1
            continue
        if ch == ")":
            if stack:
                stack.pop()
            i += 1
            continue
        if ch == "=" and stack:
            previous = text[i - 1] if i > 0 else ""
            following = text[i + 1] if i + 1 < len(text) else ""
            if previous not in "=!<>+-*/%&|^" and following not in "=>":
                j = i - 1
                while j >= 0 and text[j].isspace():
                    j -= 1
                end = j + 1
                while j >= 0 and (text[j].isalnum() or text[j] == "_"):
                    j -= 1
                name = text[j + 1:end]
                k = j
                while k >= 0 and text[k].isspace():
                    k -= 1
                if name and (k < 0 or text[k] in "(,"):
                    line = text.count("\n", 0, i) + 1
                    first = stack[-1].get(name)
                    if first is not None:
                        problems.append(
                            f"{path.relative_to(ROOT)}:{line}: duplicate named argument "
                            f"'{name}' (first at line {first})"
                        )
                    else:
                        stack[-1][name] = line
        i += 1
    return problems



def misplaced_imports(path: Path) -> list[str]:
    problems: list[str] = []
    seen_declaration = False
    in_block_comment = False
    for line_number, line in enumerate(path.read_text(errors="ignore").splitlines(), 1):
        stripped = line.strip()
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped.startswith("/*"):
            in_block_comment = "*/" not in stripped
            continue
        if not stripped or stripped.startswith("//") or stripped.startswith("@file:"):
            continue
        if stripped.startswith("package "):
            continue
        if stripped.startswith("import "):
            if seen_declaration:
                problems.append(
                    f"{path.relative_to(ROOT)}:{line_number}: import appears after a declaration"
                )
            continue
        seen_declaration = True
    return problems

def main() -> int:
    problems: list[str] = []
    for path in SOURCE_ROOT.rglob("*.kt"):
        problems.extend(delimiter_problems(path))
        problems.extend(duplicate_named_arguments(path))
        problems.extend(misplaced_imports(path))

    app = (SOURCE_ROOT / "com/darius/unison/ui/UnisonApp.kt").read_text()
    used_icons = set(re.findall(r"Icons\.Default\.([A-Za-z0-9_]+)", app))
    imported_icons = set(
        re.findall(r"import androidx\.compose\.material\.icons\.filled\.([A-Za-z0-9_]+)", app)
    )
    for icon in sorted(used_icons - imported_icons):
        problems.append(f"UnisonApp.kt: missing filled icon import for {icon}")

    runtime = (SOURCE_ROOT / "com/darius/unison/room/RoomRuntime.kt").read_text()
    wifi_locks = (SOURCE_ROOT / "com/darius/unison/network/WifiLocks.kt").read_text()
    room_power = (SOURCE_ROOT / "com/darius/unison/room/RoomPowerPolicy.kt").read_text()
    control_client = (SOURCE_ROOT / "com/darius/unison/network/ControlClient.kt").read_text()
    address_policy = (SOURCE_ROOT / "com/darius/unison/network/NetworkAddressPolicy.kt").read_text()
    room_screen = (SOURCE_ROOT / "com/darius/unison/ui/room/RoomScreens.kt").read_text()
    room_playback_components = (
        SOURCE_ROOT / "com/darius/unison/ui/room/RoomPlaybackComponents.kt"
    ).read_text()
    diagnostic_log = (SOURCE_ROOT / "com/darius/unison/util/DiagnosticLog.kt").read_text()
    scheduled_playback = (SOURCE_ROOT / "com/darius/unison/playback/ScheduledPlaybackController.kt").read_text()
    player_mutations = (SOURCE_ROOT / "com/darius/unison/playback/PlayerMutationCoordinator.kt").read_text()
    transport_lead = (SOURCE_ROOT / "com/darius/unison/room/TransportLeadTimePolicy.kt").read_text()
    transport_target = (SOURCE_ROOT / "com/darius/unison/room/TransportTargetPolicy.kt").read_text()
    command_bus = (SOURCE_ROOT / "com/darius/unison/app/RoomCommandBus.kt").read_text()

    required_runtime_markers = {
        "JoinRetryPolicy.decide": "Initial join retries are no longer policy-driven",
        "RoomEvent.InitialJoinConnected": "Initial socket admission is no longer actor-safe",
        "HeartbeatLivenessPolicy": "Sleep-aware heartbeat grace is missing",
        "RoomReconnectPolicy.MAX_ATTEMPTS": "Reconnect attempts are no longer bounded by policy",
        "launchSnapshotPreparation": "Snapshot file preparation can block the room actor again",
    }
    for marker, message in required_runtime_markers.items():
        if marker not in runtime:
            problems.append(message)

    production_text = "\n".join(
        path.read_text(errors="ignore") for path in SOURCE_ROOT.rglob("*.kt")
    )
    forbidden_artwork_markers = (
        "TrackArtwork",
        "ArtworkStore",
        "artworkStore",
        "setArtworkUri",
        "artworkFile",
    )
    for marker in forbidden_artwork_markers:
        if marker in production_text:
            problems.append(f"Artwork pipeline was reintroduced: {marker}")
    service = (SOURCE_ROOT / "com/darius/unison/playback/UnisonRoomService.kt").read_text()
    session_player = (
        SOURCE_ROOT / "com/darius/unison/playback/RoomMediaSessionPlayer.kt"
    ).read_text()
    media_adapter = (SOURCE_ROOT / "com/darius/unison/playback/Media3PlayerAdapter.kt").read_text()
    if "startAsForeground" in service or "NotificationCompat.Builder" in service:
        problems.append("Generic foreground notification path was reintroduced")
    if "DefaultMediaNotificationProvider" not in service:
        problems.append("Media3 player-control notification provider is missing")
    if "UnisonMediaArtwork.createPng()" not in service:
        problems.append("Fixed Unison system-media artwork is missing")
    if "setArtworkData(systemArtworkData" not in session_player:
        problems.append("System media metadata no longer receives fixed Unison artwork")
    if "val scheduledForStartId = latestStartId" not in service or "stopSelfResult(scheduledForStartId)" not in service:
        problems.append("Idle service shutdown is not bound to its scheduled start ID")
    if "lifecycleScope" not in service or "Dispatchers.Main.immediate" not in service:
        problems.append("Service lifecycle mutation is no longer serialized on the main dispatcher")
    if "RoomServicePolicy.playbackActive" not in service:
        problems.append("Service lifetime can be retained by stale playWhenReady without a media item")
    if not re.search(r"playbackSpeedGate\s*\.select", runtime):
        problems.append("Playback-speed actuator commands are no longer gated")
    if "PlaybackSynchronizationPolicy" in production_text or "SOLO_COORDINATOR" in production_text:
        problems.append("Participant-count playback synchronization bypass was reintroduced")
    if not re.search(
        r"val\s+canonical\s*=\s*if\s*\(coordinator\)\s*snapshot\.playback\s*"
        r"else\s*latestPlaybackStateSync\s*\?:\s*snapshot\.playback",
        runtime,
    ):
        problems.append("Coordinator and participants no longer follow the same canonical room timeline")
    if "PlaybackReferencePolicy" in production_text:
        problems.append("A physical player was reintroduced as the canonical room clock")
    if "outputLatencyOffsetMs = if (coordinator)" in runtime:
        problems.append("Coordinator route latency bypass was reintroduced")
    if "SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER" not in service:
        problems.append("Idle playback can resurrect a stale media notification after the room is cleared")
    if "onLocalInterruption" in media_adapter:
        problems.append("Local audio focus changes can become room-wide transport commands again")
    if "locallySuppressed" not in media_adapter or "PlaybackIntentReconciliationPolicy.decide" not in runtime:
        problems.append("Device-local audio safety pauses are no longer isolated from room transport")
    if "PlaybackIntentReconciliationPolicy.decidePlayRequest" not in runtime:
        problems.append("A locally suppressed phone can reschedule the whole room when Play is pressed")
    room_store = (SOURCE_ROOT / "com/darius/unison/app/RoomStore.kt").read_text()
    if "MutableStateFlow(RoomUiState())" in room_store:
        problems.append("Full aggregate room state is rebuilt on playback telemetry again")
    if "exoPlayer.playWhenReady = false" not in media_adapter or "exoPlayer.clearMediaItems()" not in media_adapter:
        problems.append("Clearing the player can retain stale playback intent")
    if "stopSelf()" in service:
        problems.append("Unbound service shutdown was reintroduced; use start-ID-safe delayed stop")
    if "roomCommandBus.hasOutstandingCommands" not in service:
        problems.append("Accepted room commands no longer block idle service shutdown")
    if "if (!isCoordinator()) submitSessionEvent(generation, RoomEvent.ClockSyncTick)" not in runtime:
        problems.append("Coordinator rooms enqueue redundant clock-sync actor work again")
    if "replaceMediaItem" in media_adapter:
        problems.append("Unchanged logical queue items are being replaced in Media3 again")
    if "withContext(Dispatchers.Default) { items.map(::toMediaItem) }" not in media_adapter:
        problems.append("Queue metadata construction moved back onto the player main thread")
    if "setArtworkUri" in media_adapter:
        problems.append("Player metadata exposes artwork again")
    pause_start = scheduled_playback.find("fun schedulePause(")
    pause_end = scheduled_playback.find("fun scheduleSeek(", pause_start)
    pause_block = scheduled_playback[pause_start:pause_end] if pause_start >= 0 and pause_end > pause_start else ""
    if "seekTo" in pause_block or "seekToItem" in pause_block:
        problems.append("Pause seeks the decoder again")
    if "PLAY_POSITION_TOLERANCE_MS" not in scheduled_playback:
        problems.append("Play no longer avoids unnecessary same-item seeks")
    if "maintenanceIfTransportIdle" not in player_mutations or "hasPendingTransport" not in player_mutations:
        problems.append("Timeline maintenance can interleave with explicit transport again")
    if "MIN_LEAD_NS = 150_000_000L" not in transport_lead or "MAX_LEAD_NS = 1_200_000_000L" not in transport_lead:
        problems.append("Adaptive transport lead escaped its responsive safety bounds")
    if "pendingResumePlayback" not in transport_target:
        problems.append("Pending Next/Previous no longer preserves playback intent")
    if "Channel<AppCommand.Transport>(capacity = TRANSPORT_CAPACITY)" not in command_bus:
        problems.append("Transport commands lost their independent mailbox capacity")
    if (
        "TransportCommandPhase.SCHEDULED" not in room_playback_components
        or "TransportCommandPhase.EXECUTING" not in room_playback_components
    ):
        problems.append("Transport UI no longer renders the real command lifecycle")
    writer_block = diagnostic_log.find("writer.execute")
    logcat_block = diagnostic_log.find("if (writeToLogcat)")
    if writer_block < 0 or logcat_block < writer_block:
        problems.append("Logcat output moved back onto application callers")

    if "PowerManager.PARTIAL_WAKE_LOCK" not in wifi_locks:
        problems.append("Active rooms no longer keep CPU execution available with the screen off")
    if "WIFI_MODE_FULL_HIGH_PERF" not in wifi_locks or "WIFI_MODE_FULL_LOW_LATENCY" not in wifi_locks:
        problems.append("Screen-on/screen-off Wi-Fi lock coverage is incomplete")
    if "Demand(wifi = true, cpu = true)" not in room_power:
        problems.append("Every active peer must retain CPU and Wi-Fi ownership")
    if "runInterruptible(Dispatchers.IO)" not in control_client:
        problems.append("Room admission sockets are no longer cancellation-friendly")
    if "parseAllowedAddress" not in address_policy or "isUniqueLocalAddress" not in address_policy:
        problems.append("Private IPv4/IPv6 endpoint support is incomplete")
    if "onCancelConnection" not in room_screen or "Connection interrupted" not in room_screen:
        problems.append("Join/reconnect UI no longer exposes recovery and cancellation")
    duplicate_join_status = (
        'Text(\n                                    state.room.statusMessage ?: "Connecting…",\n'
        '                                    state.room.statusMessage'
    )
    if duplicate_join_status in room_screen:
        problems.append("Joining status Text contains a duplicate positional argument")

    if "withTimeoutOrNull(MANUAL_DISCOVERY_WINDOW_MS)" not in runtime:
        problems.append("Room discovery is no longer bounded by the manual scan window")
    if "MANUAL_DISCOVERY_WINDOW_MS = 8_000L" not in runtime:
        problems.append("Manual room discovery must remain an 8-second user-triggered scan")
    if app.count("AppCommand.StartDiscovery") != 1:
        problems.append("Room discovery must have exactly one explicit UI trigger")

    changelog = (ROOT / "CHANGELOG.md").read_text().lower()
    if "discovery automatic" in changelog or "automatic while the room lobby" in changelog:
        problems.append("Changelog still documents the rejected automatic-discovery behavior")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Kotlin source sanity checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
