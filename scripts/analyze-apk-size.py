#!/usr/bin/env python3
"""Deterministic APK size breakdown and release-size gate."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import sys
import zipfile


def category(name: str) -> str:
    if name.startswith("classes") and name.endswith(".dex"):
        return "DEX"
    if name.startswith("lib/"):
        return "Native libraries"
    if name.startswith("res/") or name == "resources.arsc":
        return "Android resources"
    if name.startswith("assets/"):
        return "Assets"
    if name.startswith("META-INF/"):
        return "META-INF"
    return "Other"


def mib(value: int) -> str:
    return f"{value / (1024 * 1024):.2f} MiB"


def analyze(apk: Path, maximum_bytes: int | None) -> int:
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    compressed = apk.stat().st_size
    totals: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    largest: list[tuple[int, int, str]] = []
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            totals[category(entry.filename)][0] += entry.compress_size
            totals[category(entry.filename)][1] += entry.file_size
            largest.append((entry.compress_size, entry.file_size, entry.filename))

    print(f"APK: {apk}")
    print(f"Compressed file size: {mib(compressed)} ({compressed:,} bytes)")
    print("\nBreakdown (compressed -> installed/uncompressed payload):")
    for name, (packed, raw) in sorted(totals.items(), key=lambda item: item[1][0], reverse=True):
        print(f"  {name:20} {mib(packed):>11} -> {mib(raw):>11}")

    print("\nLargest packaged entries:")
    for packed, raw, name in sorted(largest, reverse=True)[:15]:
        print(f"  {mib(packed):>11} -> {mib(raw):>11}  {name}")

    if maximum_bytes is not None and compressed > maximum_bytes:
        print(
            f"\nERROR: release APK is {mib(compressed)}, above the gate of {mib(maximum_bytes)}.",
            file=sys.stderr,
        )
        return 1
    if maximum_bytes is not None:
        print(f"\nSize gate: PASS (limit {mib(maximum_bytes)})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--max-bytes", type=int, default=None)
    args = parser.parse_args()
    return analyze(args.apk, args.max_bytes)


if __name__ == "__main__":
    raise SystemExit(main())
