#!/usr/bin/env python3
"""Small dependency-free Kotlin source sanity checks for patch regressions.

This is not a compiler. It catches high-value mistakes that the offline core harness does not
compile, especially duplicate named arguments in Android/Compose source.
"""
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
    block_depth = 0
    while i < len(text):
        if state == "normal":
            if text.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line"
            elif text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block"
                block_depth = 1
            elif text.startswith('"""', i):
                out[i:i + 3] = "   "
                i += 3
                state = "triple"
            elif text[i] == '"':
                out[i] = " "
                i += 1
                state = "string"
            elif text[i] == "'":
                out[i] = " "
                i += 1
                state = "char"
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
                out[i] = out[i + 1] = " "
                block_depth += 1
                i += 2
            elif text.startswith("*/", i):
                out[i] = out[i + 1] = " "
                block_depth -= 1
                i += 2
                if block_depth == 0:
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
        else:  # normal string or char
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
    for index, ch in enumerate(text):
        if ch in "([{":
            stack.append((ch, index))
        elif ch in ")]}":
            line = text.count("\n", 0, index) + 1
            if not stack or stack[-1][0] != pairs[ch]:
                problems.append(f"{path.relative_to(ROOT)}:{line}: unmatched '{ch}'")
                continue
            stack.pop()
    for ch, index in stack:
        line = text.count("\n", 0, index) + 1
        problems.append(f"{path.relative_to(ROOT)}:{line}: unclosed '{ch}'")
    return problems


def duplicate_named_arguments(path: Path) -> list[str]:
    raw = path.read_text(errors="ignore")
    text = sanitize(raw)
    stack: list[dict[str, int]] = []
    problems: list[str] = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "(":
            stack.append({})
            i += 1
            continue
        if ch == ")":
            if stack:
                stack.pop()
            i += 1
            continue
        if ch == "=" and stack:
            previous = text[i - 1] if i > 0 else ""
            following = text[i + 1] if i + 1 < len(text) else ""
            if previous not in "=!<>+-*/%&|^" and following not in "=>":
                j = i - 1
                while j >= 0 and text[j].isspace():
                    j -= 1
                end = j + 1
                while j >= 0 and (text[j].isalnum() or text[j] == "_"):
                    j -= 1
                name = text[j + 1:end]
                k = j
                while k >= 0 and text[k].isspace():
                    k -= 1
                if name and (k < 0 or text[k] in "(,"):
                    line = text.count("\n", 0, i) + 1
                    first = stack[-1].get(name)
                    if first is not None:
                        problems.append(
                            f"{path.relative_to(ROOT)}:{line}: duplicate named argument "
                            f"'{name}' (first at line {first})"
                        )
                    else:
                        stack[-1][name] = line
        i += 1
    return problems


def main() -> int:
    problems: list[str] = []
    for path in SOURCE_ROOT.rglob("*.kt"):
        problems.extend(delimiter_problems(path))
        problems.extend(duplicate_named_arguments(path))

    app = (SOURCE_ROOT / "com/darius/unison/ui/UnisonApp.kt").read_text()
    used_icons = set(re.findall(r"Icons\.Default\.([A-Za-z0-9_]+)", app))
    imported_icons = set(
        re.findall(r"import androidx\.compose\.material\.icons\.filled\.([A-Za-z0-9_]+)", app)
    )
    for icon in sorted(used_icons - imported_icons):
        problems.append(f"UnisonApp.kt: missing filled icon import for {icon}")

    runtime = (SOURCE_ROOT / "com/darius/unison/room/RoomRuntime.kt").read_text()
    if "withTimeoutOrNull(MANUAL_DISCOVERY_WINDOW_MS)" not in runtime:
        problems.append("Room discovery is no longer bounded by the manual scan window")
    if "MANUAL_DISCOVERY_WINDOW_MS = 8_000L" not in runtime:
        problems.append("Manual room discovery must remain an 8-second user-triggered scan")
    if app.count("AppCommand.StartDiscovery") != 1:
        problems.append("Room discovery must have exactly one explicit UI trigger")

    changelog = (ROOT / "CHANGELOG.md").read_text().lower()
    if "discovery automatic" in changelog or "automatic while the room lobby" in changelog:
        problems.append("Changelog still documents the rejected automatic-discovery behavior")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Kotlin patch-regression checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
