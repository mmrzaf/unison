#!/usr/bin/env python3
"""Generate the Waiotech browser API clients from the canonical OpenAPI document."""
from __future__ import annotations

import argparse
import json
import re
import shutil
import tempfile
from pathlib import Path
from typing import Any

HEADER = '''/* eslint-disable */
/* tslint:disable */
// @ts-nocheck
/*
 * ---------------------------------------------------------------
 * ## THIS FILE IS GENERATED FROM WAIOTECH OPENAPI              ##
 * ## DO NOT EDIT GENERATED OUTPUT                              ##
 * ---------------------------------------------------------------
 */

'''
HTTP_METHODS = {"get", "put", "post", "delete", "patch", "options", "head", "trace"}


def words(value: str) -> list[str]:
    return [part for part in re.split(r"[^A-Za-z0-9]+", value) if part]


def pascal(value: str) -> str:
    result = "".join(part[:1].upper() + part[1:].lower() for part in words(value))
    if not result:
        return "Value"
    return f"Value{result}" if result[0].isdigit() else result


def camel(value: str) -> str:
    name = pascal(value)
    return name[:1].lower() + name[1:]


def quoted(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False)


def ref_name(ref: str) -> str:
    return ref.rsplit("/", 1)[-1]


def array_item_type(value: str) -> str:
    if " | " in value or " & " in value:
        return f"({value})"
    return value


def ts_type(schema: dict[str, Any] | None, refs: set[str] | None = None, indent: int = 0) -> str:
    schema = schema or {}
    if "$ref" in schema:
        name = ref_name(schema["$ref"])
        if refs is not None:
            refs.add(name)
        return name
    if "const" in schema:
        return quoted(schema["const"])
    if "enum" in schema:
        values = " | ".join(quoted(value) for value in schema["enum"])
        if schema.get("nullable") and "null" not in values:
            values += " | null"
        return values or "never"
    for key in ("anyOf", "oneOf"):
        if key in schema:
            types: list[str] = []
            for item in schema[key]:
                item_type = ts_type(item, refs, indent)
                if item_type not in types:
                    types.append(item_type)
            return " | ".join(types) or "unknown"
    if "allOf" in schema:
        types = [ts_type(item, refs, indent) for item in schema["allOf"]]
        own = {key: value for key, value in schema.items() if key not in {"allOf", "title", "description"}}
        if own:
            types.append(ts_type(own, refs, indent))
        return " & ".join(item for item in types if item != "unknown") or "unknown"

    schema_type = schema.get("type")
    if isinstance(schema_type, list):
        return " | ".join(ts_type({**schema, "type": item}, refs, indent) for item in schema_type)
    if schema_type == "null":
        return "null"
    if schema_type in {"integer", "number"}:
        return "number"
    if schema_type == "boolean":
        return "boolean"
    if schema_type == "string":
        return "File" if schema.get("format") == "binary" else "string"
    if schema_type == "array":
        item = ts_type(schema.get("items", {}), refs, indent)
        return f"{array_item_type(item)}[]"
    if schema_type == "object" or "properties" in schema or "additionalProperties" in schema:
        properties = schema.get("properties", {})
        required = set(schema.get("required", []))
        parts: list[str] = []
        if properties:
            pad = " " * indent
            child_pad = " " * (indent + 2)
            lines = ["{"]
            for name, prop in properties.items():
                optional = "" if name in required else "?"
                prop_name = name if re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name) else quoted(name)
                lines.append(f"{child_pad}{prop_name}{optional}: {ts_type(prop, refs, indent + 2)};")
            lines.append(f"{pad}}}")
            parts.append("\n".join(lines))
        additional = schema.get("additionalProperties")
        if additional is True:
            parts.append("Record<string, unknown>")
        elif isinstance(additional, dict):
            parts.append(f"Record<string, {ts_type(additional, refs, indent)}>")
        if not parts:
            return "Record<string, never>" if additional is False else "Record<string, unknown>"
        return " & ".join(parts)
    return "any" if not schema else "unknown"


def render_enum(name: str, schema: dict[str, Any]) -> str:
    lines = [f"export enum {name} {{"]
    used: set[str] = set()
    for value in schema["enum"]:
        member = pascal(str(value))
        candidate = member
        suffix = 2
        while candidate in used:
            candidate = f"{member}{suffix}"
            suffix += 1
        used.add(candidate)
        lines.append(f"  {candidate} = {quoted(value)},")
    lines.append("}")
    return "\n".join(lines)


