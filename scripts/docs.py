#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import html as html_lib
import os
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
GENERATED_DIR = DOCS / "90-generated"
OUTPUT = GENERATED_DIR / "010-waiotech.md"
PDF_OUTPUT = GENERATED_DIR / "020-waiotech.pdf"
PDF_ASSETS = DOCS / "pdf"
PDF_TEMPLATE = PDF_ASSETS / "template.html"
PDF_STYLES = PDF_ASSETS / "theme.css"
PDF_REQUIREMENTS = PDF_ASSETS / "requirements.txt"

NUMBERED_COMPONENT = re.compile(r"^(\d+)-(.+)$")
MARKDOWN_LINK = re.compile(r"(?<!!)\[([^\]]+)\]\(([^)]+)\)")
H1 = re.compile(r"^#\s+(.+?)\s*$", re.MULTILINE)

PROHIBITED_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("Q&A label", re.compile(r"\*\*(?:Answer|Decision):\*\*", re.IGNORECASE)),
    ("question heading", re.compile(r"^#{1,6}\s+.*\?\s*$", re.MULTILINE)),
    ("document ID metadata", re.compile(r"^\*\*Document ID:\*\*", re.MULTILINE | re.IGNORECASE)),
    ("documentation class metadata", re.compile(r"^\*\*Documentation class:\*\*", re.MULTILINE | re.IGNORECASE)),
    ("authority owner metadata", re.compile(r"^\*\*Authority owner:\*\*", re.MULTILINE | re.IGNORECASE)),
    ("canonical status metadata", re.compile(r"^\*\*Status:\*\*\s*Canonical", re.MULTILINE | re.IGNORECASE)),
    ("placeholder", re.compile(r"\b(?:TODO|FIXME|TBD)\b", re.IGNORECASE)),
    ("delivery-status language", re.compile(r"\b(?:for now|initially|eventually|upcoming|phase one|work in progress|future support|percentage complete)\b", re.IGNORECASE)),
)

SECTION_INFO: dict[str, tuple[str, str, str, str]] = {
    "overview": (
        "Overview",
        "Waiotech documentation library and canonical reading order.",
        "overview",
        "00",
    ),
    "00-governance": (
        "Governance",
        "Authority, precedence, documentation discipline, and change control.",
        "governance",
        "01",
    ),
    "10-product": (
        "Product",
        "Canonical product meaning, domain rules, lifecycles, evidence, and boundaries.",
        "product",
        "02",
    ),
    "20-engineering": (
        "Engineering",
        "Technical guarantees that preserve Product Authority in implementation and operation.",
        "engineering",
        "03",
    ),
    "30-experience": (
        "Experience",
        "Observable contracts for product surfaces, field execution, help, and learning.",
        "experience",
        "04",
    ),
    "70-reference": (
        "Reference",
        "Consolidated terminology, product model, and lifecycle references.",
        "reference",
        "05",
    ),
}
SECTION_ORDER = tuple(SECTION_INFO)


@dataclass(frozen=True)
class Source:
    path: Path
    relative: PurePosixPath
    title: str
    content: str


def fail(message: str) -> None:
    print(f"docs: {message}", file=sys.stderr)


def numeric_component_key(component: str) -> tuple[int, str]:
    stem = component[:-3] if component.endswith(".md") else component
    match = NUMBERED_COMPONENT.match(stem)
    if not match:
        raise ValueError(component)
    return int(match.group(1)), match.group(2)


def source_sort_key(path: PurePosixPath) -> tuple[tuple[int, str], ...]:
    if path == PurePosixPath("000-index.md"):
        return ((-1, "index"),)
    return tuple(numeric_component_key(part) for part in path.parts)


def anchor_for(path: PurePosixPath) -> str:
    value = re.sub(r"[^a-z0-9]+", "-", str(path).lower()).strip("-")
    return f"doc-{value}"


