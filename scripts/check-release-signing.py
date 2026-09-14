#!/usr/bin/env python3
"""Fail closed when a release signing certificate differs from the pinned identity."""
from __future__ import annotations

import argparse
from pathlib import Path
import re

DIGEST = re.compile(r"^[0-9a-f]{64}$")
APKSIGNER_DIGEST = re.compile(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", re.IGNORECASE)
def normalize_digest(value: str) -> str:
    normalized = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if not DIGEST.fullmatch(normalized):
        raise ValueError("Signing certificate SHA-256 must contain exactly 64 hexadecimal digits")
    return normalized


def apksigner_digest(text: str) -> str:
    matches = [normalize_digest(value) for value in APKSIGNER_DIGEST.findall(text)]
    if not matches:
        raise ValueError("Could not find a signing certificate SHA-256 digest in apksigner output")
    unique = set(matches)
    if len(unique) != 1:
        raise ValueError("APK contains multiple distinct signer certificate SHA-256 digests")
    return matches[0]


def verify(expected: str, actual: str) -> str:
    expected_normalized = normalize_digest(expected)
    actual_normalized = normalize_digest(actual)
    if actual_normalized != expected_normalized:
        raise ValueError(
            "Release signing certificate mismatch: "
            f"expected {expected_normalized}, got {actual_normalized}"
        )
    return actual_normalized


def self_test() -> None:
    digest = "ab" * 32
    colonized = ":".join(digest[index : index + 2] for index in range(0, len(digest), 2)).upper()
    assert normalize_digest(colonized) == digest
    assert apksigner_digest(f"Signer #1 certificate SHA-256 digest: {colonized}\n") == digest
    assert verify(colonized, digest) == digest
    try:
        verify(digest, "cd" * 32)
    except ValueError as error:
        assert "mismatch" in str(error)
    else:
        raise AssertionError("mismatched signing identity was accepted")
    try:
        apksigner_digest(
            "Signer #1 certificate SHA-256 digest: "
            + colonized
            + "\nSigner #2 certificate SHA-256 digest: "
            + ":".join(["cd"] * 32)
        )
    except ValueError as error:
        assert "multiple" in str(error)
    else:
        raise AssertionError("multiple signer identities were accepted")
    print("RELEASE_SIGNING_CHECK_OK")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected")
    parser.add_argument("--actual")
    parser.add_argument("--apksigner-output")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0

    if not args.expected:
        parser.error("--expected is required")
    if bool(args.actual) == bool(args.apksigner_output):
        parser.error("provide exactly one of --actual or --apksigner-output")

    try:
        actual = (
            normalize_digest(args.actual)
            if args.actual
            else apksigner_digest(Path(args.apksigner_output).read_text())
        )
        verified = verify(args.expected, actual)
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error

    print(f"Release signing certificate verified: {verified}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
