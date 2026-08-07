#!/usr/bin/env python3
"""Generate closed browser action identifiers from module-owned Domain actions."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "server"
DASHBOARD_OUT = ROOT / "dashboard/src/actions/generated/action-contract.ts"
MANIFEST_OUT = ROOT / "server/artifacts/action-contract.json"
OUTPUTS = (DASHBOARD_OUT, MANIFEST_OUT)
CONSUMER_ROOTS = (ROOT / "dashboard/src",)
ACTION_REFERENCE = re.compile(r"\bActionCode\.([A-Z][A-Z0-9_]*)\b")


def _enum_member(value: str) -> str:
    member = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").upper()
    if not member or member[0].isdigit():
        member = f"VALUE_{member}"
    return member


def _definitions():
    sys.path.insert(0, str(SERVER))
    from app.domain.action_catalogue import CANONICAL_ACTION_DEFINITIONS

    return CANONICAL_ACTION_DEFINITIONS


def _validate_enum_members(definitions) -> dict[str, str]:
    members: dict[str, str] = {}
    for definition in definitions:
        member = _enum_member(definition.code)
        previous = members.get(member)
        if previous is not None and previous != definition.code:
            raise RuntimeError(
                f"Action enum collision: {previous!r} and "
                f"{definition.code!r} -> {member}"
            )
        members[member] = definition.code
    return members


def _validate_consumers(members: dict[str, str]) -> None:
    unknown: list[str] = []
    for root in CONSUMER_ROOTS:
        for path in sorted(root.rglob("*.ts")) + sorted(root.rglob("*.tsx")):
            if path == DASHBOARD_OUT:
                continue
            content = path.read_text(encoding="utf-8")
            for member in sorted(set(ACTION_REFERENCE.findall(content))):
                if member not in members:
                    unknown.append(f"{path.relative_to(ROOT)}: ActionCode.{member}")
    if unknown:
        formatted = "\n".join(f"  {item}" for item in unknown)
        raise RuntimeError(
            "Action consumers reference identifiers absent from the canonical "
            f"Domain action source:\n{formatted}"
        )


def _typescript_content(definitions) -> str:
    entries = "\n".join(
        f'  {_enum_member(definition.code)}: "{definition.code}",'
        for definition in definitions
    )
    return f"""// Generated from module-owned server Domain actions. Do not edit.
// Run `just generate-action-contracts`.

export const ActionCode = {{
{entries}
}} as const;

export type ActionCode = (typeof ActionCode)[keyof typeof ActionCode];

export const ACTION_CODES = Object.values(ActionCode) as readonly ActionCode[];

const ACTION_CODE_SET: ReadonlySet<string> = new Set(ACTION_CODES);

export function isActionCode(value: string): value is ActionCode {{
  return ACTION_CODE_SET.has(value);
}}
"""


def _manifest_content(definitions) -> str:
    payload = {
        "schema_version": 2,
        "source": "module-owned server Domain actions",
        "actions": [
            {
                "code": definition.code,
                "constant_name": definition.constant_name,
                "owner_module": definition.owner_module,
            }
            for definition in definitions
        ],
    }
    return json.dumps(payload, indent=2, sort_keys=True) + "\n"


def _rendered_outputs() -> dict[Path, str]:
    definitions = _definitions()
    members = _validate_enum_members(definitions)
    _validate_consumers(members)
    typescript = _typescript_content(definitions)
    return {
        DASHBOARD_OUT: typescript,
        MANIFEST_OUT: _manifest_content(definitions),
    }


def _generate(rendered: dict[Path, str]) -> None:
    for path in OUTPUTS:
        path.unlink(missing_ok=True)
    for path, content in rendered.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)}")


def _verify(rendered: dict[Path, str]) -> int:
    stale = False
    with tempfile.TemporaryDirectory(prefix="waiotech-action-contract-") as temp:
        temp_root = Path(temp)
        for path, content in rendered.items():
            temporary = temp_root / path.relative_to(ROOT)
            temporary.parent.mkdir(parents=True, exist_ok=True)
            temporary.write_text(content, encoding="utf-8")
            if not path.exists() or path.read_bytes() != temporary.read_bytes():
                print(
                    f"Action contract drift: regenerate {path.relative_to(ROOT)}",
                    file=sys.stderr,
                )
                stale = True
    return 1 if stale else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="Generate in a temporary directory and fail when committed output differs",
    )
    args = parser.parse_args()
    rendered = _rendered_outputs()
    if args.check:
        return _verify(rendered)
    _generate(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