def discover_sources() -> tuple[list[Source], list[str]]:
    errors: list[str] = []
    if not DOCS.is_dir():
        return [], ["docs/ does not exist"]

    paths = [
        path
        for path in DOCS.rglob("*.md")
        if GENERATED_DIR not in path.parents
    ]

    sources: list[Source] = []
    for path in paths:
        relative = PurePosixPath(path.relative_to(DOCS).as_posix())
        try:
            source_sort_key(relative)
        except ValueError as exc:
            errors.append(f"unnumbered path component in {relative}: {exc.args[0]}")
            continue

        content = path.read_text(encoding="utf-8")
        title_match = H1.search(content)
        if not title_match:
            errors.append(f"missing H1 title: {relative}")
            continue
        if content[: title_match.start()].strip():
            errors.append(f"content appears before H1 title: {relative}")
        sources.append(Source(path, relative, title_match.group(1).strip(), content.rstrip() + "\n"))

    sources.sort(key=lambda source: source_sort_key(source.relative))
    return sources, errors


def validate_indexes(sources: list[Source]) -> list[str]:
    errors: list[str] = []
    source_paths = {source.relative for source in sources}
    directories: set[PurePosixPath] = {PurePosixPath(".")}
    for source in sources:
        parent = source.relative.parent
        while str(parent) not in ("", "."):
            directories.add(parent)
            parent = parent.parent
    for directory in sorted(directories, key=lambda item: str(item)):
        expected = PurePosixPath("000-index.md") if str(directory) == "." else directory / "000-index.md"
        if expected not in source_paths:
            errors.append(f"missing index: {expected}")
    return errors


def validate_content(sources: list[Source]) -> list[str]:
    errors: list[str] = []
    for source in sources:
        text = source.content
        for label, pattern in PROHIBITED_PATTERNS:
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(f"{source.relative}:{line}: prohibited {label}")
        if re.search(r"^#{1,6}\s*$", text, re.MULTILINE):
            errors.append(f"{source.relative}: empty heading")
        empty_section = re.search(r"^(#{1,6} .+)\n\n(?=#{1,6} )", text, re.MULTILINE)
        if empty_section:
            line = text.count("\n", 0, empty_section.start()) + 1
            errors.append(f"{source.relative}:{line}: empty section")
        if "\n## Related documents\n\n## " in text:
            errors.append(f"{source.relative}: Related documents must be the final section")
    return errors


def parse_link_destination(raw: str) -> str:
    value = raw.strip()
    if value.startswith("<") and ">" in value:
        return value[1 : value.index(">")]
    # Markdown permits an optional quoted title after whitespace.
    return value.split(maxsplit=1)[0]


def resolve_markdown_target(source: Source, destination: str) -> PurePosixPath | None:
    if not destination or destination.startswith(("#", "http://", "https://", "mailto:", "tel:", "data:")):
        return None
    path_part = destination.split("#", 1)[0]
    if not path_part.lower().endswith(".md"):
        return None
    normalized = os.path.normpath(str(source.relative.parent / PurePosixPath(path_part))).replace("\\", "/")
    return PurePosixPath(normalized)


def validate_links(sources: list[Source]) -> list[str]:
    errors: list[str] = []
    source_paths = {source.relative for source in sources}
    for source in sources:
        for match in MARKDOWN_LINK.finditer(source.content):
            destination = parse_link_destination(match.group(2))
            target = resolve_markdown_target(source, destination)
            if target is not None and target not in source_paths:
                line = source.content.count("\n", 0, match.start()) + 1
                errors.append(f"{source.relative}:{line}: unresolved link to {destination}")
    return errors


def validate(sources: list[Source], discovery_errors: list[str]) -> list[str]:
    errors = list(discovery_errors)
    errors.extend(validate_indexes(sources))
    errors.extend(validate_content(sources))
    errors.extend(validate_links(sources))
    return sorted(set(errors))


def source_digest(sources: list[Source]) -> str:
    digest = hashlib.sha256()
    for source in sources:
        digest.update(str(source.relative).encode("utf-8"))
        digest.update(b"\0")
        digest.update(source.content.encode("utf-8"))
        digest.update(b"\0")
    return digest.hexdigest()