def render_schema(name: str, schema: dict[str, Any]) -> str:
    description = schema.get("description")
    comment = f"/** {description.strip()} */\n" if isinstance(description, str) and description.strip() else f"/** {name} */\n"
    if schema.get("type") == "string" and "enum" in schema:
        return comment + render_enum(name, schema)
    if (schema.get("type") == "object" or "properties" in schema) and not any(key in schema for key in ("allOf", "oneOf", "anyOf")):
        required = set(schema.get("required", []))
        lines = [f"export interface {name} {{"]
        for prop_name, prop in schema.get("properties", {}).items():
            optional = "" if prop_name in required else "?"
            key = prop_name if re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", prop_name) else quoted(prop_name)
            lines.append(f"  {key}{optional}: {ts_type(prop, set(), 2)};")
        if not schema.get("properties") and schema.get("additionalProperties"):
            additional = schema["additionalProperties"]
            value_type = "unknown" if additional is True else ts_type(additional, set(), 2)
            lines.append(f"  [key: string]: {value_type};")
        lines.append("}")
        return comment + "\n".join(lines)
    return comment + f"export type {name} = {ts_type(schema, set(), 0)};"


def render_contracts(
    spec: dict[str, Any], schema_names: set[str] | None = None
) -> str:
    schemas = spec.get("components", {}).get("schemas", {})
    selected = (
        schemas.items()
        if schema_names is None
        else ((name, schema) for name, schema in schemas.items() if name in schema_names)
    )
    return HEADER + "\n\n".join(render_schema(name, schema) for name, schema in selected) + "\n"


def parameter_schema(parameter: dict[str, Any]) -> dict[str, Any]:
    return parameter.get("schema") or {}


def collect_operation_refs(operation: dict[str, Any]) -> set[str]:
    refs: set[str] = set()
    for parameter in operation.get("parameters", []):
        ts_type(parameter_schema(parameter), refs)
    for media in operation.get("requestBody", {}).get("content", {}).values():
        ts_type(media.get("schema", {}), refs)
    for response in operation.get("responses", {}).values():
        for media in response.get("content", {}).values():
            ts_type(media.get("schema", {}), refs)
    refs.discard("ApiErrorOut")
    return refs


def success_response(operation: dict[str, Any], refs: set[str]) -> tuple[str, bool]:
    responses = operation.get("responses", {})
    successful = sorted((code, response) for code, response in responses.items() if str(code).startswith("2"))
    if not successful:
        return "void", False
    _, response = successful[0]
    content = response.get("content", {})
    if not content:
        return "void", False
    media_type, media = next(iter(content.items()))
    schema = media.get("schema", {})
    if not schema:
        return "any", media_type == "application/json"
    return ts_type(schema, refs), media_type == "application/json"


def error_response(operation: dict[str, Any], refs: set[str]) -> str:
    for code, response in operation.get("responses", {}).items():
        if str(code).startswith("2"):
            continue
        for media in response.get("content", {}).values():
            schema = media.get("schema", {})
            if schema:
                return ts_type(schema, refs)
    return "any"


def operation_security(spec: dict[str, Any], operation: dict[str, Any]) -> bool:
    security = operation.get("security", spec.get("security", []))
    return bool(security)


def render_query(parameters: list[dict[str, Any]], refs: set[str]) -> tuple[str | None, bool]:
    query = [parameter for parameter in parameters if parameter.get("in") == "query"]
    if not query:
        return None, False
    required_query = any(parameter.get("required") for parameter in query)
    lines = ["{"]
    for parameter in query:
        name = parameter["name"]
        optional = "" if parameter.get("required") else "?"
        key = name if re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name) else quoted(name)
        lines.append(f"      {key}{optional}: {ts_type(parameter_schema(parameter), refs)};")
    lines.append("    }")
    return "\n".join(lines), required_query


