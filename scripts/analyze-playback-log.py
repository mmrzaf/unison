#!/usr/bin/env python3
"""Summarize Unison structured NDJSON diagnostics and enforce playback stability gates."""

from __future__ import annotations

import argparse
import json
import sys
from collections import deque
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

TERMINAL_PHASES = {"SETTLED", "SUPERSEDED", "REJECTED"}
NAVIGATION_COMMANDS = {"SkipNext", "SkipPrevious", "PlayQueueItem"}
NAVIGATION_ACTIONS = {"NEXT", "PREVIOUS", "PLAY_ITEM"}
QUEUE_EVENTS = {"playback.queue.cleared", "playback.queue.reconciled", "playback.queue.rebuilt", "playback.queue.patched"}
PLAYBACK_FAILURE_EVENTS = {"playback.dispatch.failed", "playback.player.failed", "playback.command.failed"}

SEVERITY_BY_NUMBER = {5: "DEBUG", 9: "INFO", 13: "WARN", 17: "ERROR"}
LOG_CATEGORIES = {"app", "room", "network", "discovery", "playback", "sync", "transfer", "storage", "security"}


@dataclass(frozen=True)
class PlaybackLogSummary:
    lines: int
    invalid_lines: int
    duration_seconds: float
    warning_events: int
    error_events: int
    current_item_changes: int
    max_current_item_changes_in_2s: int
    max_unattributed_current_item_changes_in_2s: int
    queue_sets: int
    queue_size_changes: int
    item_switches: int
    max_item_switches_in_2s: int
    seeks: int
    max_reported_late_ms: int
    unavailable_errors: int
    transition_circuit_breakers: int
    playback_failures: int
    unresolved_transport_commands: int
    max_transport_pending_seconds: float
    unresolved_preparation_requests: int
    max_preparation_pending_seconds: float
    repeated_current_item_navigation: int
    notification_updates_shed: int
    sync_samples: int
    sync_corrections: int
    max_abs_filtered_drift_ms: int
    hard_seek_events: int
    diagnostic_dropped_events: int

    @property
    def stable(self) -> bool:
        return not stability_failures(self)


def parse_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def parse_events(lines: Iterable[str]) -> tuple[list[dict[str, Any]], int, int]:
    events: list[dict[str, Any]] = []
    total = 0
    invalid = 0
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        total += 1
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            invalid += 1
            continue
        if not isinstance(event, dict) or event.get("schemaVersion") != 1:
            invalid += 1
            continue
        if parse_timestamp(event.get("timestamp")) is None or parse_timestamp(event.get("observedTimestamp")) is None:
            invalid += 1
            continue
        event_name = event.get("eventName")
        if not isinstance(event_name, str) or "." not in event_name:
            invalid += 1
            continue
        severity_number = event.get("severityNumber")
        if not isinstance(severity_number, int) or SEVERITY_BY_NUMBER.get(severity_number) != event.get("severityText"):
            invalid += 1
            continue
        resource = event.get("resource")
        scope = event.get("instrumentationScope")
        attributes = event.get("attributes")
        if not isinstance(resource, dict) or resource.get("service.name") != "unison":
            invalid += 1
            continue
        if not isinstance(scope, dict) or not isinstance(scope.get("name"), str) or not scope.get("name"):
            invalid += 1
            continue
        if not isinstance(attributes, dict) or attributes.get("log.category") not in LOG_CATEGORIES:
            invalid += 1
            continue
        events.append(event)
    return events, total, invalid


def attrs(event: dict[str, Any]) -> dict[str, Any]:
    value = event.get("attributes")
    return value if isinstance(value, dict) else {}


def max_events_in_window(events: list[datetime], seconds: float) -> int:
    window: deque[datetime] = deque()
    maximum = 0
    for event in sorted(events):
        window.append(event)
        while window and (event - window[0]).total_seconds() > seconds:
            window.popleft()
        maximum = max(maximum, len(window))
    return maximum