def rewrite_links(source: Source, content: str, anchors: dict[PurePosixPath, str]) -> str:
    def replace(match: re.Match[str]) -> str:
        label, raw = match.group(1), match.group(2)
        destination = parse_link_destination(raw)
        target = resolve_markdown_target(source, destination)
        if target is None or target not in anchors:
            return match.group(0)
        return f"[{label}](#{anchors[target]})"

    return MARKDOWN_LINK.sub(replace, content)


def render_combined(sources: list[Source]) -> str:
    anchors = {source.relative: anchor_for(source.relative) for source in sources}
    digest = source_digest(sources)

    lines: list[str] = [
        "# Waiotech documentation",
        "",
        "> **Generated file.** Do not edit this file directly. Run `just docs` after changing maintained documentation.",
        "",
        f"Source digest: `{digest}`",
        "",
        "## Table of contents",
        "",
    ]

    for source in sources:
        depth = max(0, len(source.relative.parts) - 1)
        indent = "  " * depth
        lines.append(f"{indent}- [{source.title}](#{anchors[source.relative]})")

    lines.extend(["", "## Ordered source files", ""])
    for index, source in enumerate(sources, start=1):
        lines.append(f"{index}. `{source.relative}`")

    for source in sources:
        rewritten = rewrite_links(source, source.content, anchors).rstrip()
        lines.extend([
            "",
            "---",
            "",
            f'<a id="{anchors[source.relative]}"></a>',
            "",
            f"> Source: `{source.relative}`",
            "",
            rewritten,
        ])

    return "\n".join(lines).rstrip() + "\n"


def section_key_for(source: Source) -> str:
    if source.relative == PurePosixPath("000-index.md"):
        return "overview"
    first = source.relative.parts[0]
    return first if first in SECTION_INFO else "overview"


def grouped_sources(sources: list[Source]) -> dict[str, list[Source]]:
    groups = {key: [] for key in SECTION_ORDER}
    for source in sources:
        groups.setdefault(section_key_for(source), []).append(source)
    return groups


def render_pdf_toc_group(
    section_key: str,
    sources: list[Source],
    anchors: dict[PurePosixPath, str],
    *,
    title_suffix: str = "",
) -> str:
    if not sources:
        return ""
    title = SECTION_INFO[section_key][0] + title_suffix
    items: list[str] = []
    for source in sources:
        depth = max(1, min(4, len(source.relative.parts)))
        items.append(
            f'<li class="depth-{depth}"><a class="toc-link" href="#{anchors[source.relative]}">'
            f"{html_lib.escape(source.title)}</a></li>"
        )
    return (
        '<section class="toc-group">'
        f"<h2>{html_lib.escape(title)}</h2>"
        f'<ul class="toc-list">{"".join(items)}</ul>'
        "</section>"
    )


def render_pdf_toc(sources: list[Source], anchors: dict[PurePosixPath, str]) -> str:
    groups = grouped_sources(sources)
    product = groups.get("10-product", [])
    engineering = groups.get("20-engineering", [])
    product_split = (product[:23], product[23:])
    engineering_split = (engineering[:23], engineering[23:])

    page_columns = (
        (
            render_pdf_toc_group("overview", groups.get("overview", []), anchors)
            + render_pdf_toc_group("00-governance", groups.get("00-governance", []), anchors),
            render_pdf_toc_group("10-product", product_split[0], anchors),
        ),
        (
            render_pdf_toc_group("10-product", product_split[1], anchors, title_suffix=" - continued"),
            render_pdf_toc_group("20-engineering", engineering_split[0], anchors),
        ),
        (
            render_pdf_toc_group("20-engineering", engineering_split[1], anchors, title_suffix=" - continued"),
            render_pdf_toc_group("30-experience", groups.get("30-experience", []), anchors)
            + render_pdf_toc_group("70-reference", groups.get("70-reference", []), anchors),
        ),
    )

    pages: list[str] = []
    for page_number, (left, right) in enumerate(page_columns, start=1):
        heading = "Table of contents" if page_number == 1 else "Table of contents - continued"
        intro = (
            '<p class="front-intro">The library is ordered by source path. Page numbers and links are generated from the final pagination.</p>'
            if page_number == 1
            else ""
        )
        pages.append(
            '<section class="front-matter">'
            f"<h1>{heading}</h1>"
            f"{intro}"
            '<div class="toc-grid">'
            f'<div class="toc-column">{left}</div>'
            f'<div class="toc-column">{right}</div>'
            "</div>"
            "</section>"
        )
    return "".join(pages)


