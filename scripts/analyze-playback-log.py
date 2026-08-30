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
SYNC_EVENTS = {"sync.sample", "sync.buffering", "sync.speed_adjustment", "sync.hard_seek"}
REJOIN_TERMINAL_EVENTS = {"playback.rejoin.completed", "playback.rejoin.cancelled", "playback.rejoin.cleared"}
AUTO_REJOIN_STUCK_SECONDS = 10.5
EMPTY_READINESS_STUCK_SECONDS = 5.0

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
    unavailable_command_rejections: int
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
    natural_end_events: int
    boundary_events: int
    boundary_duplicate_observations: int
    missing_natural_boundaries: int
    wrong_play_state_repairs_before_boundary: int
    system_policy_inhibitions: int
    max_empty_readiness_cohort_seconds: float
    max_empty_readiness_cohort_samples: int
    auto_rejoin_recoveries: int
    stuck_auto_rejoins: int
    max_auto_rejoin_recovery_seconds: float
    unsafe_unlocked_clock_projections: int

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


def unavailable_reason(event: dict[str, Any], values: dict[str, Any]) -> bool:
    text = " ".join(
        str(value or "")
        for value in (
            event.get("body"),
            values.get("reason"),
            values.get("transport.message"),
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
    unavailable_rejection_keys: set[str] = set()
    legacy_unavailable_rejections: list[datetime] = []
    transport_unavailable_rejections: list[datetime] = []
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

    natural_end_events = 0
    boundary_events = 0
    boundary_duplicate_observations = 0
    awaiting_boundary = False
    wrong_play_state_repairs_before_boundary = 0
    system_policy_inhibitions = 0
    unsafe_unlocked_clock_projections = 0

    empty_readiness_start: datetime | None = None
    empty_readiness_last: datetime | None = None
    empty_readiness_samples = 0
    max_empty_readiness_seconds = 0.0
    max_empty_readiness_samples = 0

    auto_rejoin_pending = False
    auto_rejoin_recoverable_since: datetime | None = None
    auto_rejoin_durations: list[float] = []
    auto_rejoin_recoveries = 0

    transport_started: dict[str, datetime] = {}
    transport_start_items: dict[str, str | None] = {}
    transport_pending_seconds: list[float] = []
    repeated_current_item_navigation = 0
    pending_preparations: dict[str, datetime] = {}
    preparation_pending_seconds: list[float] = []

    def resolve_auto_rejoin(timestamp: datetime, *, successful: bool, cancelled: bool = False) -> None:
        nonlocal auto_rejoin_pending, auto_rejoin_recoverable_since, auto_rejoin_recoveries
        if not auto_rejoin_pending and auto_rejoin_recoverable_since is None:
            return
        if auto_rejoin_recoverable_since is not None and not cancelled:
            auto_rejoin_durations.append(max(0.0, (timestamp - auto_rejoin_recoverable_since).total_seconds()))
        if successful:
            auto_rejoin_recoveries += 1
        auto_rejoin_pending = False
        auto_rejoin_recoverable_since = None

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
            awaiting_boundary = False
        elif name == "room.canonical.applied" and values.get("mutation.type") == "PauseScheduled":
            awaiting_boundary = False

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

        if name == "playback.request.failed" and unavailable_reason(event, values):
            unavailable_errors += 1
        if name == "room.command.rejected" and unavailable_reason(event, values):
            legacy_unavailable_rejections.append(timestamp)
        if name == "playback.command.rejected" and unavailable_reason(event, values):
            key = values.get("command.id")
            unavailable_rejection_keys.add(str(key) if key else f"sequence:{event.get('sequence')}:{name}")
        if (
            name == "room.transport.status"
            and values.get("transport.phase") == "REJECTED"
            and unavailable_reason(event, values)
        ):
            key = values.get("command.id")
            unavailable_rejection_keys.add(str(key) if key else f"sequence:{event.get('sequence')}:{name}")
            transport_unavailable_rejections.append(timestamp)

        if name == "playback.transition.circuit_breaker":
            transition_circuit_breakers += 1
        if name in PLAYBACK_FAILURE_EVENTS:
            playback_failures += 1
        if name == "playback.notification.shed":
            notification_updates_shed += 1

        if name == "playback.media3.play_when_ready.changed" and values.get("media3.play_when_ready_reason_name") == "END_OF_MEDIA_ITEM":
            natural_end_events += 1
            awaiting_boundary = True
        elif name == "playback.item.boundary":
            boundary_events += 1
            awaiting_boundary = False
        elif name == "playback.item.boundary_duplicate_ignored":
            boundary_duplicate_observations += 1
            awaiting_boundary = False
        elif name == "playback.successor.pending":
            awaiting_boundary = False

        if (
            name in {"sync.peer.playback_repair", "sync.local.playback_repaired"}
            and values.get("sync.reason") == "WRONG_PLAY_STATE"
            and awaiting_boundary
        ):
            wrong_play_state_repairs_before_boundary += 1

        if (
            name == "playback.participation.changed"
            and values.get("playback.participation_to") == "OUTPUT_INHIBITED"
            and values.get("playback.inhibition_reason") == "SYSTEM_POLICY"
        ):
            system_policy_inhibitions += 1

        if name == "playback.preparation.status":
            connected = values.get("room.connected_members")
            readiness = values.get("playback.readiness_members")
            if not isinstance(readiness, int):
                # Pre-Phase-2 traces used playback.cohort_members for both audible participation and
                # content readiness. Falling back here lets the regression analyzer recognize the
                # historical empty-cohort deadlock without misreading new split-cohort traces.
                readiness = values.get("playback.cohort_members")
            empty = isinstance(connected, int) and connected > 0 and isinstance(readiness, int) and readiness == 0
            if empty:
                if empty_readiness_start is None:
                    empty_readiness_start = timestamp
                    empty_readiness_samples = 1
                else:
                    empty_readiness_samples += 1
                empty_readiness_last = timestamp
                max_empty_readiness_samples = max(max_empty_readiness_samples, empty_readiness_samples)
                if empty_readiness_start is not None and empty_readiness_last is not None:
                    max_empty_readiness_seconds = max(
                        max_empty_readiness_seconds,
                        (empty_readiness_last - empty_readiness_start).total_seconds(),
                    )
            else:
                empty_readiness_start = None
                empty_readiness_last = None
                empty_readiness_samples = 0

        if name == "playback.rejoin.pending" and values.get("playback.rejoin_reason") == "AUTO_AUDIO_FOCUS":
            auto_rejoin_pending = True
        if (
            name == "playback.participation.changed"
            and values.get("playback.participation_to") == "OUTPUT_INHIBITED"
            and values.get("playback.inhibition_reason") == "AUDIO_FOCUS"
        ):
            # Legacy traces predate playback.rejoin.pending; infer the automatic rejoin intent from
            # the explicit transient-focus inhibition so the real incident remains a fixture.
            auto_rejoin_pending = True
        if auto_rejoin_pending and (
            (
                name == "playback.output.suppression_cleared"
                and values.get("playback.inhibition_reason") in {None, "AUDIO_FOCUS"}
            )
            or (
                name == "playback.media3.suppression.changed"
                and (
                    values.get("media3.playback_suppression_name") == "NONE"
                    or values.get("media3.playback_suppression_to") == 0
                )
            )
        ):
            if auto_rejoin_recoverable_since is None:
                auto_rejoin_recoverable_since = timestamp

        if name in REJOIN_TERMINAL_EVENTS:
            reason = values.get("playback.rejoin_reason")
            if reason == "AUTO_AUDIO_FOCUS":
                resolve_auto_rejoin(
                    timestamp,
                    successful=name in {"playback.rejoin.completed", "playback.rejoin.cleared"},
                    cancelled=name == "playback.rejoin.cancelled",
                )
        if name == "playback.participation.changed" and values.get("playback.participation_to") == "ACTIVE":
            resolve_auto_rejoin(timestamp, successful=True)

        if name in SYNC_EVENTS:
            sync_samples += 1
            if name in {"sync.speed_adjustment", "sync.hard_seek"}:
                sync_corrections += 1
            drift = values.get("sync.filtered_drift_ms")
            if isinstance(drift, (int, float)):
                max_abs_filtered_drift_ms = max(max_abs_filtered_drift_ms, abs(int(drift)))
            if values.get("sync.action") == "SEEK":
                hard_seek_events += 1
            if (
                values.get("room.role") == "participant"
                and values.get("clock.state") not in {None, "LOCKED"}
                and isinstance(values.get("playback.canonical_position_ms"), (int, float))
            ):
                unsafe_unlocked_clock_projections += 1

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

        if name == "room.session.ended":
            dropped = values.get("log.dropped_count")
            if isinstance(dropped, int):
                diagnostic_dropped_events = max(diagnostic_dropped_events, dropped)

    if timestamps:
        end = max(timestamps)
        transport_pending_seconds.extend((end - started).total_seconds() for started in transport_started.values())
        preparation_pending_seconds.extend((end - started).total_seconds() for started in pending_preparations.values())
        if auto_rejoin_recoverable_since is not None:
            auto_rejoin_durations.append(max(0.0, (end - auto_rejoin_recoverable_since).total_seconds()))

    # Older coordinator traces emitted both room.command.rejected and a transport REJECTED status
    # for the same user action. Count that action once; retain a legacy room rejection only when no
    # transport rejection followed immediately.
    for rejected_at in legacy_unavailable_rejections:
        if not any(
            0.0 <= (transport_at - rejected_at).total_seconds() <= 0.250
            for transport_at in transport_unavailable_rejections
        ):
            unavailable_rejection_keys.add(f"legacy:{rejected_at.isoformat()}")

    missing_natural_boundaries = max(
        0,
        natural_end_events - boundary_events - boundary_duplicate_observations,
    )
    stuck_auto_rejoins = sum(duration > AUTO_REJOIN_STUCK_SECONDS for duration in auto_rejoin_durations)

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
        unavailable_command_rejections=len(unavailable_rejection_keys),
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
        natural_end_events=natural_end_events,
        boundary_events=boundary_events,
        boundary_duplicate_observations=boundary_duplicate_observations,
        missing_natural_boundaries=missing_natural_boundaries,
        wrong_play_state_repairs_before_boundary=wrong_play_state_repairs_before_boundary,
        system_policy_inhibitions=system_policy_inhibitions,
        max_empty_readiness_cohort_seconds=round(max_empty_readiness_seconds, 3),
        max_empty_readiness_cohort_samples=max_empty_readiness_samples,
        auto_rejoin_recoveries=auto_rejoin_recoveries,
        stuck_auto_rejoins=stuck_auto_rejoins,
        max_auto_rejoin_recovery_seconds=round(max(auto_rejoin_durations, default=0.0), 3),
        unsafe_unlocked_clock_projections=unsafe_unlocked_clock_projections,
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
    if summary.unavailable_command_rejections > 0:
        failures.append(f"unavailable-media command rejections ({summary.unavailable_command_rejections})")
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
    if summary.missing_natural_boundaries > 0:
        failures.append(f"natural-end observations missing physical boundary handoff ({summary.missing_natural_boundaries})")
    if summary.wrong_play_state_repairs_before_boundary > 0:
        failures.append(
            "wrong-play-state repair before natural boundary handoff "
            f"({summary.wrong_play_state_repairs_before_boundary})"
        )
    if summary.system_policy_inhibitions > 0:
        failures.append(f"generic SYSTEM_POLICY output inhibition ({summary.system_policy_inhibitions})")
    if (
        summary.max_empty_readiness_cohort_samples >= 3
        and summary.max_empty_readiness_cohort_seconds > EMPTY_READINESS_STUCK_SECONDS
    ):
        failures.append(
            "connected room had empty content-readiness cohort for "
            f"{summary.max_empty_readiness_cohort_seconds:.1f}s"
        )
    if summary.stuck_auto_rejoins > 0:
        failures.append(
            "automatic audio-focus rejoin remained recoverable but incomplete "
            f"({summary.max_auto_rejoin_recovery_seconds:.1f}s)"
        )
    if summary.unsafe_unlocked_clock_projections > 0:
        failures.append(
            "participant projected canonical position with unlocked room clock "
            f"({summary.unsafe_unlocked_clock_projections})"
        )
    return failures


def event(ts: str, name: str, severity: int = 9, category: str = "playback", **attributes: Any) -> str:
    return json.dumps(
        {
            "schemaVersion": 1,
            "sequence": 1,
            "timestamp": ts,
            "observedTimestamp": ts,
            "monotonicTimeNs": 1,
            "severityText": "ERROR" if severity >= 17 else ("WARN" if severity >= 13 else ("DEBUG" if severity <= 5 else "INFO")),
            "severityNumber": severity,
            "eventName": name,
            "body": None,
            "resource": {"service.name": "unison"},
            "instrumentationScope": {"name": "test"},
            "attributes": {"log.category": category} | attributes,
        },
        separators=(",", ":"),
    ) + "\n"


def self_test() -> None:
    stable = analyze(
        [
            event("2026-01-01T10:00:00Z", "room.command.received", category="room", **{"command.type": "SkipNext"}),
            event("2026-01-01T10:00:00.050Z", "room.canonical.applied", category="room", **{"mutation.type": "CurrentItemChanged"}),
            event("2026-01-01T10:00:00.100Z", "playback.state.changed", **{"queue.item_id": "a"}),
            event("2026-01-01T10:00:01Z", "room.transport.status", category="room", **{"command.id": "abc", "transport.action": "NEXT", "transport.phase": "ACCEPTED", "queue.item_id": "b"}),
            event("2026-01-01T10:00:02Z", "room.transport.status", category="room", **{"command.id": "abc", "transport.action": "NEXT", "transport.phase": "SETTLED", "queue.item_id": "b"}),
            event("2026-01-01T10:00:02.100Z", "sync.sample", category="sync", **{"sync.filtered_drift_ms": 18, "sync.action": "HOLD", "clock.state": "LOCKED", "room.role": "participant"}),
            event("2026-01-01T10:00:03Z", "playback.media3.play_when_ready.changed", **{"media3.play_when_ready_reason_name": "END_OF_MEDIA_ITEM"}),
            event("2026-01-01T10:00:03.001Z", "playback.item.boundary", **{"queue.item_id": "a", "playback.boundary_revision": 1}),
            event("2026-01-01T10:00:03.010Z", "playback.successor.pending", category="room", **{"queue.item_id": "b"}),
            event("2026-01-01T10:00:04Z", "playback.preparation.status", category="room", **{"room.connected_members": 2, "playback.readiness_members": 2, "playback.cohort_members": 1}),
            event("2026-01-01T10:00:05Z", "playback.rejoin.pending", category="room", **{"playback.rejoin_reason": "AUTO_AUDIO_FOCUS"}),
            event("2026-01-01T10:00:06Z", "playback.output.suppression_cleared", **{"playback.inhibition_reason": "AUDIO_FOCUS"}),
            event("2026-01-01T10:00:07Z", "playback.rejoin.completed", category="room", **{"playback.rejoin_reason": "AUTO_AUDIO_FOCUS"}),
        ]
    )
    assert stable.stable, stable

    boundary_failure = analyze(
        [
            event("2026-01-01T10:00:00Z", "playback.state.changed", **{"queue.item_id": "a"}),
            event("2026-01-01T10:00:01Z", "playback.media3.play_when_ready.changed", **{"media3.play_when_ready_reason_name": "END_OF_MEDIA_ITEM"}),
            event("2026-01-01T10:00:01.100Z", "sync.local.playback_repaired", category="sync", **{"sync.reason": "WRONG_PLAY_STATE"}),
        ]
    )
    assert boundary_failure.missing_natural_boundaries == 1 and not boundary_failure.stable, boundary_failure
    assert boundary_failure.wrong_play_state_repairs_before_boundary == 1, boundary_failure

    readiness_failure = analyze(
        [
            event("2026-01-01T10:00:00Z", "playback.preparation.status", category="room", **{"room.connected_members": 2, "playback.readiness_members": 0}),
            event("2026-01-01T10:00:03Z", "playback.preparation.status", category="room", **{"room.connected_members": 2, "playback.readiness_members": 0}),
            event("2026-01-01T10:00:07Z", "playback.preparation.status", category="room", **{"room.connected_members": 2, "playback.readiness_members": 0}),
        ]
    )
    assert not readiness_failure.stable, readiness_failure

    rejoin_failure = analyze(
        [
            event("2026-01-01T10:00:00Z", "playback.participation.changed", category="room", **{"playback.participation_to": "OUTPUT_INHIBITED", "playback.inhibition_reason": "AUDIO_FOCUS"}),
            event("2026-01-01T10:00:01Z", "playback.output.suppression_cleared", **{"playback.inhibition_reason": "AUDIO_FOCUS"}),
            event("2026-01-01T10:00:20Z", "sync.sample", category="sync", **{"clock.state": "LOCKED", "room.role": "participant"}),
        ]
    )
    assert rejoin_failure.stuck_auto_rejoins == 1 and not rejoin_failure.stable, rejoin_failure

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