def analyze(lines: Iterable[str]) -> PlaybackLogSummary:
    events, line_count, invalid_lines = parse_events(lines)
    timestamps = [parse_timestamp(event["timestamp"]) for event in events]
    timestamps = [value for value in timestamps if value is not None]

    change_events: list[datetime] = []
    unattributed_change_events: list[datetime] = []
    navigation_events: deque[datetime] = deque()
    item_switch_events: list[datetime] = []
    queue_sizes: list[int] = []
    previous_item: str | None = None
    current_item: str | None = None
    seeks = 0
    max_late = 0
    unavailable_errors = 0
    transition_circuit_breakers = 0
    playback_failures = 0
    notification_updates_shed = 0
    warning_events = 0
    error_events = 0
    sync_samples = 0
    sync_corrections = 0
    max_abs_filtered_drift_ms = 0
    hard_seek_events = 0
    diagnostic_dropped_events = 0

    transport_started: dict[str, datetime] = {}
    transport_start_items: dict[str, str | None] = {}
    transport_pending_seconds: list[float] = []
    repeated_current_item_navigation = 0
    pending_preparations: dict[str, datetime] = {}
    preparation_pending_seconds: list[float] = []

    for event in events:
        timestamp = parse_timestamp(event["timestamp"])
        if timestamp is None:
            continue
        name = event["eventName"]
        values = attrs(event)
        severity_number = int(event.get("severityNumber") or 0)
        if 13 <= severity_number <= 16:
            warning_events += 1
        elif severity_number >= 17:
            error_events += 1

        if name == "room.command.received" and values.get("command.type") in NAVIGATION_COMMANDS:
            navigation_events.append(timestamp)

        if name == "room.canonical.applied" and values.get("mutation.type") == "CurrentItemChanged":
            change_events.append(timestamp)
            while navigation_events and (timestamp - navigation_events[0]).total_seconds() > 12.0:
                navigation_events.popleft()
            if navigation_events:
                navigation_events.popleft()
            else:
                unattributed_change_events.append(timestamp)

        if name in QUEUE_EVENTS:
            size = 0 if name == "playback.queue.cleared" else values.get("queue.size")
            if isinstance(size, int):
                queue_sizes.append(size)

        if name == "playback.state.changed":
            item = values.get("queue.item_id")
            item = item if isinstance(item, str) else None
            if previous_item is not None and item != previous_item:
                item_switch_events.append(timestamp)
            previous_item = item
            current_item = item

        if name == "playback.seek.applied":
            seeks += 1

        if name == "playback.command.executing":
            late = values.get("playback.late_ms")
            if isinstance(late, (int, float)):
                max_late = max(max_late, int(late))

        if name == "playback.request.failed" and "not ready" in str(event.get("body") or "").lower():
            unavailable_errors += 1
        if name == "playback.transition.circuit_breaker":
            transition_circuit_breakers += 1
        if name in PLAYBACK_FAILURE_EVENTS:
            playback_failures += 1
        if name == "playback.notification.shed":
            notification_updates_shed += 1

        if name == "room.transport.status":
            command_id = values.get("command.id")
            action = values.get("transport.action")
            phase = values.get("transport.phase")
            item = values.get("queue.item_id")
            message = str(values.get("transport.message") or "")
            if isinstance(command_id, str):
                if phase not in TERMINAL_PHASES:
                    transport_started.setdefault(command_id, timestamp)
                    transport_start_items.setdefault(command_id, current_item)
                else:
                    started = transport_started.pop(command_id, None)
                    starting_item = transport_start_items.pop(command_id, None)
                    if started is not None:
                        transport_pending_seconds.append((timestamp - started).total_seconds())
                    preparation_started = pending_preparations.pop(command_id, None)
                    if preparation_started is not None:
                        preparation_pending_seconds.append((timestamp - preparation_started).total_seconds())
                    if (
                        phase == "SETTLED"
                        and action in NAVIGATION_ACTIONS
                        and item is not None
                        and (item == starting_item or "already on" in message.lower() or message == "ALREADY_ALIGNED")
                    ):
                        repeated_current_item_navigation += 1

        if name == "playback.preparation.requested":
            command_id = values.get("command.id")
            key = command_id if isinstance(command_id, str) else f"sequence:{event.get('sequence')}"
            pending_preparations.setdefault(key, timestamp)

        if name == "playback.transition.prepared":
            command_id = values.get("command.id")
            if isinstance(command_id, str):
                started = pending_preparations.pop(command_id, None)
                if started is not None:
                    preparation_pending_seconds.append((timestamp - started).total_seconds())

        if name in {"sync.sample", "sync.buffering", "sync.speed_adjustment", "sync.hard_seek"}:
            sync_samples += 1
            if name in {"sync.speed_adjustment", "sync.hard_seek"}:
                sync_corrections += 1
            drift = values.get("sync.filtered_drift_ms")
            if isinstance(drift, (int, float)):
                max_abs_filtered_drift_ms = max(max_abs_filtered_drift_ms, abs(int(drift)))
            if values.get("sync.action") == "SEEK":
                hard_seek_events += 1

        if name == "room.session.ended":
            dropped = values.get("log.dropped_count")
            if isinstance(dropped, int):
                diagnostic_dropped_events = max(diagnostic_dropped_events, dropped)

    if timestamps:
        end = max(timestamps)
        transport_pending_seconds.extend((end - started).total_seconds() for started in transport_started.values())
        preparation_pending_seconds.extend((end - started).total_seconds() for started in pending_preparations.values())

    return PlaybackLogSummary(
        lines=line_count,
        invalid_lines=invalid_lines,
        duration_seconds=round((max(timestamps) - min(timestamps)).total_seconds(), 3) if len(timestamps) >= 2 else 0.0,
        warning_events=warning_events,
        error_events=error_events,
        current_item_changes=len(change_events),
        max_current_item_changes_in_2s=max_events_in_window(change_events, 2.0),
        max_unattributed_current_item_changes_in_2s=max_events_in_window(unattributed_change_events, 2.0),
        queue_sets=len(queue_sizes),
        queue_size_changes=sum(a != b for a, b in zip(queue_sizes, queue_sizes[1:])),
        item_switches=len(item_switch_events),
        max_item_switches_in_2s=max_events_in_window(item_switch_events, 2.0),
        seeks=seeks,
        max_reported_late_ms=max_late,
        unavailable_errors=unavailable_errors,
        transition_circuit_breakers=transition_circuit_breakers,
        playback_failures=playback_failures,
        unresolved_transport_commands=len(transport_started),
        max_transport_pending_seconds=round(max(transport_pending_seconds, default=0.0), 3),
        unresolved_preparation_requests=len(pending_preparations),
        max_preparation_pending_seconds=round(max(preparation_pending_seconds, default=0.0), 3),
        repeated_current_item_navigation=repeated_current_item_navigation,
        notification_updates_shed=notification_updates_shed,
        sync_samples=sync_samples,
        sync_corrections=sync_corrections,
        max_abs_filtered_drift_ms=max_abs_filtered_drift_ms,
        hard_seek_events=hard_seek_events,
        diagnostic_dropped_events=diagnostic_dropped_events,
    )