def render_pdf_source_list(sources: list[Source]) -> str:
    items = "".join(
        f"<li>{html_lib.escape(str(source.relative))}</li>" for source in sources
    )
    return (
        '<section class="front-matter">'
        '<h1>Ordered source files</h1>'
        '<p class="front-intro">These maintained Markdown files are the deterministic source set used for this document.</p>'
        f'<div class="source-columns"><ol class="source-list">{items}</ol></div>'
        "</section>"
    )


def render_pdf_cover(sources: list[Source], digest: str) -> str:
    return f"""
<section class="cover">
  <div class="cover-brand"><span class="cover-mark">W</span><span>WAIOTECH</span></div>
  <div class="cover-kicker">Industrial operations &amp; maintenance</div>
  <div class="cover-rule"></div>
  <h1>Canonical<br>Documentation<br>Library</h1>
  <p class="cover-subtitle">Product, Engineering, Experience, and Reference</p>
  <div class="cover-meta">
    <div><div class="cover-meta-label">Document</div><div class="cover-meta-value">Waiotech Documentation</div></div>
    <div><div class="cover-meta-label">Format</div><div class="cover-meta-value">Styled PDF reference</div></div>
    <div><div class="cover-meta-label">Source set</div><div class="cover-meta-value">{len(sources)} ordered Markdown documents<br>Digest {html_lib.escape(digest[:12])}</div></div>
  </div>
  <div class="cover-note">Generated reference output. Edit the maintained source documents, not this PDF.</div>
</section>
"""


def render_pdf_divider(section_key: str, count: int) -> str:
    title, description, css_class, number = SECTION_INFO[section_key]
    noun = "document" if count == 1 else "documents"
    return f"""
<section class="divider {css_class}">
  <div class="divider-number">Section {number}</div>
  <div class="divider-rule"></div>
  <h1>{html_lib.escape(title)}</h1>
  <p>{html_lib.escape(description)}</p>
  <div class="divider-count">{count} ordered source {noun}</div>
</section>
"""


def markdown_renderer():
    try:
        from markdown_it import MarkdownIt
    except ImportError as exc:
        raise RuntimeError(
            f"PDF dependency missing: markdown-it-py. Run `python3 -m pip install -r {PDF_REQUIREMENTS.relative_to(ROOT)}`."
        ) from exc

    renderer = MarkdownIt("commonmark", {"html": False, "linkify": False, "typographer": False})
    renderer.enable("table")
    renderer.enable("strikethrough")
    return renderer


def render_pdf_document(
    source: Source,
    anchors: dict[PurePosixPath, str],
    renderer,
) -> str:
    section_key = section_key_for(source)
    css_class = SECTION_INFO[section_key][2]
    rewritten = rewrite_links(source, source.content, anchors)
    content_html = renderer.render(rewritten)
    return (
        f'<article class="document {css_class}" id="{anchors[source.relative]}">'
        f'<div class="source-path">Source: {html_lib.escape(str(source.relative))}</div>'
        f"{content_html}"
        "</article>"
    )


