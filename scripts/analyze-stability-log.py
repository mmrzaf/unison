#!/usr/bin/env python3
"""Small release-oriented analyzer for Unison room/transfer stability diagnostics."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path


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


def analyze(events, malformed=0):
    counts = Counter()
    attempts_by_track = Counter()
    completed_by_track = Counter()
    retries_by_route = Counter()
    failures_by_phase = Counter()
    max_late_ms = 0
    teardown_violations = []

    for event in events:
        name = event.get("eventName", "")
        counts[name] += 1
        track = attr(event, "track.id")
        if name == "transfer.download.connecting" and track:
            attempts_by_track[track] += 1
        elif name == "transfer.download.completed" and track:
            completed_by_track[track] += 1
        elif name in {"transfer.track.failed", "transfer.download.failure_detail"}:
            failures_by_phase[str(attr(event, "transfer.phase", "UNKNOWN"))] += 1
        elif name == "transfer.retry.scheduled":
            route = (track, attr(event, "transfer.destination_peer_id"))
            retries_by_route[route] += 1
        elif name == "playback.command.executing":
            late = attr(event, "playback.late_ms", 0)
            if isinstance(late, (int, float)):
                max_late_ms = max(max_late_ms, int(late))
        elif name == "room.session.ended":
            remaining = attr(event, "coroutine.remaining_jobs", 0)
            active = attr(event, "transfer.active_count", 0)
            if isinstance(remaining, (int, float)) and remaining > 0:
                teardown_violations.append(f"room ended with {int(remaining)} remaining coroutine jobs")
            if isinstance(active, (int, float)) and active > 0:
                teardown_violations.append(f"room ended with {int(active)} active transfers")

    unavailable_rejections = 0
    handshake_timeouts = 0
    for event in events:
        if event.get("eventName") == "playback.command.rejected" and "not ready" in str(event.get("body", "")).lower():
            unavailable_rejections += 1
        if (
            event.get("eventName") == "transfer.download.failure_detail"
            and str(attr(event, "transfer.phase", "")).upper() == "HANDSHAKE"
            and "timeout" in str((event.get("exception") or {}).get("type", "")).lower()
        ):
            handshake_timeouts += 1

    churn_tracks = {
        track: attempts
        for track, attempts in attempts_by_track.items()
        if attempts >= 4 and completed_by_track[track] == 0
    }
    retry_storm_routes = {route: count for route, count in retries_by_route.items() if count >= 4}

    violations = []
    if malformed:
        violations.append(f"{malformed} malformed diagnostic records")
    if unavailable_rejections:
        violations.append(f"{unavailable_rejections} unavailable-media playback rejections")
    if counts["transfer.download.duplicate_ignored"]:
        violations.append(f"{counts['transfer.download.duplicate_ignored']} duplicate transfer assignments")
    if handshake_timeouts:
        violations.append(f"{handshake_timeouts} transfer handshake timeouts")
    if churn_tracks:
        detail = ", ".join(f"{track}:{attempts}" for track, attempts in sorted(churn_tracks.items()))
        violations.append(f"transfer reconnect churn without completion ({detail})")
    if retry_storm_routes:
        detail = ", ".join(f"{track}->{dest}:{count}" for (track, dest), count in sorted(retry_storm_routes.items()))
        violations.append(f"transfer retry storm ({detail})")
    if max_late_ms > 1_000:
        violations.append(f"scheduled playback command executed {max_late_ms} ms late")
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
        "unavailable_playback_rejections": unavailable_rejections,
        "max_playback_late_ms": max_late_ms,
        "failures_by_phase": dict(sorted(failures_by_phase.items())),
        "violations": violations,
    }


def self_test():
    good = [
        {"eventName": "transfer.download.connecting", "attributes": {"track.id": "a", "transfer.operation_id": "op1"}},
        {"eventName": "transfer.download.completed", "attributes": {"track.id": "a", "transfer.operation_id": "op1"}},
        {"eventName": "playback.command.executing", "attributes": {"playback.late_ms": 120}},
        {"eventName": "room.session.ended", "attributes": {"coroutine.remaining_jobs": 0, "transfer.active_count": 0}},
    ]
    result = analyze(good)
    assert not result["violations"], result

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
            {"eventName": "playback.command.rejected", "body": "This song is not ready yet", "attributes": {}},
            {"eventName": "transfer.download.duplicate_ignored", "attributes": {"track.id": "deadbeef"}},
            {"eventName": "playback.command.executing", "attributes": {"playback.late_ms": 6476}},
            {"eventName": "room.session.ended", "attributes": {"coroutine.remaining_jobs": 2, "transfer.active_count": 1}},
            {
                "eventName": "transfer.download.failure_detail",
                "attributes": {"track.id": "deadbeef", "transfer.phase": "HANDSHAKE"},
                "exception": {"type": "java.net.SocketTimeoutException"},
            },
        ]
    )
    result = analyze(bad)
    assert len(result["violations"]) >= 7, result
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
        print(f"unavailable playback rejections={result['unavailable_playback_rejections']}")
        print(f"max playback lateness={result['max_playback_late_ms']} ms")
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
