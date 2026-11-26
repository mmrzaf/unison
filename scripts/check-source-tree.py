#!/usr/bin/env python3
"""Repository-level release invariants that are properties of the tree, not of code behavior.

Behavioral correctness belongs in executable tests. This gate only covers what tests cannot see:
secrets and signing hygiene, pinned build/SDK/protocol/schema contracts, release-workflow security
properties, the Android manifest's exposure surface, and the local-first dependency policy.
"""
from __future__ import annotations

from pathlib import Path
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
WORKFLOWS = (
    ".github/workflows/verify.yml",
    ".github/workflows/android.yml",
    ".github/workflows/release.yml",
    ".github/workflows/codeql.yml",
    ".github/workflows/refresh-dependency-verification.yml",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def text(path: str) -> str:
    value = ROOT / path
    require(value.is_file(), f"Missing required file: {path}")
    return value.read_text(errors="ignore")


def repository_files() -> list[str]:
    """Return the source inventory in either a Git checkout or an exported source package."""
    try:
        inside = subprocess.check_output(
            ["git", "rev-parse", "--is-inside-work-tree"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        if inside == "true":
            return subprocess.check_output(
                ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
                cwd=ROOT,
                text=True,
            ).splitlines()
    except (FileNotFoundError, subprocess.CalledProcessError):
        pass

    excluded_parts = {
        ".git", ".gradle", ".kotlin", ".idea", ".vscode", "build", "dist",
        "captures", "keystore", "signing", "__pycache__",
    }
    return sorted(
        path.relative_to(ROOT).as_posix()
        for path in ROOT.rglob("*")
        if path.is_file() and not excluded_parts.intersection(path.relative_to(ROOT).parts)
    )


def version_value(versions: str, key: str) -> str:
    match = re.search(rf'^{re.escape(key)}\s*=\s*"([^"]+)"\s*$', versions, re.MULTILINE)
    require(match is not None, f"Missing {key} in version catalog")
    return match.group(1)


def check_secrets() -> None:
    gitignore = text(".gitignore")
    for generated_path in (".gradle/", ".kotlin/", "build/", "local.properties"):
        require(generated_path in gitignore, f"Generated path is not ignored: {generated_path}")
    for secret_path in ("keystore/", "keystore.properties"):
        require(secret_path in gitignore, f"Signing material is not ignored: {secret_path}")

    sensitive_suffixes = (".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".base64")
    private_key_markers = (
        "-----BEGIN " + "PRIVATE KEY-----",
        "-----BEGIN " + "ENCRYPTED PRIVATE KEY-----",
    )
    for tracked in repository_files():
        lower = tracked.lower()
        require(tracked not in {"keystore.properties", "local.properties"}, f"Sensitive/local file is tracked: {tracked}")
        require(not lower.startswith(("keystore/", "signing/")), f"Signing directory is tracked: {tracked}")
        require(not lower.endswith(sensitive_suffixes), f"Sensitive key/archive is tracked: {tracked}")
        value = ROOT / tracked
        if value.is_file() and value.stat().st_size <= 2_000_000:
            content = value.read_text(errors="ignore")
            require(not any(marker in content for marker in private_key_markers), f"Private key material is tracked: {tracked}")


def check_versions() -> str:
    versions = text("gradle/libs.versions.toml")
    version_name = version_value(versions, "appVersionName")
    version_code = version_value(versions, "appVersionCode")
    require(re.fullmatch(r"1\.2\.[0-9]+(-(beta|rc)\.[1-9][0-9]*)?", version_name) is not None,
            f"Unexpected 1.2 release-line version: {version_name}")
    require(version_code.isdigit(), f"versionCode is not numeric: {version_code}")
    require(version_value(versions, "compileSdk") == "36", "compileSdk changed unexpectedly")
    require(version_value(versions, "minSdk") == "30", "minSdk changed unexpectedly")
    require(version_value(versions, "targetSdk") == "33", "targetSdk changed unexpectedly (see ADR-006)")
    require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version_value(versions, "buildTools")) is not None,
            "buildTools must be pinned to an exact version")
    require("libs.versions.buildTools.get()" in text("app/build.gradle.kts"),
            "Gradle does not use the pinned build-tools version")
    require(re.search(r"firebase|play-services|billingclient", text("app/build.gradle.kts") + versions, re.IGNORECASE) is None,
            "Hosted/store runtime dependency found")

    settings_gradle = text("settings.gradle.kts")
    require("google()" in settings_gradle and "mavenCentral()" in settings_gradle and "gradlePluginPortal()" in settings_gradle,
            "Trusted Gradle repositories are not configured")
    require("mirror" not in settings_gradle.lower(), "Repository-defined dependency mirror must not be present")
    return version_name


def check_release_records(version_name: str) -> None:
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    require(version_name not in readme, "README must remain version-independent")
    require(f"## {version_name}\n" in changelog, f"Changelog has no section for {version_name}")

    versions = text("gradle/libs.versions.toml")
    evidence = text(f"docs/release-evidence/{version_name}.md")
    require(f"- Version: `{version_name}`" in evidence, "Release evidence version drifted")
    require(f"- Tag: `v{version_name}`" in evidence, "Release evidence tag drifted")
    require(f"- versionCode: `{version_value(versions, 'appVersionCode')}`" in evidence, "Release evidence versionCode drifted")
    require(re.search(r"^Status: \*\*(IN PROGRESS|QUALIFIED)\*\*$", evidence, re.MULTILINE) is not None,
            "Release evidence status must be IN PROGRESS or QUALIFIED")


def check_workflows() -> None:
    release_ci = text(".github/workflows/release.yml")
    verify_ci = text(".github/workflows/verify.yml")
    android_ci = text(".github/workflows/android.yml")
    require("connectedDebugAndroidTest" in android_ci and "api-level: 33" in android_ci,
            "Normal CI does not execute Android instrumentation on API 33")
    require("api: [30, 33, 36]" in release_ci and "connectedDebugAndroidTest" in release_ci,
            "Release CI does not execute the required API 30/33/36 instrumentation matrix")
    require("workflow_dispatch" not in release_ci, "Release publication must be tag-triggered only")
    require("--clobber" not in release_ci, "Release assets must not be replaceable with --clobber")
    require("app-debug.apk" not in release_ci and "Unison-debug" not in release_ci, "Debug APK must not be a public release asset")
    for gate in ("ANDROID_SIGNING_CERT_SHA256", "check-release-signing.py", "verify-release-apk.sh"):
        require(gate in release_ci, f"Release workflow is missing signing gate: {gate}")
    require("Remove release signing material" in release_ci and "if: always()" in release_ci,
            "Release workflow does not clean signing material after use")
    require("persist-credentials: false" in verify_ci, "Reusable verification workflow lacks hardened checkout")
    require("spotlessCheck" in verify_ci, "CI does not enforce formatting")

    action_ref = re.compile(r"uses:\s+[^\s@]+@([0-9a-f]{40})(?:\s|$)")
    for workflow_path in WORKFLOWS:
        workflow = text(workflow_path)
        if "./gradlew" in workflow:
            require("scripts/build-tools-version.sh" in workflow,
                    f"Workflow builds without installing the pinned build tools: {workflow_path}")
        for line in workflow.splitlines():
            if "uses:" in line and "uses: ./" not in line:
                require(action_ref.search(line) is not None, f"Unpinned GitHub Action in {workflow_path}: {line.strip()}")


def check_contracts() -> None:
    protocol = text("app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt")
    protocol_json = text("app/src/main/java/com/darius/unison/protocol/ProtocolJson.kt")
    require("const val PROTOCOL_VERSION = 1" in protocol, "Wire protocol is not 1")
    require("ignoreUnknownKeys = false" in protocol_json, "Unknown protocol keys are accepted")
    require("explicitNulls = true" in protocol_json, "Protocol nullability is not explicit")

    database = text("app/src/main/java/com/darius/unison/storage/Database.kt")
    require("version = 1" in database, "Database is not schema 1")
    schemas = sorted((ROOT / "app/schemas/com.darius.unison.storage.UnisonDatabase").glob("*.json"))
    require([path.name for path in schemas] == ["1.json"], "Exactly schema 1 must be checked in")
    require(json.loads(schemas[0].read_text())["database"]["version"] == 1, "Exported schema version is not 1")

    production = "\n".join(path.read_text(errors="ignore") for path in (ROOT / "app/src/main/java").rglob("*.kt"))
    require(re.search(r"Migration\s*\(", production) is None and "addMigrations" not in production,
            "Database migration code exists; schema 1 is the only data format")
    require("fallbackToDestructiveMigration" not in production, "Destructive migration fallback exists")
    require(re.search(r"\bTODO\b|\bFIXME\b|\bHACK\b", production) is None, "Production TODO/FIXME/HACK remains")
    require(re.search(r"https?://", production) is None, "Hard-coded remote endpoint found")

    proguard_rules = text("app/proguard-rules.pro")
    for diagnostic_type in (
        "com.darius.unison.model.AppCommand$*",
        "com.darius.unison.model.UserCommand$*",
        "com.darius.unison.protocol.ProtocolBody$*",
        "com.darius.unison.room.RoomEvent$*",
        "com.darius.unison.playback.PlaybackFailure$*",
    ):
        require(f"-keepnames class {diagnostic_type}" in proguard_rules,
                f"R8 may obfuscate structured diagnostic type names: {diagnostic_type}")


def check_manifest() -> None:
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    application = manifest.find("application")
    require(application is not None, "Application manifest node is missing")
    require(application.get(ANDROID_NS + "allowBackup") == "false", "Android backup must remain disabled")
    require(application.get(ANDROID_NS + "usesCleartextTraffic") == "false", "Cleartext traffic must remain disabled")

    permissions = {node.get(ANDROID_NS + "name"): node for node in manifest.findall("uses-permission")}
    nearby = permissions.get("android.permission.NEARBY_WIFI_DEVICES")
    require(nearby is not None and nearby.get(ANDROID_NS + "usesPermissionFlags") == "neverForLocation",
            "Nearby Wi-Fi permission must remain neverForLocation")
    for permission in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"):
        node = permissions.get(permission)
        require(node is not None and node.get(ANDROID_NS + "maxSdkVersion") == "32",
                f"Pre-API-33 hotspot location permission must stop at API 32: {permission}")

    namespaces = {"android": "http://schemas.android.com/apk/res/android"}
    service = application.find("service[@android:name='.playback.UnisonRoomService']", namespaces)
    require(service is not None, "UnisonRoomService manifest declaration is missing")
    require({"mediaPlayback", "connectedDevice"}.issubset(set((service.get(ANDROID_NS + "foregroundServiceType") or "").split("|"))),
            "Room service must declare mediaPlayback and connectedDevice foreground-service types")
    exported = {
        (component_type, node.get(ANDROID_NS + "name"))
        for component_type in ("activity", "activity-alias", "service", "receiver", "provider")
        for node in application.findall(component_type)
        if node.get(ANDROID_NS + "exported") == "true"
    }
    require(exported == {("activity", ".ui.MainActivity"), ("service", ".playback.UnisonRoomService")},
            f"Unexpected exported Android component: {sorted(exported)}")
    require("if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()"
            in text("app/src/main/java/com/darius/unison/playback/UnisonRoomService.kt"),
            "Exported media session no longer rejects untrusted controllers")

    provider = application.find("provider[@android:name='androidx.core.content.FileProvider']", namespaces)
    require(provider is not None and provider.get(ANDROID_NS + "exported") == "false"
            and provider.get(ANDROID_NS + "grantUriPermissions") == "true",
            "Diagnostic FileProvider exposure changed")
    cache_paths = ET.parse(ROOT / "app/src/main/res/xml/diagnostic_file_paths.xml").getroot().findall("cache-path")
    require(len(cache_paths) == 1 and cache_paths[0].get("path") == "diagnostics/",
            "Diagnostic FileProvider path is broader than the diagnostics cache directory")
    base_config = ET.parse(ROOT / "app/src/main/res/xml/network_security_config.xml").getroot().find("base-config")
    require(base_config is not None and base_config.get("cleartextTrafficPermitted") == "false",
            "Network security config permits cleartext traffic")


CORE_PACKAGES = ("model", "protocol", "util", "room", "sync")


def check_core_purity() -> None:
    """The JVM room core must not depend on Android, loopers, or the wall clock."""
    for package in CORE_PACKAGES:
        root = ROOT / "app/src/main/java/com/darius/unison" / package
        for path in sorted(root.rglob("*.kt")):
            content = path.read_text(errors="ignore")
            name = path.relative_to(ROOT).as_posix()
            require("import android." not in content and "import androidx." not in content,
                    f"Core file imports an Android API: {name}")
            require("Dispatchers." not in content,
                    f"Core file hard-codes a coroutine dispatcher: {name}")
            require("System.nanoTime" not in content and "System.currentTimeMillis" not in content,
                    f"Core file reads the wall clock directly: {name}")


ROOM_LOCK_PATTERN = re.compile(r"\bsynchronized\s*\(|@Synchronized|ConcurrentHashMap|ReentrantLock|\bMutex\(")
# Genuine thread boundaries inside the room package (ADR-007): everything else is actor-owned.
ROOM_LOCK_ALLOWLIST = {"AdmissionGuard.kt", "QueuePreparationFence.kt", "SessionJobRegistry.kt"}


def check_actor_ownership() -> None:
    """Room state is owned by the actor: no new lock or concurrent map may hide a race there."""
    root = ROOT / "app/src/main/java/com/darius/unison/room"
    for path in sorted(root.rglob("*.kt")):
        if path.name in ROOM_LOCK_ALLOWLIST:
            continue
        require(ROOM_LOCK_PATTERN.search(path.read_text(errors="ignore")) is None,
                f"Room file adds a lock or concurrent map instead of routing through the actor: "
                f"{path.relative_to(ROOT).as_posix()}")


def main() -> int:
    try:
        check_secrets()
        version_name = check_versions()
        check_release_records(version_name)
        check_workflows()
        check_contracts()
        check_manifest()
        check_core_purity()
        check_actor_ownership()
    except AssertionError as error:
        print(str(error), file=sys.stderr)
        return 1
    print("SOURCE_TREE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
