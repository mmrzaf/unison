#!/usr/bin/env python3
"""Deterministic benchmark for Unison's current Room/SQLite library search shape."""

from __future__ import annotations

import argparse
import json
import sqlite3
import statistics
import time
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Result:
    tracks: int
    median_ms: float
    p95_ms: float
    matches: int


QUERY = """
SELECT trackId FROM tracks
WHERE ? = ''
   OR LOWER(COALESCE(title, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(artist, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(album, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(originalFileName, '')) LIKE '%' || LOWER(?) || '%'
ORDER BY LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC
LIMIT 60 OFFSET 0
"""


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def benchmark(track_count: int, iterations: int) -> Result:
    connection = sqlite3.connect(":memory:")
    connection.executescript(
        """
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        CREATE TABLE tracks (
            trackId TEXT NOT NULL PRIMARY KEY,
            sizeBytes INTEGER NOT NULL,
            mimeType TEXT,
            durationMs INTEGER NOT NULL,
            title TEXT,
            artist TEXT,
            album TEXT,
            originalFileName TEXT,
            createdAt INTEGER NOT NULL,
            lastPlayedAt INTEGER
        );
        CREATE INDEX index_tracks_title ON tracks(title);
        CREATE INDEX index_tracks_artist ON tracks(artist);
        CREATE INDEX index_tracks_album ON tracks(album);
        CREATE INDEX index_tracks_originalFileName ON tracks(originalFileName);
        """
    )
    rows = []
    for index in range(track_count):
        title = f"needle track {index}" if index % 997 == 0 else f"track {index}"
        rows.append(
            (
                f"{index:064x}"[-64:],
                4_000_000,
                "audio/mpeg",
                180_000,
                title,
                f"artist {index % 500}",
                f"album {index % 200}",
                f"{title}.mp3",
                index,
                None,
            )
        )
    connection.executemany("INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
    connection.commit()

    params = ("needle",) * 5
    for _ in range(3):
        connection.execute(QUERY, params).fetchall()
    timings = []
    matches = 0
    for _ in range(iterations):
        started = time.perf_counter()
        result_rows = connection.execute(QUERY, params).fetchall()
        timings.append((time.perf_counter() - started) * 1_000.0)
        matches = len(result_rows)
    connection.close()
    return Result(
        tracks=track_count,
        median_ms=round(statistics.median(timings), 2),
        p95_ms=round(percentile(timings, 0.95), 2),
        matches=matches,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sizes", default="10000,50000,100000")
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    sizes = [int(value) for value in args.sizes.split(",") if value.strip()]
    results = [benchmark(size, args.iterations) for size in sizes]
    if args.json:
        print(json.dumps([asdict(result) for result in results], indent=2))
        return
    print("tracks\tmedian_ms\tp95_ms\tmatches")
    for result in results:
        print(f"{result.tracks}\t{result.median_ms:.2f}\t{result.p95_ms:.2f}\t{result.matches}")


if __name__ == "__main__":
    main()
