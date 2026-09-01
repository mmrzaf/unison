#!/usr/bin/env python3
"""Release-oriented analyzer for Unison room/transfer stability diagnostics."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

LATE_THRESHOLD_MS = 1_000
LEGACY_IMMEDIATE_EXECUTION_MS = 100


def load_events(path: Path):
    events = []
    malformed = 0
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError:
                malformed += 1
                continue
            if isinstance(value, dict):
                events.append(value)
            else:
                malformed += 1
    return events, malformed


def attr(event, key, default=None):
    attrs = event.get("attributes")
    return attrs.get(key, default) if isinstance(attrs, dict) else default


def timestamp(event) -> datetime | None:
    value = event.get("timestamp")
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def unavailable_reason(event) -> bool:
    text = " ".join(
        str(value or "")
        for value in (
            event.get("body"),
            attr(event, "reason"),
            attr(event, "transport.message"),
        )
    ).lower()
    return any(
        marker in text
        for marker in (
            "not ready",
            "unavailable",
            "prepare this song before playing it",
            "needs preparation",
        )
    )


def schedule_key(event):
    command_id = attr(event, "command.id")
    if command_id:
        return ("command", str(command_id))
    return (
        "anonymous",
        str(attr(event, "command.type", "")),
        str(attr(event, "queue.item_id", "")),
    )


def transfer_attempt_identity(event):
    operation_id = attr(event, "transfer.operation_id")
    if operation_id:
        return ("operation", str(operation_id))
    assignment_id = attr(event, "transfer.assignment_id")
    if assignment_id:
        return ("assignment", str(assignment_id))
    return None


def analyze(events, malformed=0):
    counts = Counter()
    attempt_keys_by_track = {}
    legacy_attempts_by_track = Counter()
    completed_by_track = Counter()
    retries_by_route = Counter()
    failures_by_phase = Counter()
    socket_route_failures_by_reason = Counter()
    socket_route_attempts = 0
    socket_route_failures = 0
    teardown_violations = []
    unavailable_rejection_keys = set()
    legacy_unavailable_rejections = []
    transport_unavailable_rejections = []

    scheduled = {}
    max_total_late_ms = 0
    max_arrival_late_ms = 0
    max_executor_late_ms = 0

    for event in events:
        name = event.get("eventName", "")
        counts[name] += 1
        track = attr(event, "track.id")

        if name in {
            "transfer.download.route_start",
            "transfer.download.connecting",
            "transfer.download.failure_detail",
        } and track:
            attempt_identity = transfer_attempt_identity(event)
            if attempt_identity is not None:
                attempt_keys_by_track.setdefault(track, set()).add(attempt_identity)
            elif name != "transfer.download.failure_detail":
                # Legacy logs may lack operation/assignment IDs. Count route/connect start events,
                # but do not infer an extra attempt from their adjacent failure-detail record.
                legacy_attempts_by_track[track] += 1

        if name == "network.socket.route_attempt" and attr(event, "network.socket_purpose") == "transfer":
            socket_route_attempts += 1
        elif name == "network.socket.route_failed" and attr(event, "network.socket_purpose") == "transfer":
            socket_route_failures += 1
            socket_route_failures_by_reason[str(attr(event, "network.failure_reason", "UNKNOWN"))] += 1

        if name == "transfer.download.completed" and track:
            completed_by_track[track] += 1
        elif name == "transfer.track.failed":
            # Terminal failure events own release accounting. The adjacent
            # transfer.download.failure_detail event is diagnostic context for the same failed
            # attempt and must not double-count the phase.
            failures_by_phase[str(attr(event, "transfer.phase", "UNKNOWN"))] += 1
        elif name == "transfer.retry.scheduled":
            route = (track, attr(event, "transfer.destination_peer_id"))
            retries_by_route[route] += 1
        elif name == "playback.command.scheduled":
            scheduled[schedule_key(event)] = event
        elif name == "playback.command.executing":
            total_late = attr(event, "playback.late_ms", 0)
            if not isinstance(total_late, (int, float)):
                total_late = 0
            total_late = max(0, int(total_late))
            max_total_late_ms = max(max_total_late_ms, total_late)

            arrival_late = attr(event, "playback.arrival_late_ms")
            executor_late = attr(event, "playback.executor_late_ms")
            if isinstance(arrival_late, (int, float)):
                arrival_late = max(0, int(arrival_late))
            else:
                arrival_late = None
            if isinstance(executor_late, (int, float)):
                executor_late = max(0, int(executor_late))
            else:
                executor_late = None

            schedule_event = scheduled.get(schedule_key(event))
            if arrival_late is None and executor_late is None and total_late > 0 and schedule_event is not None:
                scheduled_at = timestamp(schedule_event)
                executed_at = timestamp(event)
                delay_ms = None
                if scheduled_at is not None and executed_at is not None:
                    delay_ms = max(0, int((executed_at - scheduled_at).total_seconds() * 1_000))
                # Legacy diagnostics only exposed total lateness. If the command was logged as
                # scheduled immediately before an already-late execution, attribute the lateness to
                # arrival/actor/network delay rather than blaming PlayerExecutor.
                if delay_ms is not None and delay_ms <= LEGACY_IMMEDIATE_EXECUTION_MS:
                    arrival_late = total_late
                    executor_late = 0
                else:
                    arrival_late = 0
                    executor_late = total_late
            else:
                if arrival_late is None:
                    arrival_late = 0
                if executor_late is None:
                    executor_late = max(0, total_late - arrival_late)

            max_arrival_late_ms = max(max_arrival_late_ms, arrival_late)
            max_executor_late_ms = max(max_executor_late_ms, executor_late)
        elif name == "room.session.ended":
            remaining = attr(event, "coroutine.remaining_jobs", 0)
            active = attr(event, "transfer.active_count", 0)
            if isinstance(remaining, (int, float)) and remaining > 0:
                teardown_violations.append(f"room ended with {int(remaining)} remaining coroutine jobs")
            if isinstance(active, (int, float)) and active > 0:
                teardown_violations.append(f"room ended with {int(active)} active transfers")

        if name == "room.command.rejected" and unavailable_reason(event):
            when = timestamp(event)
            if when is not None:
                legacy_unavailable_rejections.append(when)
        if name == "playback.command.rejected" and unavailable_reason(event):
            command_id = attr(event, "command.id")
            unavailable_rejection_keys.add(str(command_id) if command_id else f"sequence:{event.get('sequence')}:{name}")
        if name == "room.transport.status" and attr(event, "transport.phase") == "REJECTED" and unavailable_reason(event):
            command_id = attr(event, "command.id")
            unavailable_rejection_keys.add(str(command_id) if command_id else f"sequence:{event.get('sequence')}:{name}")
            when = timestamp(event)
            if when is not None:
                transport_unavailable_rejections.append(when)

    for rejected_at in legacy_unavailable_rejections:
        if not any(
            0.0 <= (transport_at - rejected_at).total_seconds() <= 0.250
            for transport_at in transport_unavailable_rejections
        ):
            unavailable_rejection_keys.add(f"legacy:{rejected_at.isoformat()}")

    attempts_by_track = Counter(
        {
            track: len(keys) + legacy_attempts_by_track[track]
            for track, keys in attempt_keys_by_track.items()
        }
    )
    for track, count in legacy_attempts_by_track.items():
        if track not in attempts_by_track:
            attempts_by_track[track] = count

    handshake_timeout_keys = set()
    for event in events:
        if event.get("eventName") != "transfer.track.failed":
            continue
        if str(attr(event, "transfer.phase", "")).upper() != "HANDSHAKE":
            continue
        exception = event.get("exception") or {}
        if "timeout" not in str(exception.get("type", "")).lower() and "timeout" not in str(exception.get("message", "")).lower():
            continue
        operation_id = attr(event, "transfer.operation_id")
        assignment_id = attr(event, "transfer.assignment_id")
        handshake_timeout_keys.add(str(operation_id or assignment_id or event.get("sequence")))

    churn_tracks = {
        track: attempts
        for track, attempts in attempts_by_track.items()
        if attempts >= 4 and completed_by_track[track] == 0
    }
    retry_storm_routes = {route: count for route, count in retries_by_route.items() if count >= 4}

    violations = []
    if malformed:
        violations.append(f"{malformed} malformed diagnostic records")
    if unavailable_rejection_keys:
        violations.append(f"{len(unavailable_rejection_keys)} unavailable-media playback rejections")
    if counts["transfer.download.duplicate_ignored"]:
        violations.append(f"{counts['transfer.download.duplicate_ignored']} duplicate transfer assignments")
    if handshake_timeout_keys:
        violations.append(f"{len(handshake_timeout_keys)} transfer handshake timeouts")
    if churn_tracks:
        detail = ", ".join(f"{track}:{attempts}" for track, attempts in sorted(churn_tracks.items()))
        violations.append(f"transfer reconnect churn without completion ({detail})")
    if retry_storm_routes:
        detail = ", ".join(f"{track}->{dest}:{count}" for (track, dest), count in sorted(retry_storm_routes.items()))
        violations.append(f"transfer retry storm ({detail})")
    if counts["room.event.unexpected_handler_cancellation"]:
        violations.append(
            f"{counts['room.event.unexpected_handler_cancellation']} unexpected room actor handler cancellations"
        )
    if max_arrival_late_ms > LATE_THRESHOLD_MS:
        violations.append(f"scheduled playback arrived {max_arrival_late_ms} ms late before PlayerExecutor")
    if max_executor_late_ms > LATE_THRESHOLD_MS:
        violations.append(f"PlayerExecutor missed scheduled playback by {max_executor_late_ms} ms")
    violations.extend(teardown_violations)

    return {
        "events": len(events),
        "malformed": malformed,
        "transfer_attempts": sum(attempts_by_track.values()),
        "transfer_completed": counts["transfer.download.completed"],
        "transfer_failed": counts["transfer.track.failed"],
        "transfer_cancelled": counts["transfer.download.cancelled"],
        "transfer_duplicate_ignored": counts["transfer.download.duplicate_ignored"],
        "transfer_retries": counts["transfer.retry.scheduled"],
        "transfer_route_starts": counts["transfer.download.route_start"],
        "socket_route_attempts": socket_route_attempts,
        "socket_route_failures": socket_route_failures,
        "socket_route_failures_by_reason": dict(sorted(socket_route_failures_by_reason.items())),
        "transfer_route_suspensions": counts["transfer.route.suspended"],
        "transfer_route_retry_requests": counts["transfer.route.retry_requested"],
        "unavailable_playback_rejections": len(unavailable_rejection_keys),
        "max_playback_late_ms": max_total_late_ms,
        "max_playback_arrival_late_ms": max_arrival_late_ms,
        "max_player_executor_late_ms": max_executor_late_ms,
        "failures_by_phase": dict(sorted(failures_by_phase.items())),
        "violations": violations,
    }


def self_test():
    good = [
        {"eventName": "transfer.download.route_start", "attributes": {"track.id": "a", "transfer.operation_id": "op1"}},
        {"eventName": "transfer.download.connecting", "attributes": {"track.id": "a", "transfer.operation_id": "op1"}},
        {"eventName": "transfer.download.completed", "attributes": {"track.id": "a", "transfer.operation_id": "op1"}},
        {"eventName": "playback.command.scheduled", "timestamp": "2026-01-01T10:00:00Z", "attributes": {"command.id": "cmd", "playback.arrival_late_ms": 0}},
        {"eventName": "playback.command.executing", "timestamp": "2026-01-01T10:00:00.120Z", "attributes": {"command.id": "cmd", "playback.late_ms": 120, "playback.arrival_late_ms": 0, "playback.executor_late_ms": 120}},
        {"eventName": "room.session.ended", "attributes": {"coroutine.remaining_jobs": 0, "transfer.active_count": 0}},
    ]
    result = analyze(good)
    assert result["transfer_attempts"] == 1, result
    assert not result["violations"], result

    preconnect = []
    for index in range(4):
        preconnect.extend(
            [
                {
                    "eventName": "transfer.download.failure_detail",
                    "attributes": {
                        "track.id": "preconnect",
                        "transfer.operation_id": f"op-{index}",
                        "transfer.phase": "CONNECT",
                    },
                    "exception": {"type": "java.net.SocketException", "message": "EPERM"},
                },
                {
                    "eventName": "transfer.track.failed",
                    "attributes": {
                        "track.id": "preconnect",
                        "transfer.operation_id": f"op-{index}",
                        "transfer.phase": "CONNECT",
                    },
                },
                {
                    "eventName": "transfer.retry.scheduled",
                    "attributes": {
                        "track.id": "preconnect",
                        "transfer.destination_peer_id": "peer",
                    },
                },
            ]
        )
    result = analyze(preconnect)
    assert result["transfer_attempts"] == 4, result
    assert any("transfer reconnect churn" in value for value in result["violations"]), result
    assert any("transfer retry storm" in value for value in result["violations"]), result

    arrival_late = [
        {"eventName": "playback.command.scheduled", "timestamp": "2026-01-01T10:00:00Z", "attributes": {"command.id": "late"}},
        {"eventName": "playback.command.executing", "timestamp": "2026-01-01T10:00:00.005Z", "attributes": {"command.id": "late", "playback.late_ms": 1_378}},
    ]
    result = analyze(arrival_late)
    assert result["max_playback_arrival_late_ms"] == 1_378, result
    assert result["max_player_executor_late_ms"] == 0, result
    assert any("before PlayerExecutor" in value for value in result["violations"]), result

    executor_late = [
        {"eventName": "playback.command.scheduled", "timestamp": "2026-01-01T10:00:00Z", "attributes": {"command.id": "late", "playback.arrival_late_ms": 0}},
        {"eventName": "playback.command.executing", "timestamp": "2026-01-01T10:00:02Z", "attributes": {"command.id": "late", "playback.late_ms": 1_300, "playback.arrival_late_ms": 0, "playback.executor_late_ms": 1_300}},
    ]
    result = analyze(executor_late)
    assert result["max_player_executor_late_ms"] == 1_300, result
    assert any("PlayerExecutor" in value for value in result["violations"]), result

    bad = []
    bad.extend(
        {"eventName": "transfer.download.connecting", "attributes": {"track.id": "deadbeef"}}
        for _ in range(4)
    )
    bad.extend(
        {"eventName": "transfer.retry.scheduled", "attributes": {"track.id": "deadbeef", "transfer.destination_peer_id": "peer"}}
        for _ in range(4)
    )
    bad.extend(
        [
            {
                "eventName": "room.command.rejected",
                "timestamp": "2026-01-01T10:00:03Z",
                "attributes": {"reason": "Prepare this song before playing it"},
            },
            {"eventName": "transfer.download.duplicate_ignored", "attributes": {"track.id": "deadbeef"}},
            {"eventName": "room.session.ended", "attributes": {"coroutine.remaining_jobs": 2, "transfer.active_count": 1}},
            {"eventName": "room.event.unexpected_handler_cancellation", "attributes": {"room.event_type": "Synthetic"}},
            {
                "eventName": "transfer.track.failed",
                "attributes": {"track.id": "deadbeef", "transfer.phase": "HANDSHAKE", "transfer.operation_id": "op"},
                "exception": {"type": "java.net.SocketTimeoutException"},
            },
            # The detail + terminal pair must count as one failed phase, not two.
            {
                "eventName": "transfer.download.failure_detail",
                "attributes": {"track.id": "deadbeef", "transfer.phase": "HANDSHAKE"},
                "exception": {"type": "java.net.SocketTimeoutException"},
            },
        ]
    )
    result = analyze(bad)
    assert result["failures_by_phase"] == {"HANDSHAKE": 1}, result
    assert any("unexpected room actor handler cancellations" in value for value in result["violations"]), result
    assert len(result["violations"]) >= 8, result
    print("STABILITY_LOG_ANALYZER_SELF_TEST_OK")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log", nargs="?", type=Path)
    parser.add_argument("--strict", action="store_true", help="exit non-zero when stability invariants are violated")
    parser.add_argument("--json", action="store_true", help="print machine-readable summary")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0
    if args.log is None:
        parser.error("log path is required unless --self-test is used")

    events, malformed = load_events(args.log)
    result = analyze(events, malformed)
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(f"events={result['events']}")
        print(f"transfer attempts={result['transfer_attempts']} completed={result['transfer_completed']} failed={result['transfer_failed']} cancelled={result['transfer_cancelled']} retries={result['transfer_retries']}")
        print(
            "socket route "
            f"attempts={result['socket_route_attempts']} failures={result['socket_route_failures']} "
            f"suspensions={result['transfer_route_suspensions']} "
            f"retry_requests={result['transfer_route_retry_requests']}"
        )
        if result["socket_route_failures_by_reason"]:
            print(
                "socket route failures by reason="
                + json.dumps(result["socket_route_failures_by_reason"], sort_keys=True)
            )
        print(f"unavailable playback rejections={result['unavailable_playback_rejections']}")
        print(
            "playback lateness total="
            f"{result['max_playback_late_ms']} ms arrival={result['max_playback_arrival_late_ms']} ms "
            f"executor={result['max_player_executor_late_ms']} ms"
        )
        if result["failures_by_phase"]:
            print("transfer failures by phase=" + json.dumps(result["failures_by_phase"], sort_keys=True))
        if result["violations"]:
            print("STABILITY_VIOLATIONS")
            for violation in result["violations"]:
                print(f"- {violation}")
        else:
            print("STABILITY_LOG_OK")

    return 1 if args.strict and result["violations"] else 0


if __name__ == "__main__":
    sys.exit(main())
