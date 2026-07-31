#!/usr/bin/env python3
"""Summarize Unison playback logs and enforce stability gates for device/soak runs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import deque
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

TIMESTAMP = re.compile(r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})")
CURRENT_CHANGE = re.compile(r"Apply CurrentItemChanged sequence=(\d+)")
NAVIGATION_COMMAND = re.compile(r"App command (?:SkipNext|SkipPrevious|PlayQueueItem)")
QUEUE_SET = re.compile(
    r"(?:Set queue items=(?P<set_size>\d+)|Patch queue from=\d+ to=(?P<patch_size>\d+)) "
    r"current=(?P<item>[A-Za-z0-9_-]+|null)"
)
PREPARATION_REQUEST = re.compile(r"Apply QueueItemPreparationRequested sequence=(\d+)")
PREPARATION_REQUEST_DETAIL = re.compile(
    r"Preparation request command=(?P<id>[A-Za-z0-9_-]+|none) "
    r"item=(?P<item>[A-Za-z0-9_-]+) sequence=(?P<sequence>\d+)"
)
PREPARATION_COMPLETED = re.compile(r"Prepared pending transition(?: command=(?P<id>[A-Za-z0-9_-]+))?")
SCHEDULED_SEEK_ITEM = re.compile(r"Schedule seek item=([A-Za-z0-9_-]+)")
SEEK = re.compile(r"(?:Execute seek lateMs=(\d+)|Seek item=([A-Za-z0-9_-]+))")
ITEM_EVENT = re.compile(
    r"(?:Set queue items=\d+ current=|Patch queue from=\d+ to=\d+ current=|Seek item=|State item=)"
    r"([A-Za-z0-9_-]+|null)"
)
TRANSPORT_STATUS = re.compile(
    r"Transport command id=(?P<id>[A-Za-z0-9_-]+) action=(?P<action>[A-Z_]+) "
    r"phase=(?P<phase>[A-Z_]+) item=(?P<item>[A-Za-z0-9_-]+|none)(?: message=(?P<message>.*))?"
)
TERMINAL_PHASES = {"SETTLED", "SUPERSEDED", "REJECTED"}


@dataclass(frozen=True)
class PlaybackLogSummary:
    lines: int
    duration_seconds: float
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

    @property
    def stable(self) -> bool:
        return not stability_failures(self)


def parse_timestamp(line: str) -> datetime | None:
    match = TIMESTAMP.match(line)
    return datetime.strptime(match.group("ts"), "%Y-%m-%d %H:%M:%S.%f") if match else None


def max_events_in_window(events: list[datetime], seconds: float) -> int:
    window: deque[datetime] = deque()
    maximum = 0
    for event in events:
        window.append(event)
        while window and (event - window[0]).total_seconds() > seconds:
            window.popleft()
        maximum = max(maximum, len(window))
    return maximum


def analyze(lines: Iterable[str]) -> PlaybackLogSummary:
    line_list = list(lines)
    timestamps = [timestamp for line in line_list if (timestamp := parse_timestamp(line)) is not None]
    change_events: list[datetime] = []
    unattributed_change_events: list[datetime] = []
    pending_navigation_commands: deque[datetime] = deque()
    item_switch_events: list[datetime] = []
    queue_sizes: list[int] = []
    previous_item: str | None = None
    seeks = 0
    max_late = 0
    transport_started: dict[str, datetime] = {}
    transport_start_items: dict[str, str | None] = {}
    transport_pending_seconds: list[float] = []
    repeated_current_item_navigation = 0
    current_item: str | None = None
    pending_unstructured_navigation: deque[tuple[datetime, str | None]] = deque()
    pending_preparations: deque[tuple[datetime, str | None]] = deque()
    latest_preparation_command_id: str | None = None
    preparation_pending_seconds: list[float] = []

    for line in line_list:
        timestamp = parse_timestamp(line)
        if NAVIGATION_COMMAND.search(line) and timestamp is not None:
            pending_navigation_commands.append(timestamp)
            pending_unstructured_navigation.append((timestamp, current_item))
        if PREPARATION_REQUEST.search(line) and timestamp is not None:
            pending_preparations.append((timestamp, latest_preparation_command_id))
        if match := PREPARATION_REQUEST_DETAIL.search(line):
            command_id = match.group("id")
            if command_id != "none":
                for index in range(len(pending_preparations) - 1, -1, -1):
                    started, pending_command_id = pending_preparations[index]
                    if pending_command_id is None:
                        pending_preparations[index] = (started, command_id)
                        break
        if match := PREPARATION_COMPLETED.search(line):
            if timestamp is not None and pending_preparations:
                completed_id = match.group("id")
                matching_index = next(
                    (index for index, (_, command_id) in enumerate(pending_preparations) if completed_id is None or command_id == completed_id),
                    None,
                )
                if matching_index is not None:
                    started, _ = pending_preparations[matching_index]
                    del pending_preparations[matching_index]
                    preparation_pending_seconds.append((timestamp - started).total_seconds())
        if CURRENT_CHANGE.search(line) and timestamp is not None:
            change_events.append(timestamp)
            while pending_navigation_commands and (timestamp - pending_navigation_commands[0]).total_seconds() > 12.0:
                pending_navigation_commands.popleft()
            if pending_navigation_commands:
                pending_navigation_commands.popleft()
            else:
                unattributed_change_events.append(timestamp)
        if match := QUEUE_SET.search(line):
            queue_sizes.append(int(match.group("set_size") or match.group("patch_size")))
        if match := SEEK.search(line):
            if match.group(1) is not None:
                seeks += 1
                max_late = max(max_late, int(match.group(1)))
        if match := SCHEDULED_SEEK_ITEM.search(line):
            while pending_unstructured_navigation and timestamp is not None and (timestamp - pending_unstructured_navigation[0][0]).total_seconds() > 3.0:
                pending_unstructured_navigation.popleft()
            if pending_unstructured_navigation:
                _, base_item = pending_unstructured_navigation.popleft()
                if base_item is not None and match.group(1) == base_item:
                    repeated_current_item_navigation += 1
        if match := ITEM_EVENT.search(line):
            item = match.group(1)
            if previous_item is not None and item != previous_item and timestamp is not None:
                item_switch_events.append(timestamp)
            previous_item = item
            if "State item=" in line and item != "null":
                current_item = item
        if match := TRANSPORT_STATUS.search(line):
            command_id = match.group("id")
            phase = match.group("phase")
            action = match.group("action")
            item = match.group("item")
            message = match.group("message") or ""
            if timestamp is not None and phase not in TERMINAL_PHASES:
                if command_id not in transport_started:
                    transport_started[command_id] = timestamp
                    transport_start_items[command_id] = current_item
                if action in {"NEXT", "PREVIOUS", "PLAY_ITEM"} and phase in {"SUBMITTED", "ACCEPTED"}:
                    latest_preparation_command_id = command_id
            if phase in TERMINAL_PHASES:
                started = transport_started.pop(command_id, None)
                starting_item = transport_start_items.pop(command_id, None)
                matching_preparation = next(
                    (index for index, (_, pending_command_id) in enumerate(pending_preparations) if pending_command_id == command_id),
                    None,
                )
                if matching_preparation is not None:
                    preparation_started, _ = pending_preparations[matching_preparation]
                    del pending_preparations[matching_preparation]
                    if timestamp is not None:
                        preparation_pending_seconds.append((timestamp - preparation_started).total_seconds())
                if latest_preparation_command_id == command_id:
                    latest_preparation_command_id = None
                if started is not None and timestamp is not None:
                    transport_pending_seconds.append((timestamp - started).total_seconds())
                if (
                    phase == "SETTLED"
                    and action in {"NEXT", "PREVIOUS"}
                    and item != "none"
                    and (item == starting_item or "Already on that song" in message)
                ):
                    repeated_current_item_navigation += 1

    if timestamps:
        end = timestamps[-1]
        transport_pending_seconds.extend((end - started).total_seconds() for started in transport_started.values())
        preparation_pending_seconds.extend((end - started).total_seconds() for started, _ in pending_preparations)

    return PlaybackLogSummary(
        lines=len(line_list),
        duration_seconds=round((timestamps[-1] - timestamps[0]).total_seconds(), 3) if len(timestamps) >= 2 else 0.0,
        current_item_changes=len(change_events),
        max_current_item_changes_in_2s=max_events_in_window(change_events, 2.0),
        max_unattributed_current_item_changes_in_2s=max_events_in_window(unattributed_change_events, 2.0),
        queue_sets=len(queue_sizes),
        queue_size_changes=sum(a != b for a, b in zip(queue_sizes, queue_sizes[1:])),
        item_switches=len(item_switch_events),
        max_item_switches_in_2s=max_events_in_window(item_switch_events, 2.0),
        seeks=seeks,
        max_reported_late_ms=max_late,
        unavailable_errors=sum("This song is not ready yet" in line for line in line_list),
        transition_circuit_breakers=sum("circuit breaker tripped" in line for line in line_list),
        playback_failures=sum("Canonical playback work failed" in line for line in line_list),
        unresolved_transport_commands=len(transport_started),
        max_transport_pending_seconds=round(max(transport_pending_seconds, default=0.0), 3),
        unresolved_preparation_requests=len(pending_preparations),
        max_preparation_pending_seconds=round(max(preparation_pending_seconds, default=0.0), 3),
        repeated_current_item_navigation=repeated_current_item_navigation,
        notification_updates_shed=sum("NotificationService" in line and "Shedding" in line for line in line_list),
    )


def stability_failures(summary: PlaybackLogSummary) -> list[str]:
    failures: list[str] = []
    if summary.max_unattributed_current_item_changes_in_2s > 3:
        failures.append(
            "unattributed current-item storm "
            f"({summary.max_unattributed_current_item_changes_in_2s} changes/2s)"
        )
    if summary.max_item_switches_in_2s > 10:
        failures.append(f"local item-switch storm ({summary.max_item_switches_in_2s} switches/2s)")
    if summary.unavailable_errors > 0:
        failures.append(f"unavailable-track errors ({summary.unavailable_errors})")
    if summary.playback_failures > 0:
        failures.append(f"playback dispatcher failures ({summary.playback_failures})")
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
    return failures


def self_test() -> None:
    stable = analyze(
        [
            "2026-01-01 10:00:00.000 RoomRuntime I Apply CurrentItemChanged sequence=1\n",
            "2026-01-01 10:00:00.100 UnisonPlayback I State item=a playback=READY\n",
            "2026-01-01 10:03:00.000 RoomRuntime I Apply CurrentItemChanged sequence=2\n",
            "2026-01-01 10:03:00.100 UnisonPlayback I State item=b playback=READY\n",
            "2026-01-01 10:03:01.000 RoomRuntime I Transport command id=abc action=NEXT phase=ACCEPTED item=c message=Preparing\n",
            "2026-01-01 10:03:02.000 RoomRuntime I Transport command id=abc action=NEXT phase=SETTLED item=c\n",
        ]
    )
    assert stable.stable, stable
    rapid_user_navigation = analyze(
        [
            item
            for index in range(6)
            for item in (
                f"2026-01-01 10:00:00.{index * 120:03d} RoomRuntime I App command SkipNext\n",
                f"2026-01-01 10:00:00.{index * 120 + 40:03d} RoomRuntime I Apply CurrentItemChanged sequence={index}\n",
            )
        ]
    )
    assert rapid_user_navigation.stable, rapid_user_navigation
    correlated_preparation = analyze(
        [
            "2026-01-01 10:00:00.000 RoomRuntime I Transport command id=abc action=NEXT phase=ACCEPTED item=b message=Preparing\n",
            "2026-01-01 10:00:00.001 RoomRuntime I Apply QueueItemPreparationRequested sequence=1\n",
            "2026-01-01 10:00:00.002 RoomRuntime I Preparation request command=abc item=b sequence=1\n",
            "2026-01-01 10:00:03.000 RoomRuntime I Transport command id=abc action=NEXT phase=SUPERSEDED item=b message=Replaced\n",
        ]
    )
    assert correlated_preparation.unresolved_preparation_requests == 0, correlated_preparation
    assert correlated_preparation.max_preparation_pending_seconds == 2.999, correlated_preparation
    unstable = analyze(
        [
            f"2026-01-01 10:00:00.{index * 100:03d} RoomRuntime I Apply CurrentItemChanged sequence={index}\n"
            for index in range(6)
        ]
        + [
            "2026-01-01 10:00:01.000 UnisonPlayback E This song is not ready yet\n",
            "2026-01-01 10:00:01.100 RoomRuntime I Transport command id=stuck action=NEXT phase=ACCEPTED item=a message=Preparing\n",
            "2026-01-01 10:00:20.000 NotificationService E Shedding package=test\n",
        ]
    )
    assert not unstable.stable, unstable
    unstructured_navigation_failure = analyze(
        [
            "2026-01-01 10:00:00.000 UnisonPlayback I State item=a playback=READY\n",
            "2026-01-01 10:00:00.100 RoomRuntime I App command SkipNext\n",
            "2026-01-01 10:00:00.200 RoomRuntime I Apply QueueItemPreparationRequested sequence=1\n",
            "2026-01-01 10:00:01.000 RoomRuntime I App command SkipNext\n",
            "2026-01-01 10:00:01.100 UnisonScheduler I Schedule seek item=a positionMs=0\n",
            "2026-01-01 10:00:20.000 RoomRuntime I App command Pause\n",
        ]
    )
    assert unstructured_navigation_failure.unresolved_preparation_requests == 1, unstructured_navigation_failure
    assert unstructured_navigation_failure.repeated_current_item_navigation == 1, unstructured_navigation_failure
    assert not unstructured_navigation_failure.stable, unstructured_navigation_failure
    superseded_preparation = analyze(
        [
            "2026-01-01 10:00:00.000 RoomRuntime I Transport command id=abc action=NEXT phase=ACCEPTED item=b message=Preparing\n",
            "2026-01-01 10:00:00.010 RoomRuntime I Apply QueueItemPreparationRequested sequence=1\n",
            "2026-01-01 10:00:00.500 RoomRuntime I Transport command id=abc action=NEXT phase=SUPERSEDED item=b message=Replaced\n",
        ]
    )
    assert superseded_preparation.unresolved_preparation_requests == 0, superseded_preparation
    assert superseded_preparation.stable, superseded_preparation
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