def stability_failures(summary: PlaybackLogSummary) -> list[str]:
    failures: list[str] = []
    if summary.invalid_lines > 0:
        failures.append(f"malformed/unrecognized diagnostic records ({summary.invalid_lines})")
    if summary.max_unattributed_current_item_changes_in_2s > 3:
        failures.append(
            "unattributed current-item storm "
            f"({summary.max_unattributed_current_item_changes_in_2s} changes/2s)"
        )
    if summary.max_item_switches_in_2s > 10:
        failures.append(f"local item-switch storm ({summary.max_item_switches_in_2s} switches/2s)")
    if summary.unavailable_errors > 0:
        failures.append(f"unavailable-track errors ({summary.unavailable_errors})")
    if summary.transition_circuit_breakers > 0:
        failures.append(f"automatic transition circuit breakers ({summary.transition_circuit_breakers})")
    if summary.playback_failures > 0:
        failures.append(f"playback failures ({summary.playback_failures})")
    if summary.unresolved_transport_commands > 0:
        failures.append(f"unresolved transport commands ({summary.unresolved_transport_commands})")
    if summary.max_transport_pending_seconds > 10.5:
        failures.append(f"transport command pending too long ({summary.max_transport_pending_seconds:.1f}s)")
    if summary.unresolved_preparation_requests > 0:
        failures.append(f"unresolved preparation requests ({summary.unresolved_preparation_requests})")
    if summary.max_preparation_pending_seconds > 10.5:
        failures.append(f"preparation pending too long ({summary.max_preparation_pending_seconds:.1f}s)")
    if summary.repeated_current_item_navigation > 0:
        failures.append(f"navigation settled on current item ({summary.repeated_current_item_navigation})")
    if summary.notification_updates_shed > 0:
        failures.append(f"notification updates shed ({summary.notification_updates_shed})")
    if summary.diagnostic_dropped_events > 0:
        failures.append(f"diagnostic events dropped ({summary.diagnostic_dropped_events})")
    return failures


