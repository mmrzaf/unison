#!/usr/bin/env python3
"""Extract the current version's curated Markdown section from CHANGELOG.md."""
from __future__ import annotations

import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def version_name() -> str:
    versions = (ROOT / "gradle/libs.versions.toml").read_text()
    match = re.search(r'^appVersionName\s*=\s*"([^"]+)"\s*$', versions, re.MULTILINE)
    if match is None:
        raise SystemExit("Could not read appVersionName")
    return match.group(1)


def changelog_section(version: str) -> str:
    changelog = (ROOT / "CHANGELOG.md").read_text()
    heading = f"## {version}\n"
    start = changelog.find(heading)
    if start < 0:
        raise SystemExit(f"CHANGELOG.md has no {heading.strip()} section")
    body_start = start + len(heading)
    next_heading = changelog.find("\n## ", body_start)
    if next_heading < 0:
        next_heading = len(changelog)
    body = changelog[body_start:next_heading].strip()
    if not body:
        raise SystemExit(f"CHANGELOG.md section for {version} is empty")
    return body


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    version = version_name()
    body = changelog_section(version)
    prefix = ""
    if "-beta." in version:
        prefix = "**Public beta:** production-style prerelease for real-world testing before stable 1.2.0.\n\n"
    elif "-rc." in version:
        prefix = "**Release candidate:** intended to become stable unless a release blocker is found.\n\n"

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(prefix + body + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
