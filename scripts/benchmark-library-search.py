#!/usr/bin/env python3
"""Deterministic SQLite benchmark for Unison's v1 library browse/search query shapes."""

from __future__ import annotations

import argparse
import json
import sqlite3
import statistics
import time
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Result:
    scenario: str
    tracks: int
    offset: int
    median_ms: float
    p95_ms: float
    matches: int
    plan: tuple[str, ...]


BROWSE_QUERIES = {
    "recent": "SELECT * FROM tracks ORDER BY recentSortAt DESC, trackId DESC LIMIT 60 OFFSET ?",
    "title": "SELECT * FROM tracks ORDER BY sortTitle ASC, trackId ASC LIMIT 60 OFFSET ?",
    "artist": "SELECT * FROM tracks ORDER BY sortArtist ASC, sortTitle ASC, trackId ASC LIMIT 60 OFFSET ?",
    "album": "SELECT * FROM tracks ORDER BY sortAlbum ASC, sortTitle ASC, trackId ASC LIMIT 60 OFFSET ?",
}
SEARCH_QUERY = """
SELECT * FROM tracks
WHERE searchText LIKE '%' || ? || '%' ESCAPE '!'
ORDER BY sortTitle ASC, trackId ASC
LIMIT 60 OFFSET ?
"""
EXPECTED_BROWSE_INDEX = {
    "recent": "index_tracks_recentSortAt_trackId",
    "title": "index_tracks_sortTitle_trackId",
    "artist": "index_tracks_sortArtist_sortTitle_trackId",
    "album": "index_tracks_sortAlbum_sortTitle_trackId",
}


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return ordered[index]


def create_database(track_count: int) -> sqlite3.Connection:
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
            searchText TEXT NOT NULL,
            sortTitle TEXT NOT NULL,
            sortArtist TEXT NOT NULL,
            sortAlbum TEXT NOT NULL,
            recentSortAt INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            lastPlayedAt INTEGER
        );
        CREATE INDEX index_tracks_recentSortAt_trackId ON tracks(recentSortAt, trackId);
        CREATE INDEX index_tracks_sortTitle_trackId ON tracks(sortTitle, trackId);
        CREATE INDEX index_tracks_sortArtist_sortTitle_trackId ON tracks(sortArtist, sortTitle, trackId);
        CREATE INDEX index_tracks_sortAlbum_sortTitle_trackId ON tracks(sortAlbum, sortTitle, trackId);
        """
    )
    rows = []
    for index in range(track_count):
        title = f"needle track {index}" if index % 997 == 0 else f"track {index}"
        artist = f"artist {index % 500}"
        album = f"album {index % 200}"
        file_name = f"{title}.mp3"
        rows.append(
            (
                f"{index:064x}"[-64:],
                4_000_000,
                "audio/mpeg",
                180_000,
                title,
                artist,
                album,
                file_name,
                f"{title} {artist} {album} {file_name}".lower(),
                title.lower(),
                artist.lower(),
                album.lower(),
                index,
                index,
                None,
            )
        )
    connection.executemany(
        "INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows
    )
    connection.commit()
    return connection


def query_plan(connection: sqlite3.Connection, query: str, params: tuple[object, ...]) -> tuple[str, ...]:
    return tuple(row[3] for row in connection.execute("EXPLAIN QUERY PLAN " + query, params))


def timed_query(
    connection: sqlite3.Connection,
    scenario: str,
    track_count: int,
    query: str,
    params: tuple[object, ...],
    iterations: int,
    offset: int,
) -> Result:
    for _ in range(3):
        connection.execute(query, params).fetchall()
    timings = []
    matches = 0
    for _ in range(iterations):
        started = time.perf_counter()
        rows = connection.execute(query, params).fetchall()
        timings.append((time.perf_counter() - started) * 1_000.0)
        matches = len(rows)
    return Result(
        scenario=scenario,
        tracks=track_count,
        offset=offset,
        median_ms=round(statistics.median(timings), 2),
        p95_ms=round(percentile(timings, 0.95), 2),
        matches=matches,
        plan=query_plan(connection, query, params),
    )


def benchmark(track_count: int, iterations: int) -> list[Result]:
    connection = create_database(track_count)
    results: list[Result] = []
    try:
        for name, query in BROWSE_QUERIES.items():
            results.append(
                timed_query(connection, f"browse_{name}", track_count, query, (0,), iterations, 0)
            )
        deep_offset = max(0, min(90_000, track_count - 60))
        results.append(
            timed_query(
                connection,
                "browse_title_deep",
                track_count,
                BROWSE_QUERIES["title"],
                (deep_offset,),
                iterations,
                deep_offset,
            )
        )
        results.append(
            timed_query(
                connection,
                "search_title",
                track_count,
                SEARCH_QUERY,
                ("needle", 0),
                iterations,
                0,
            )
        )
        results.append(
            timed_query(
                connection,
                "search_title_miss",
                track_count,
                SEARCH_QUERY,
                ("definitely-not-present", 0),
                iterations,
                0,
            )
        )
        return results
    finally:
        connection.close()


def verify_browse_plans(results: list[Result]) -> None:
    for result in results:
        if not result.scenario.startswith("browse_"):
            continue
        base = result.scenario.removeprefix("browse_").removesuffix("_deep")
        expected_index = EXPECTED_BROWSE_INDEX[base]
        plan = " | ".join(result.plan)
        if expected_index not in plan:
            raise SystemExit(f"{result.scenario} did not use {expected_index}: {plan}")
        if "TEMP B-TREE FOR ORDER BY" in plan:
            raise SystemExit(f"{result.scenario} regressed to a temporary sort: {plan}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sizes", default="10000,50000,100000")
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--max-browse-p95-ms", type=float, default=None)
    parser.add_argument("--max-search-p95-ms", type=float, default=None)
    args = parser.parse_args()
    sizes = [int(value) for value in args.sizes.split(",") if value.strip()]
    results = [result for size in sizes for result in benchmark(size, args.iterations)]
    verify_browse_plans(results)
    if args.json:
        print(json.dumps([asdict(result) for result in results], indent=2))
    else:
        print("scenario\ttracks\toffset\tmedian_ms\tp95_ms\tmatches")
        for result in results:
            print(
                f"{result.scenario}\t{result.tracks}\t{result.offset}\t"
                f"{result.median_ms:.2f}\t{result.p95_ms:.2f}\t{result.matches}"
            )
    if args.max_browse_p95_ms is not None:
        slow = [
            result
            for result in results
            if result.scenario.startswith("browse_") and result.p95_ms > args.max_browse_p95_ms
        ]
        if slow:
            details = ", ".join(f"{r.scenario}:{r.p95_ms:.2f}ms" for r in slow)
            raise SystemExit(
                f"Library browse p95 exceeded {args.max_browse_p95_ms:.2f}ms: {details}"
            )
    if args.max_search_p95_ms is not None:
        slow = [
            result
            for result in results
            if result.scenario.startswith("search_") and result.p95_ms > args.max_search_p95_ms
        ]
        if slow:
            details = ", ".join(f"{r.scenario}:{r.p95_ms:.2f}ms" for r in slow)
            raise SystemExit(
                f"Library search p95 exceeded {args.max_search_p95_ms:.2f}ms: {details}"
            )


if __name__ == "__main__":
    main()