def event(ts: str, name: str, severity: int = 9, **attributes: Any) -> str:
    return json.dumps(
        {
            "schemaVersion": 1,
            "sequence": 1,
            "timestamp": ts,
            "observedTimestamp": ts,
            "monotonicTimeNs": 1,
            "severityText": "ERROR" if severity >= 17 else ("WARN" if severity >= 13 else "INFO"),
            "severityNumber": severity,
            "eventName": name,
            "body": None,
            "resource": {"service.name": "unison"},
            "instrumentationScope": {"name": "test"},
            "attributes": {"log.category": "playback"} | attributes,
        },
        separators=(",", ":"),
    ) + "\n"


def self_test() -> None:
    stable = analyze(
        [
            event("2026-01-01T10:00:00Z", "room.command.received", **{"command.type": "SkipNext"}),
            event("2026-01-01T10:00:00.050Z", "room.canonical.applied", **{"mutation.type": "CurrentItemChanged"}),
            event("2026-01-01T10:00:00.100Z", "playback.state.changed", **{"queue.item_id": "a"}),
            event("2026-01-01T10:00:01Z", "room.transport.status", **{"command.id": "abc", "transport.action": "NEXT", "transport.phase": "ACCEPTED", "queue.item_id": "b"}),
            event("2026-01-01T10:00:02Z", "room.transport.status", **{"command.id": "abc", "transport.action": "NEXT", "transport.phase": "SETTLED", "queue.item_id": "b"}),
            event("2026-01-01T10:00:02.100Z", "sync.sample", **{"sync.filtered_drift_ms": 18, "sync.action": "HOLD"}),
        ]
    )
    assert stable.stable, stable

    unstable = analyze(
        [event(f"2026-01-01T10:00:00.{index}00Z", "room.canonical.applied", **{"mutation.type": "CurrentItemChanged"}) for index in range(6)]
        + [
            event("2026-01-01T10:00:01Z", "playback.dispatch.failed", severity=17),
            event("2026-01-01T10:00:01.100Z", "room.transport.status", **{"command.id": "stuck", "transport.action": "NEXT", "transport.phase": "ACCEPTED", "queue.item_id": "a"}),
        ]
    )
    assert not unstable.stable, unstable

    malformed = analyze(["not-json\n"])
    assert malformed.invalid_lines == 1 and not malformed.stable, malformed

    wrong_severity = event("2026-01-01T10:00:00Z", "playback.state.changed").replace(
        '"severityText":"INFO"', '"severityText":"WARN"'
    )
    malformed_schema = analyze([wrong_severity])
    assert malformed_schema.invalid_lines == 1 and not malformed_schema.stable, malformed_schema
    print("PLAYBACK_LOG_ANALYZER_SELF_TEST_OK")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", nargs="?", type=Path)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.log is None:
        parser.error("log is required unless --self-test is used")
    summary = analyze(args.log.read_text(errors="replace").splitlines(keepends=True))
    if args.json:
        print(json.dumps(asdict(summary) | {"stable": summary.stable}, indent=2))
    else:
        for key, value in asdict(summary).items():
            print(f"{key}: {value}")
        print(f"stable: {summary.stable}")
    failures = stability_failures(summary)
    if args.strict and failures:
        print("Stability gate failed: " + "; ".join(failures), file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
