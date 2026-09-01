#!/usr/bin/env python3
"""Verify release analyzers against sanitized real-device regression shapes."""

from __future__ import annotations

import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "scripts" / "fixtures" / "diagnostics"


@dataclass(frozen=True)
class Case:
    name: str
    playback_passes: bool
    stability_passes: bool
    playback_marker: str | None = None
    stability_markers: tuple[str, ...] = ()


CASES = (
    Case("good-phase4.ndjson", True, True),
    Case(
        "bad-natural-end-resurrection.ndjson",
        False,
        True,
        playback_marker="natural-end observations missing physical boundary handoff",
    ),
    Case(
        "bad-empty-readiness-cohort.ndjson",
        False,
        True,
        playback_marker="empty content-readiness cohort",
    ),
    Case(
        "bad-system-policy-inhibition.ndjson",
        False,
        True,
        playback_marker="SYSTEM_POLICY",
    ),
    Case(
        "bad-unavailable-command-spam.ndjson",
        False,
        False,
        playback_marker="unavailable-media command rejections",
        stability_markers=("unavailable-media playback rejections",),
    ),
    Case(
        "bad-auto-rejoin-stuck.ndjson",
        False,
        True,
        playback_marker="automatic audio-focus rejoin",
    ),
    Case(
        "bad-unlocked-clock-projection.ndjson",
        False,
        True,
        playback_marker="unlocked room clock",
    ),
    Case(
        "bad-teardown.ndjson",
        True,
        False,
        stability_markers=("remaining coroutine jobs",),
    ),
    Case(
        "bad-arrival-late.ndjson",
        True,
        False,
        stability_markers=("before PlayerExecutor",),
    ),
    Case(
        "bad-executor-late.ndjson",
        True,
        False,
        stability_markers=("PlayerExecutor missed scheduled playback",),
    ),
    Case(
        "bad-actor-handler-cancellation.ndjson",
        True,
        False,
        stability_markers=("unexpected room actor handler cancellations",),
    ),
    Case(
        "good-transfer-policy-blocked-bounded.ndjson",
        True,
        True,
        stability_markers=(
            "transfer attempts=1",
            "socket route attempts=1 failures=1 suspensions=1 retry_requests=0",
            'socket route failures by reason={"POLICY_BLOCKED": 1}',
        ),
    ),
    Case(
        "bad-transfer-preconnect-retry-storm.ndjson",
        True,
        False,
        stability_markers=(
            "transfer attempts=4",
            "transfer retry storm",
        ),
    ),
)


def run(analyzer: str, fixture: Path):
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / analyzer), str(fixture), "--strict"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    return result.returncode == 0, result.stdout + result.stderr


def main() -> int:
    failures: list[str] = []
    for case in CASES:
        fixture = FIXTURES / case.name
        if not fixture.is_file():
            failures.append(f"missing fixture: {case.name}")
            continue
        playback_passes, playback_output = run("analyze-playback-log.py", fixture)
        stability_passes, stability_output = run("analyze-stability-log.py", fixture)
        if playback_passes != case.playback_passes:
            failures.append(
                f"{case.name}: playback expected pass={case.playback_passes}, got pass={playback_passes}\n{playback_output}"
            )
        if stability_passes != case.stability_passes:
            failures.append(
                f"{case.name}: stability expected pass={case.stability_passes}, got pass={stability_passes}\n{stability_output}"
            )
        if case.playback_marker and case.playback_marker not in playback_output:
            failures.append(f"{case.name}: playback output missing marker {case.playback_marker!r}")
        for marker in case.stability_markers:
            if marker not in stability_output:
                failures.append(f"{case.name}: stability output missing marker {marker!r}")

    if failures:
        print("LOG_ANALYZER_FIXTURE_FAILURES", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"LOG_ANALYZER_FIXTURES_OK ({len(CASES)} cases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