def render_pdf_html(sources: list[Source]) -> str:
    missing = [path for path in (PDF_TEMPLATE, PDF_STYLES) if not path.is_file()]
    if missing:
        names = ", ".join(str(path.relative_to(ROOT)) for path in missing)
        raise RuntimeError(f"missing PDF presentation assets: {names}")

    anchors = {source.relative: anchor_for(source.relative) for source in sources}
    digest = source_digest(sources)
    groups = grouped_sources(sources)
    renderer = markdown_renderer()

    body: list[str] = [
        render_pdf_cover(sources, digest),
        render_pdf_toc(sources, anchors),
        render_pdf_source_list(sources),
    ]

    for source in groups.get("overview", []):
        body.append(render_pdf_document(source, anchors, renderer))

    for section_key in SECTION_ORDER:
        if section_key == "overview":
            continue
        group = groups.get(section_key, [])
        if not group:
            continue
        body.append(render_pdf_divider(section_key, len(group)))
        body.extend(render_pdf_document(source, anchors, renderer) for source in group)

    template = PDF_TEMPLATE.read_text(encoding="utf-8")
    styles = PDF_STYLES.read_text(encoding="utf-8")
    document = template.replace("{{DOCUMENT_TITLE}}", "Waiotech Canonical Documentation Library")
    document = document.replace("{{STYLES}}", styles)
    document = document.replace("{{DOCUMENT_BODY}}", "\n".join(body))
    if "{{" in document or "}}" in document:
        raise RuntimeError("unresolved placeholder in PDF HTML template")
    return document


def generate_pdf(sources: list[Source]) -> int:
    try:
        from weasyprint import HTML
    except ImportError as exc:
        fail(
            f"PDF dependency missing: WeasyPrint. Run `python3 -m pip install -r {PDF_REQUIREMENTS.relative_to(ROOT)}`."
        )
        return 1

    try:
        document_html = render_pdf_html(sources)
    except RuntimeError as exc:
        fail(str(exc))
        return 1

    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            prefix=".waiotech-",
            suffix=".pdf",
            dir=GENERATED_DIR,
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)

        HTML(string=document_html, base_url=str(ROOT)).write_pdf(str(temporary_path))
        if temporary_path.stat().st_size < 1024 or temporary_path.read_bytes()[:5] != b"%PDF-":
            raise RuntimeError("renderer did not produce a valid PDF file")
        temporary_path.replace(PDF_OUTPUT)
    except Exception as exc:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
        fail(f"PDF generation failed: {exc}")
        return 1

    size_mib = PDF_OUTPUT.stat().st_size / (1024 * 1024)
    print(f"Generated {PDF_OUTPUT.relative_to(ROOT)} ({size_mib:.1f} MiB)")
    return 0


def generate(*, pdf: bool = False) -> int:
    sources, discovery_errors = discover_sources()
    errors = validate(sources, discovery_errors)
    if errors:
        for error in errors:
            fail(error)
        return 1
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    output = render_combined(sources)
    OUTPUT.write_text(output, encoding="utf-8")
    print(f"Generated {OUTPUT.relative_to(ROOT)} from {len(sources)} source files")
    if pdf:
        return generate_pdf(sources)
    return 0


def verify() -> int:
    sources, discovery_errors = discover_sources()
    errors = validate(sources, discovery_errors)
    if errors:
        for error in errors:
            fail(error)
        return 1
    expected = render_combined(sources)
    if not OUTPUT.exists():
        fail(f"missing generated file: {OUTPUT.relative_to(ROOT)}")
        return 1
    actual = OUTPUT.read_text(encoding="utf-8")
    if actual != expected:
        fail("generated documentation is stale; run `just docs`")
        return 1
    print(f"Verified {len(sources)} source files and generated output")
    return 0


def check() -> int:
    sources, discovery_errors = discover_sources()
    errors = validate(sources, discovery_errors)
    if errors:
        for error in errors:
            fail(error)
        return 1
    print(f"Validated {len(sources)} documentation source files")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and generate Waiotech documentation")
    parser.add_argument("command", choices=("generate", "verify", "check"))
    parser.add_argument(
        "--pdf",
        action="store_true",
        help="also generate docs/90-generated/020-waiotech.pdf (generate command only)",
    )
    args = parser.parse_args()
    if args.pdf and args.command != "generate":
        parser.error("--pdf is supported only with the generate command")
    return {
        "generate": lambda: generate(pdf=args.pdf),
        "verify": verify,
        "check": check,
    }[args.command]()


if __name__ == "__main__":
    raise SystemExit(main())