def render_operation(spec: dict[str, Any], path: str, method: str, operation: dict[str, Any]) -> tuple[str, set[str], bool]:
    refs = collect_operation_refs(operation)
    parameters = operation.get("parameters", [])
    path_params = [parameter for parameter in parameters if parameter.get("in") == "path"]
    query_type, query_required = render_query(parameters, refs)
    request_body = operation.get("requestBody")
    response_type, json_response = success_response(operation, refs)
    error_type = error_response(operation, refs)
    name = camel(operation["operationId"])
    title = pascal(operation["operationId"])
    summary = operation.get("summary", title)
    tag = operation.get("tags", ["Default"])[0]
    secure = operation_security(spec, operation)

    arguments: list[str] = []
    template_path = path
    for parameter in path_params:
        raw_name = parameter["name"]
        argument_name = camel(raw_name)
        arguments.append(f"    {argument_name}: {ts_type(parameter_schema(parameter), refs)},")
        template_path = template_path.replace("{" + raw_name + "}", "${" + argument_name + "}")
    if query_type is not None:
        token = "query" if query_required else "query?"
        arguments.append(f"    {token}: {query_type},")
    body_media_type = None
    if request_body:
        content = request_body.get("content", {})
        if content:
            body_media_type, media = next(iter(content.items()))
            arguments.append(f"    data{'?' if not request_body.get('required', False) else ''}: {ts_type(media.get('schema', {}), refs)},")
    arguments.append("    params: RequestParams = {},")
    if len(arguments) == 1:
        signature = "(params: RequestParams = {})"
    elif len(arguments) <= 2 and all("\n" not in arg for arg in arguments):
        signature = "(" + " ".join(arg.strip() for arg in arguments) + ")"
    else:
        signature = "(\n" + "\n".join(arguments) + "\n  )"

    imports_content_type = body_media_type == "application/json"
    lines = [
        "  /**",
        "   * No description",
        "   *",
        f"   * @tags {tag}",
        f"   * @name {title}",
        f"   * @summary {summary}",
        f"   * @request {method.upper()}:{path}",
    ]
    if secure:
        lines.append("   * @secure")
    lines.extend(["   */", f"  {name} = {signature} =>", f"    this.request<{response_type}, {error_type}>({{"])
    lines.append(f"      path: `{template_path}`,")
    lines.append(f"      method: {quoted(method.upper())},")
    if query_type is not None:
        lines.append("      query: query,")
    if request_body:
        lines.append("      body: data,")
    if secure:
        lines.append("      secure: true,")
    if body_media_type == "application/json":
        lines.append("      type: ContentType.Json,")
    if json_response:
        lines.append('      format: "json",')
    lines.extend(["      ...params,", "    });"])
    return "\n".join(lines), refs, imports_content_type


def tag_class_name(tag: str) -> str:
    return pascal(tag)


def _tag_selected(
    tag: str,
    tags: set[str] | None,
    excluded_tags: set[str] | None,
) -> bool:
    return (tags is None or tag in tags) and (
        excluded_tags is None or tag not in excluded_tags
    )


def render_services(
    spec: dict[str, Any],
    tags: set[str] | None = None,
    excluded_tags: set[str] | None = None,
) -> dict[str, str]:
    grouped: dict[str, list[tuple[str, str, dict[str, Any]]]] = {}
    for path, path_item in spec.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS or not isinstance(operation, dict):
                continue
            tag = operation.get("tags", ["Default"])[0]
            if not _tag_selected(tag, tags, excluded_tags):
                continue
            grouped.setdefault(tag, []).append((path, method, operation))

    output: dict[str, str] = {}
    for tag, operations in grouped.items():
        blocks: list[str] = []
        refs: set[str] = set()
        content_type = False
        for path, method, operation in operations:
            block, operation_refs, operation_content_type = render_operation(spec, path, method, operation)
            blocks.append(block)
            refs.update(operation_refs)
            content_type = content_type or operation_content_type
        imports = ""
        if refs:
            imports = "import {\n" + "\n".join(f"  {name}," for name in sorted(refs)) + '\n} from "./data-contracts";\n'
        http_imports = ["HttpClient", "RequestParams"]
        if content_type:
            http_imports.insert(0, "ContentType")
        imports += f'import {{ {", ".join(http_imports)} }} from "./http-client";\n\n'
        class_name = tag_class_name(tag)
        body = "\n".join(blocks)
        output[f"{class_name}.ts"] = (
            HEADER
            + imports
            + f"export class {class_name}<\n  SecurityDataType = unknown,\n> extends HttpClient<SecurityDataType> {{\n"
            + body
            + "\n}\n"
        )
    return output


