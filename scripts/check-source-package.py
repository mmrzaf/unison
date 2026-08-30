#!/usr/bin/env python3
"""Validate a distributable Unison source archive without requiring Git metadata."""
from __future__ import annotations

import argparse
from pathlib import PurePosixPath
import tarfile

SENSITIVE_NAMES = {"keystore.properties", "local.properties", ".env"}
SENSITIVE_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".base64")
FORBIDDEN_PARTS = {
    ".git",
    ".gradle",
    ".kotlin",
    "build",
    "dist",
    "keystore",
    "signing",
    "captures",
    "__pycache__",
}
REQUIRED_SUFFIXES = {
    "README.md",
    "LICENSE",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SUPPORT.md",
    "THIRD_PARTY_NOTICES.md",
    ".github/SECURITY.md",
    ".github/PULL_REQUEST_TEMPLATE.md",
    ".github/ISSUE_TEMPLATE/bug.yml",
    ".github/workflows/android.yml",
    ".github/workflows/release.yml",
    "docs/DEVELOPMENT.md",
    "docs/GITHUB_SETUP.md",
    "docs/RELEASE_QUALIFICATION.md",
    "gradlew",
    "gradle/libs.versions.toml",
    "app/build.gradle.kts",
    "scripts/check-source-tree.py",
}


def normalized_member_path(name: str) -> PurePosixPath:
    path = PurePosixPath(name)
    parts = tuple(part for part in path.parts if part not in ("", "."))
    if any(part == ".." for part in parts):
        raise ValueError(f"Archive member escapes package root: {name}")
    return PurePosixPath(*parts)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive")
    args = parser.parse_args()

    with tarfile.open(args.archive, "r:*") as archive:
        paths: list[PurePosixPath] = []
        for member in archive.getmembers():
            path = normalized_member_path(member.name)
            if not path.parts:
                continue
            paths.append(path)
            lower_parts = tuple(part.lower() for part in path.parts)
            basename = lower_parts[-1]
            if basename in SENSITIVE_NAMES:
                raise SystemExit(f"Sensitive/local file found in source package: {path}")
            if basename.endswith(SENSITIVE_SUFFIXES):
                raise SystemExit(f"Sensitive key material found in source package: {path}")
            if any(part in FORBIDDEN_PARTS for part in lower_parts):
                raise SystemExit(f"Generated/private directory found in source package: {path}")
            if member.issym() or member.islnk():
                target = PurePosixPath(member.linkname)
                if target.is_absolute() or ".." in target.parts:
                    raise SystemExit(f"Unsafe archive link: {path} -> {member.linkname}")

        # All release packages have one versioned top-level prefix. Check required paths beneath it.
        suffixes = {"/".join(path.parts[1:]) for path in paths if len(path.parts) >= 2}
        missing = sorted(REQUIRED_SUFFIXES - suffixes)
        if missing:
            raise SystemExit("Source package is missing required paths: " + ", ".join(missing))

    print("Source package structure is safe and complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
