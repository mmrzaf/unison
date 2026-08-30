#!/usr/bin/env python3
"""Validate that Gradle dependency-verification metadata is present and checksum-based."""
from __future__ import annotations

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "gradle/verification-metadata.xml"


def main() -> int:
    if not METADATA.is_file():
        raise SystemExit(
            "Missing gradle/verification-metadata.xml. Generate it from a trusted resolution path "
            "with scripts/refresh-dependency-verification.sh and review the result before release."
        )

    root = ET.parse(METADATA).getroot()
    names = [element.tag.rsplit("}", 1)[-1] for element in root.iter()]
    if "sha256" not in names:
        raise SystemExit("Dependency verification metadata contains no SHA-256 artifact checksums")
    if "component" not in names:
        raise SystemExit("Dependency verification metadata contains no verified components")

    text = METADATA.read_text(errors="ignore")
    if "<verify-metadata>false</verify-metadata>" in text:
        raise SystemExit("Gradle module-metadata verification must not be explicitly disabled")

    print("Dependency verification metadata is present and checksum-based.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
