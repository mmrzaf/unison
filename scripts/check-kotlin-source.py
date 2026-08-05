#!/usr/bin/env python3
"""Dependency-free Kotlin source checks for the clean 1.0 source tree."""
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
    depth = 0
    while i < len(text):
        if state == "normal":
            if text.startswith("//", i):
                out[i:i + 2] = "  "
                i += 2
                state = "line"
            elif text.startswith("/*", i):
                out[i:i + 2] = "  "
                i += 2
                state = "block"
                depth = 1
            elif text.startswith('"""', i):
                out[i:i + 3] = "   "
                i += 3
                state = "triple"
            elif text[i] in {'"', "'"}:
                state = "string" if text[i] == '"' else "char"
                out[i] = " "
                i += 1
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
                out[i:i + 2] = "  "
                depth += 1
                i += 2
            elif text.startswith("*/", i):
                out[i:i + 2] = "  "
                depth -= 1
                i += 2
                if depth == 0:
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
        else:
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
    for index, char in enumerate(text):
        if char in "([{":
            stack.append((char, index))
        elif char in ")]}":
            line = text.count("\n", 0, index) + 1
            if not stack or stack[-1][0] != pairs[char]:
                problems.append(f"{path.relative_to(ROOT)}:{line}: unmatched '{char}'")
            else:
                stack.pop()
    for char, index in stack:
        line = text.count("\n", 0, index) + 1
        problems.append(f"{path.relative_to(ROOT)}:{line}: unclosed '{char}'")
    return problems


def duplicate_named_arguments(path: Path) -> list[str]:
    text = sanitize(path.read_text(errors="ignore"))
    stack: list[dict[str, int]] = []
    problems: list[str] = []
    for index, char in enumerate(text):
        if char == "(":
            stack.append({})
        elif char == ")":
            if stack:
                stack.pop()
        elif char == "=" and stack:
            previous = text[index - 1] if index else ""
            following = text[index + 1] if index + 1 < len(text) else ""
            if previous in "=!<>+-*/%&|^" or following in "=>":
                continue
            cursor = index - 1
            while cursor >= 0 and text[cursor].isspace():
                cursor -= 1
            end = cursor + 1
            while cursor >= 0 and (text[cursor].isalnum() or text[cursor] == "_"):
                cursor -= 1
            name = text[cursor + 1:end]
            before = cursor
            while before >= 0 and text[before].isspace():
                before -= 1
            if name and (before < 0 or text[before] in "(,"):
                line = text.count("\n", 0, index) + 1
                first = stack[-1].get(name)
                if first is None:
                    stack[-1][name] = line
                else:
                    problems.append(
                        f"{path.relative_to(ROOT)}:{line}: duplicate named argument "
                        f"'{name}' (first at line {first})"
                    )
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


def require(text: str, marker: str, message: str, problems: list[str]) -> None:
    if marker not in text:
        problems.append(message)


def main() -> int:
    problems: list[str] = []
    kotlin_files = sorted(SOURCE_ROOT.rglob("*.kt"))
    for path in kotlin_files:
        problems.extend(delimiter_problems(path))
        problems.extend(duplicate_named_arguments(path))
        problems.extend(misplaced_imports(path))

    app = (SOURCE_ROOT / "com/darius/unison/ui/UnisonApp.kt").read_text()
    runtime = (SOURCE_ROOT / "com/darius/unison/room/RoomRuntime.kt").read_text()
    protocol = (SOURCE_ROOT / "com/darius/unison/protocol/ProtocolModels.kt").read_text()
    protocol_json = (SOURCE_ROOT / "com/darius/unison/protocol/ProtocolJson.kt").read_text()
    database = (SOURCE_ROOT / "com/darius/unison/storage/Database.kt").read_text()
    peer_server = (SOURCE_ROOT / "com/darius/unison/network/PeerServer.kt").read_text()
    production = "\n".join(path.read_text(errors="ignore") for path in kotlin_files)

    require(app, "HomeScreen(", "Home is not the out-of-room surface", problems)
    require(app, "SharedRoomScreen(", "Shared room is not the in-room surface", problems)
    if "NavigationBar" in app or re.search(r"\bLibraryScreen\s*\(", app) or re.search(r"(?<!Shared)\bRoomScreen\s*\(", app):
        problems.append("Legacy destination navigation was reintroduced")

    for obsolete in (
        SOURCE_ROOT / "com/darius/unison/ui/room/RoomScreens.kt",
        SOURCE_ROOT / "com/darius/unison/ui/library/LibraryScreen.kt",
        SOURCE_ROOT / "com/darius/unison/room/RoomPersistenceManager.kt",
    ):
        if obsolete.exists():
            problems.append(f"Obsolete production file remains: {obsolete.relative_to(ROOT)}")

    require(protocol, "const val PROTOCOL_VERSION = 1", "Protocol baseline is not 1", problems)
    require(protocol, "data class PinClientHello", "PIN hello is not explicit", problems)
    require(protocol, "data class ReconnectClientHello", "Reconnect hello is not explicit", problems)
    require(protocol, "data class FileClientHello", "File hello is not explicit", problems)
    if any(marker in production for marker in ("protocolVersions", "acceptedVersion", "reconnectRequested", "fileRequest")):
        problems.append("Protocol compatibility/nullable hello state remains")
    if "ignoreUnknownKeys = false" not in protocol_json or "explicitNulls = true" not in protocol_json:
        problems.append("Protocol JSON is not strict")
    if "ChannelType" in production:
        problems.append("Obsolete channel negotiation remains")
    require(peer_server, "rejectProtocolMismatch", "Handshake version rejection is missing", problems)

    if "room_snapshots" in database or "RoomSnapshotEntity" in database:
        problems.append("Legacy persisted room snapshot schema remains")
    require(database, "version = 1", "Database is not a fresh schema 1", problems)
    if re.search(r"Migration\s*\(|addMigrations|fallbackToDestructiveMigration", production):
        problems.append("Migration or compatibility path exists in the fresh schema")

    require(runtime, "SerializedEventLoop<RoomEvent>", "Room actor serialization is missing", problems)
    require(runtime, "canonicalPlayback.applyRemoteSync", "Canonical convergence path is missing", problems)
    require(runtime, "QueuePreparationFence", "Async queue mutation fencing is missing", problems)
    if "RoomPersistenceManager" in runtime:
        problems.append("Runtime still owns obsolete persistence cleanup")

    if "TrackArtwork" in production or "AsyncImage" in production:
        problems.append("Artwork-driven product UI was reintroduced")
    if "GlobalScope" in production or "runBlocking" in "\n".join(
        p.read_text(errors="ignore") for p in SOURCE_ROOT.rglob("*.kt")
    ):
        problems.append("Unstructured coroutine usage exists in production")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Kotlin source sanity checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