def _schema_refs(value: Any) -> set[str]:
    refs: set[str] = set()
    if isinstance(value, dict):
        ref = value.get("$ref")
        if isinstance(ref, str):
            refs.add(ref_name(ref))
        for child in value.values():
            refs.update(_schema_refs(child))
    elif isinstance(value, list):
        for child in value:
            refs.update(_schema_refs(child))
    return refs


def reachable_schema_names(
    spec: dict[str, Any],
    tags: set[str] | None,
    excluded_tags: set[str] | None,
) -> set[str]:
    schemas = spec.get("components", {}).get("schemas", {})
    selected: set[str] = set()
    for path_item in spec.get("paths", {}).values():
        if not isinstance(path_item, dict):
            continue
        for method, operation in path_item.items():
            if method not in HTTP_METHODS or not isinstance(operation, dict):
                continue
            tag = operation.get("tags", ["Default"])[0]
            if _tag_selected(tag, tags, excluded_tags):
                selected.update(_schema_refs(operation))

    pending = list(selected)
    while pending:
        name = pending.pop()
        schema = schemas.get(name)
        if not isinstance(schema, dict):
            continue
        for dependency in _schema_refs(schema):
            if dependency not in selected:
                selected.add(dependency)
                pending.append(dependency)
    return selected


def generate(
    spec_path: Path,
    output_dir: Path,
    tags: set[str] | None = None,
    excluded_tags: set[str] | None = None,
) -> None:
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    output_dir.mkdir(parents=True, exist_ok=True)
    for child in output_dir.iterdir():
        if child.is_file():
            child.unlink()
        elif child.is_dir():
            shutil.rmtree(child)
    schema_names = (
        reachable_schema_names(spec, tags, excluded_tags)
        if tags is not None or excluded_tags is not None
        else None
    )
    (output_dir / "data-contracts.ts").write_text(
        render_contracts(spec, schema_names), encoding="utf-8"
    )
    template = Path(__file__).with_name("typescript-api-http-client.ts")
    shutil.copyfile(template, output_dir / "http-client.ts")
    for filename, content in render_services(spec, tags, excluded_tags).items():
        (output_dir / filename).write_text(content, encoding="utf-8")


def directory_files(path: Path) -> list[Path]:
    return sorted(child for child in path.iterdir() if child.is_file())


def verify(
    spec_path: Path,
    output_dir: Path,
    tags: set[str] | None = None,
    excluded_tags: set[str] | None = None,
) -> None:
    with tempfile.TemporaryDirectory(prefix="waiotech-typescript-api-") as temporary:
        expected = Path(temporary)
        generate(spec_path, expected, tags, excluded_tags)
        expected_names = [item.name for item in directory_files(expected)]
        actual_names = [item.name for item in directory_files(output_dir)]
        if expected_names != actual_names:
            raise SystemExit(f"Generated API file set is stale for {output_dir}")
        for expected_file in directory_files(expected):
            actual_file = output_dir / expected_file.name
            if expected_file.read_bytes() != actual_file.read_bytes():
                raise SystemExit(f"Generated API client is stale: {actual_file}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("app_directory", type=Path)
    parser.add_argument("contract", type=Path)
    parser.add_argument(
        "--tag",
        action="append",
        dest="tags",
        help="Generate only operations and reachable schemas for this OpenAPI tag",
    )
    parser.add_argument(
        "--exclude-tag",
        action="append",
        dest="excluded_tags",
        help="Exclude operations and schemas used only by this OpenAPI tag",
    )
    args = parser.parse_args()
    output = args.app_directory.resolve() / "src" / "api" / "generated"
    contract = args.contract.resolve()
    tags = set(args.tags) if args.tags else None
    excluded_tags = set(args.excluded_tags) if args.excluded_tags else None
    if args.mode == "generate":
        generate(contract, output, tags, excluded_tags)
        print(f"generated TypeScript API client: {output}")
    else:
        verify(contract, output, tags, excluded_tags)
        print(f"ok: generated API client matches {args.app_directory.resolve()}")


if __name__ == "__main__":
    main()
