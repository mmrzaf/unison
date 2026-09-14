#!/usr/bin/env python3
"""Validate release APK identity metadata emitted by `aapt2 dump badging`."""
from __future__ import annotations

import argparse
from pathlib import Path
import re


def version_value(path: Path, name: str) -> str:
    text = path.read_text()
    match = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise ValueError(f"Could not read {name} from {path}")
    return match.group(1)


def quoted_field(line: str, field: str) -> str | None:
    match = re.search(rf"\b{re.escape(field)}='([^']*)'", line)
    return match.group(1) if match else None


def validate_badging(
    text: str,
    *,
    application_id: str,
    version_name: str,
    version_code: str,
    min_sdk: str,
    target_sdk: str,
) -> None:
    package_line = next((line for line in text.splitlines() if line.startswith("package: ")), None)
    if package_line is None:
        raise ValueError("aapt2 badging output has no package line")

    expected_fields = {
        "name": application_id,
        "versionName": version_name,
        "versionCode": version_code,
    }
    for field, expected in expected_fields.items():
        actual = quoted_field(package_line, field)
        if actual != expected:
            raise ValueError(f"Release APK {field} mismatch: expected {expected!r}, got {actual!r}")

    sdk_match = re.search(r"(?m)^(?:minSdkVersion|sdkVersion):'([^']+)'$", text)
    target_match = re.search(r"(?m)^targetSdkVersion:'([^']+)'$", text)
    if sdk_match is None or sdk_match.group(1) != min_sdk:
        actual = sdk_match.group(1) if sdk_match else None
        raise ValueError(f"Release APK minSdk mismatch: expected {min_sdk}, got {actual}")
    if target_match is None or target_match.group(1) != target_sdk:
        actual = target_match.group(1) if target_match else None
        raise ValueError(f"Release APK targetSdk mismatch: expected {target_sdk}, got {actual}")

    if re.search(r"(?m)^application-debuggable(?:\b|$)", text):
        raise ValueError("Release APK is debuggable")

    launchable = re.search(r"(?m)^launchable-activity:\s+name='([^']+)'", text)
    if launchable is None or launchable.group(1) != "com.darius.unison.ui.MainActivity":
        actual = launchable.group(1) if launchable else None
        raise ValueError(f"Unexpected release launcher activity: {actual!r}")


def self_test() -> None:
    valid = """package: name='com.darius.unison' versionCode='4242' versionName='9.9.9-test.1'\nminSdkVersion:'30'\ntargetSdkVersion:'33'\nlaunchable-activity: name='com.darius.unison.ui.MainActivity'  label='Unison' icon=''\n"""
    validate_badging(
        valid,
        application_id="com.darius.unison",
        version_name="9.9.9-test.1",
        version_code="4242",
        min_sdk="30",
        target_sdk="33",
    )
    try:
        validate_badging(
            valid + "application-debuggable\n",
            application_id="com.darius.unison",
            version_name="9.9.9-test.1",
            version_code="4242",
            min_sdk="30",
            target_sdk="33",
        )
    except ValueError as error:
        assert "debuggable" in str(error)
    else:
        raise AssertionError("debuggable release APK was accepted")
    print("RELEASE_APK_METADATA_CHECK_OK")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--badging-output")
    parser.add_argument("--versions", default="gradle/libs.versions.toml")
    parser.add_argument("--application-id", default="com.darius.unison")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0
    if not args.badging_output:
        parser.error("--badging-output is required")

    versions = Path(args.versions)
    try:
        validate_badging(
            Path(args.badging_output).read_text(),
            application_id=args.application_id,
            version_name=version_value(versions, "appVersionName"),
            version_code=version_value(versions, "appVersionCode"),
            min_sdk=version_value(versions, "minSdk"),
            target_sdk=version_value(versions, "targetSdk"),
        )
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error

    print("Release APK identity metadata verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
