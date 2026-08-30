#!/usr/bin/env python3
"""Write public, non-secret provenance metadata for one signed Unison APK."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def version_value(name: str) -> str:
    text = (ROOT / "gradle/libs.versions.toml").read_text()
    match = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise SystemExit(f"Could not read {name}")
    return match.group(1)


def protocol_version() -> str:
    text = (ROOT / "app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt").read_text()
    match = re.search(r"const val PROTOCOL_VERSION\s*=\s*(\d+)", text)
    if not match:
        raise SystemExit("Could not read protocol version")
    return match.group(1)


def certificate_digest(text: str) -> str:
    match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", text)
    if not match:
        raise SystemExit("Could not find signing certificate SHA-256 digest in apksigner output")
    return match.group(1).lower()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--apksigner-output", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--git-sha", required=True)
    args = parser.parse_args()

    apk = Path(args.apk)
    info = [
        "Unison release provenance",
        f"versionName={version_value('appVersionName')}",
        f"versionCode={version_value('appVersionCode')}",
        f"gitCommit={args.git_sha}",
        f"protocolVersion={protocol_version()}",
        "databaseSchema=1",
        f"apkSha256={sha256(apk)}",
        f"signingCertificateSha256={certificate_digest(Path(args.apksigner_output).read_text())}",
    ]
    Path(args.output).write_text("\n".join(info) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
