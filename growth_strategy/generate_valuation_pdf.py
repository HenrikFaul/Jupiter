#!/usr/bin/env python3
"""
Generic Software Valuation & Technical Due-Diligence PDF Generator
==================================================================

A fully data-driven renderer for software valuation reports (12 sections):
  1. Executive Summary
  2. Product Reconstruction
  3. Scope Decomposition
  4. Methodology
  5. Team Composition
  6. Effort Estimate
  7. Cost Estimate
  8. Market Comparison
  9. Market Value Estimate
 10. Assumptions and Limitations
 11. Recommended Next Steps
 12. Appendix

Reads:
  - project.json                                  : shared branding + meta
  - data/valuation/<lang>.json (per language)     : full report content

Usage:
    python generate_valuation_pdf.py
    python generate_valuation_pdf.py --lang en
    python generate_valuation_pdf.py --all-languages
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_RIGHT, TA_JUSTIFY
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, HRFlowable,
)

# Make sibling module importable regardless of working directory.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from _fonts import setup_fonts  # noqa: E402

# Register Unicode-capable fonts (fixes Hungarian ő/ű, accents, dashes).
FONTS = setup_fonts()


# =============================================================================
# UI STRINGS (built-in language packs for skeleton only)
# =============================================================================
UI_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "report_subtitle":   "Software Valuation & Technical Due-Diligence Report",
        "header_label":      "Software Valuation Report",
        "toc_heading":       "Table of Contents",
        "page":              "Page",
        "confidential":      "CONFIDENTIAL",
        "footer_note":       "For informational purposes only.",
        "biggest_uncertainty": "Biggest Uncertainty Driver",
        "section_names": [
            "Executive Summary",
            "Product Reconstruction",
            "Scope Decomposition",
            "Methodology",
            "Team Composition",
            "Effort Estimate",
            "Cost Estimate",
            "Market Comparison",
            "Market Value Estimate",
            "Assumptions and Limitations",
            "Recommended Next Steps",
            "Appendix",
        ],
    },
    "hu": {
        "report_subtitle":   "Szoftver Értékelési és Technikai Átvilágítási Jelentés",
        "header_label":      "Szoftver Értékelési Jelentés",
        "toc_heading":       "Tartalomjegyzék",
        "page":              "Oldal",
        "confidential":      "BIZALMAS",
        "footer_note":       "Csak tájékoztató jelleggel.",
        "biggest_uncertainty": "Legnagyobb bizonytalansági tényező",
        "section_names": [
            "Vezetői összefoglaló",
            "Termékrekonstrukció",
            "Hatókör-lebontás",
            "Módszertan",
            "Csapatösszetétel",
            "Ráfordítás-becslés",
            "Költségbecslés",
            "Piaci összehasonlítás",
            "Piaci értékbecslés",
            "Feltételezések és korlátok",
            "Ajánlott következő lépések",
            "Függelék",
        ],
    },
    "de": {
        "report_subtitle":   "Software-Bewertung & Technische Due-Diligence-Bericht",
        "header_label":      "Software-Bewertungsbericht",
        "toc_heading":       "Inhaltsverzeichnis",
        "page":              "Seite",
        "confidential":      "VERTRAULICH",
        "footer_note":       "Nur zu Informationszwecken.",
        "biggest_uncertainty": "Größter Unsicherheitsfaktor",
        "section_names": [
            "Zusammenfassung",
            "Produktrekonstruktion",
            "Aufschlüsselung des Umfangs",
            "Methodik",
            "Teamzusammensetzung",
            "Aufwandsschätzung",
            "Kostenschätzung",
            "Marktvergleich",
            "Marktwertschätzung",
            "Annahmen und Einschränkungen",
            "Empfohlene nächste Schritte",
            "Anhang",
        ],
    },
}


# =============================================================================
# BRANDING
# =============================================================================
DEFAULT_BRAND = {
    "primary":      "#6366F1",
    "primary_dark": "#0F172A",
    "primary_mid":  "#1E293B",
    "accent":       "#818CF8",
    "success":      "#10B981",
    "warning":      "#F59E0B",
    "danger":       "#EF4444",
    "table_header": "#4F46E5",
    "row_zebra_1":  "#F8FAFC",
    "row_zebra_2":  "#EEF2FF",
    "border":       "#C7D2FE",
    "text_body":    "#1E293B",
    "text_muted":   "#64748B",
    "info_bg":      "#E0E7FF",
    "warn_bg":      "#FEF9C3",
    "rule_color":   "#E2E8F0",
}

PAGE_W, PAGE_H = A4
MARGIN = 20 * mm


# =============================================================================
# DATA LOADING
# =============================================================================

def load_json(path: str | Path) -> dict[str, Any]:
    p = Path(path)
    if not p.exists():
        sys.exit(f"ERROR: File not found: {p}")
    try:
        with p.open("r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        sys.exit(f"ERROR: Invalid JSON in {p}: {e}")


def resolve_brand(proj: dict) -> dict:
    merged = {**DEFAULT_BRAND, **(proj.get("brand") or {})}
    return {k: colors.HexColor(v) if isinstance(v, str) and v.startswith("#") else v
            for k, v in merged.items()}


def validate_valuation(content: dict, lang: str) -> None:
    """Ensure required top-level keys exist; missing optional sections are skipped silently."""
    if not isinstance(content, dict):
        sys.exit(f"ERROR: valuation file for '{lang}' must be a JSON object")
    if "executive_summary" not in content:
        sys.exit(f"ERROR: valuation '{lang}' missing required key: executive_summary")


# =============================================================================
# STYLES
# =============================================================================

def make_styles(brand: dict) -> dict[str, ParagraphStyle]:
    WHITE = colors.white
    TEXT_BODY = brand["text_body"]
    TEXT_MUTED = brand["text_muted"]

    return {
        "cover_title": ParagraphStyle("cover_title",
            fontName=FONTS["bold"], fontSize=32, leading=38,
            textColor=WHITE, alignment=TA_LEFT, spaceAfter=8),
        "cover_sub": ParagraphStyle("cover_sub",
            fontName=FONTS["regular"], fontSize=14, leading=20,
            textColor=brand["accent"], alignment=TA_LEFT, spaceAfter=4),
        "cover_meta": ParagraphStyle("cover_meta",
            fontName=FONTS["regular"], fontSize=10, leading=14,
            textColor=colors.HexColor("#94A3B8"), alignment=TA_LEFT, spaceAfter=3),
        "h1": ParagraphStyle("h1",
            fontName=FONTS["bold"], fontSize=18, leading=24,
            textColor=brand["primary"], spaceBefore=18, spaceAfter=8),
        "h2": ParagraphStyle("h2",
            fontName=FONTS["bold"], fontSize=13, leading=18,
            textColor=brand["primary_dark"], spaceBefore=14, spaceAfter=6),
        "h3": ParagraphStyle("h3",
            fontName=FONTS["bold"], fontSize=11, leading=16,
            textColor=brand["primary_mid"], spaceBefore=10, spaceAfter=4),
        "body": ParagraphStyle("body",
            fontName=FONTS["regular"], fontSize=10, leading=15,
            textColor=TEXT_BODY, spaceBefore=2, spaceAfter=4,
            alignment=TA_JUSTIFY),
        "bullet": ParagraphStyle("bullet",
            fontName=FONTS["regular"], fontSize=10, leading=14,
            textColor=TEXT_BODY, leftIndent=16, bulletIndent=6,
            spaceBefore=1, spaceAfter=2),
        "table_header": ParagraphStyle("table_header",
            fontName=FONTS["bold"], fontSize=8.5, leading=12,
            textColor=WHITE, alignment=TA_CENTER),
        "table_cell": ParagraphStyle("table_cell",
            fontName=FONTS["regular"], fontSize=8.5, leading=12,
            textColor=TEXT_BODY, alignment=TA_LEFT),
        "table_cell_c": ParagraphStyle("table_cell_c",
            fontName=FONTS["regular"], fontSize=8.5, leading=12,
            textColor=TEXT_BODY, alignment=TA_CENTER),
        "caption": ParagraphStyle("caption",
            fontName=FONTS["oblique"], fontSize=8, leading=11,
            textColor=TEXT_MUTED, alignment=TA_CENTER, spaceAfter=8),
        "kpi_label": ParagraphStyle("kpi_label",
            fontName=FONTS["bold"], fontSize=9, leading=12,
            textColor=TEXT_MUTED, alignment=TA_CENTER),
        "kpi_value": ParagraphStyle("kpi_value",
            fontName=FONTS["bold"], fontSize=20, leading=24,
            textColor=brand["primary"], alignment=TA_CENTER),
        "toc_item": ParagraphStyle("toc_item",
            fontName=FONTS["regular"], fontSize=10, leading=16,
            textColor=TEXT_BODY, leftIndent=8),
    }


# =============================================================================
# REUSABLE COMPONENTS
# =============================================================================

def kpi_grid(items: list[dict], styles: dict, brand: dict):
    """items: [{"label": "...", "value": "..."}, ...]"""
    if not items:
        return Spacer(1, 0)
    # Adaptive value font size: shrink as more KPIs share the row so values
    # (e.g. "31,435", "2 months") don't wrap awkwardly.
    n = len(items)
    longest = max((len(str(it["value"])) for it in items), default=4)
    value_fs = 20
    if n >= 5 or longest >= 7:
        value_fs = 15
    if n >= 7 or longest >= 11:
        value_fs = 12
    value_style = ParagraphStyle("kpi_value_adaptive", parent=styles["kpi_value"],
                                 fontSize=value_fs, leading=value_fs + 4)
    cells_top = [Paragraph(it["value"], value_style) for it in items]
    cells_bot = [Paragraph(it["label"], styles["kpi_label"]) for it in items]
    col_w = (PAGE_W - 2 * MARGIN) / len(items)
    t = Table([cells_top, cells_bot], colWidths=[col_w] * len(items))
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["row_zebra_2"]),
        ("BOX", (0, 0), (-1, -1), 0.5, brand["border"]),
        ("INNERGRID", (0, 0), (-1, -1), 0.5, brand["border"]),
        ("TOPPADDING", (0, 0), (-1, -1), 10),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
    ]))
    return t


def data_table(headers: list[str], rows: list[list], styles: dict, brand: dict,
               col_widths: list | None = None, highlight_last: bool = False,
               highlight_penultimate: bool = False):
    """Striped table with header row."""
    if not rows:
        return Spacer(1, 0)
    header_cells = [Paragraph(str(h), styles["table_header"]) for h in headers]
    table_data = [header_cells]
    for row in rows:
        cells = []
        for j, cell in enumerate(row):
            style = styles["table_cell"] if j == 0 else styles["table_cell_c"]
            cells.append(Paragraph(str(cell), style))
        table_data.append(cells)

    usable = PAGE_W - 2 * MARGIN
    if col_widths is None:
        col_widths = [usable / len(headers)] * len(headers)

    t = Table(table_data, colWidths=col_widths, repeatRows=1)
    style_cmds = [
        ("BACKGROUND", (0, 0), (-1, 0), brand["table_header"]),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), FONTS["bold"]),
        ("FONTSIZE", (0, 0), (-1, 0), 8.5),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("BOX", (0, 0), (-1, -1), 0.5, brand["border"]),
        ("INNERGRID", (0, 0), (-1, -1), 0.3, brand["border"]),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]
    for i in range(1, len(table_data)):
        bg = brand["row_zebra_2"] if i % 2 == 0 else brand["row_zebra_1"]
        style_cmds.append(("BACKGROUND", (0, i), (-1, i), bg))
    n = len(table_data)
    if highlight_penultimate and n >= 2:
        style_cmds.extend([
            ("BACKGROUND", (0, n-2), (-1, n-2), brand["info_bg"]),
            ("FONTNAME", (0, n-2), (-1, n-2), FONTS["bold"]),
        ])
    if highlight_last and n >= 1:
        style_cmds.extend([
            ("BACKGROUND", (0, n-1), (-1, n-1), brand["table_header"]),
            ("FONTNAME", (0, n-1), (-1, n-1), FONTS["bold"]),
            ("TEXTCOLOR", (0, n-1), (-1, n-1), colors.white),
        ])
    t.setStyle(TableStyle(style_cmds))
    return t


def section_rule(brand: dict):
    return HRFlowable(width="100%", thickness=2, color=brand["primary"],
                      spaceAfter=4, spaceBefore=0)


def light_rule(brand: dict):
    return HRFlowable(width="100%", thickness=0.5, color=brand["rule_color"],
                      spaceAfter=6, spaceBefore=6)


def info_box(text: str, brand: dict, bg=None, border=None):
    style = ParagraphStyle("info_box",
        fontName=FONTS["regular"], fontSize=9.5, leading=14,
        textColor=brand["primary_dark"], backColor=bg or brand["info_bg"],
        borderColor=border or brand["primary"], borderWidth=1.5, borderPad=8,
        spaceAfter=8, spaceBefore=4)
    return Paragraph(text, style)


# =============================================================================
# COVER PAGE
# =============================================================================

def cover_page(canvas_obj, doc, brand: dict, ui: dict, project: dict, lang: str):
    canvas_obj.saveState()
    W, H = PAGE_W, PAGE_H
    WHITE = colors.white

    canvas_obj.setFillColor(brand["primary_dark"])
    canvas_obj.rect(0, 0, W, H, fill=1, stroke=0)
    canvas_obj.setFillColor(brand["primary"])
    canvas_obj.rect(0, H - 8 * mm, W, 8 * mm, fill=1, stroke=0)
    canvas_obj.rect(0, 0, W, 3 * mm, fill=1, stroke=0)

    # Decorative panels
    canvas_obj.setFillColor(colors.HexColor("#312E81"))
    canvas_obj.rect(W * 0.55, H * 0.3, W * 0.6, H * 0.5, fill=1, stroke=0)
    canvas_obj.setFillColor(colors.HexColor("#1E1B4B"))
    canvas_obj.rect(W * 0.65, H * 0.1, W * 0.5, H * 0.7, fill=1, stroke=0)

    # Logo block
    x0 = 40
    canvas_obj.setFillColor(brand["primary"])
    canvas_obj.roundRect(x0, H - 50 * mm, 14 * mm, 14 * mm, 3 * mm, fill=1, stroke=0)
    canvas_obj.setFont(FONTS["bold"], 16)
    canvas_obj.setFillColor(WHITE)
    logo_letter = project.get("logo_letter") or (project.get("product_name") or "?")[0].upper()
    canvas_obj.drawString(x0 + 4 * mm, H - 42 * mm, logo_letter)
    canvas_obj.setFont(FONTS["bold"], 11)
    canvas_obj.drawString(x0 + 20 * mm, H - 41 * mm, project.get("product_name", ""))

    # Title
    y = H - 90 * mm
    canvas_obj.setFont(FONTS["bold"], 34)
    canvas_obj.setFillColor(WHITE)
    title = (project.get("valuation_title_translations") or {}).get(lang) or ui["report_subtitle"]
    words = title.split()
    lines_out = []
    line = ""
    for w in words:
        test = (line + " " + w).strip()
        if canvas_obj.stringWidth(test, FONTS["bold"], 34) < W * 0.55:
            line = test
        else:
            lines_out.append(line)
            line = w
    if line:
        lines_out.append(line)
    for li, ltext in enumerate(lines_out):
        canvas_obj.drawString(x0, y - li * 42, ltext)

    # Subtitle
    y2 = y - len(lines_out) * 42 - 12
    canvas_obj.setFont(FONTS["regular"], 14)
    canvas_obj.setFillColor(brand["accent"])
    canvas_obj.drawString(x0, y2, ui["report_subtitle"])

    # Meta
    meta_y = y2 - 50
    canvas_obj.setFont(FONTS["regular"], 10)
    meta = [
        ("Date", project.get("prepared_date") or datetime.now().strftime("%Y-%m-%d")),
        ("Repository", project.get("repository", "—")),
        ("Version", project.get("version", "—")),
        ("Language", project.get("language_names", {}).get(lang, lang.upper())),
        ("Confidence", project.get("confidence_label", "Medium-High")),
    ]
    for i, (k, v) in enumerate(meta):
        canvas_obj.setFillColor(colors.HexColor("#64748B"))
        canvas_obj.drawString(x0, meta_y - i * 14, f"{k}:  ")
        canvas_obj.setFillColor(WHITE)
        canvas_obj.drawString(x0 + 80, meta_y - i * 14, str(v))
    canvas_obj.restoreState()


def on_page(canvas_obj, doc, brand: dict, ui: dict, project: dict):
    canvas_obj.saveState()
    W, H = PAGE_W, PAGE_H
    WHITE = colors.white

    canvas_obj.setFillColor(brand["primary_dark"])
    canvas_obj.rect(0, H - 14 * mm, W, 14 * mm, fill=1, stroke=0)
    canvas_obj.setFillColor(brand["primary"])
    canvas_obj.rect(0, H - 15 * mm, W, 1 * mm, fill=1, stroke=0)

    canvas_obj.setFont(FONTS["bold"], 8)
    canvas_obj.setFillColor(WHITE)
    header_txt = f"{project.get('product_name', '')} — {ui['header_label']}"
    canvas_obj.drawString(20 * mm, H - 9 * mm, header_txt)
    canvas_obj.setFont(FONTS["regular"], 8)
    canvas_obj.setFillColor(colors.HexColor("#94A3B8"))
    canvas_obj.drawRightString(W - 20 * mm, H - 9 * mm, ui["confidential"])

    canvas_obj.setFillColor(colors.HexColor("#F8FAFC"))
    canvas_obj.rect(0, 0, W, 12 * mm, fill=1, stroke=0)
    canvas_obj.setFillColor(brand["primary"])
    canvas_obj.rect(0, 12 * mm, W, 0.5 * mm, fill=1, stroke=0)
    canvas_obj.setFont(FONTS["regular"], 7.5)
    canvas_obj.setFillColor(brand["text_muted"])
    footer_txt = f"© {datetime.now().year} {project.get('product_name', '')}. {ui['footer_note']}"
    canvas_obj.drawString(20 * mm, 4.5 * mm, footer_txt)
    canvas_obj.drawRightString(W - 20 * mm, 4.5 * mm, f"{ui['page']} {doc.page}")
    canvas_obj.restoreState()


# =============================================================================
# SECTION RENDERERS — each reads a slot in the JSON; if missing, skip silently.
# =============================================================================

def render_section_header(story, num, title, styles, brand):
    story.append(Paragraph(f"{num}. {title}", styles["h1"]))
    story.append(section_rule(brand))


def render_paragraphs(story, paragraphs, styles):
    if paragraphs is None:
        return
    if isinstance(paragraphs, str):
        paragraphs = [paragraphs]
    for p in paragraphs:
        story.append(Paragraph(p, styles["body"]))


def render_bullets(story, items, styles, marker="•"):
    if not items:
        return
    for item in items:
        story.append(Paragraph(f"{marker}  {item}", styles["bullet"]))


def render_subsections(story, subsections, styles, brand):
    """List of {"title": "...", "body": "..." | [...]} blocks."""
    if not subsections:
        return
    for sub in subsections:
        title = sub.get("title")
        if title:
            story.append(Paragraph(title, styles["h3"]))
        body = sub.get("body")
        if body:
            render_paragraphs(story, body, styles)
        bullets = sub.get("bullets")
        if bullets:
            render_bullets(story, bullets, styles)
        if sub.get("separator", True) and sub != subsections[-1]:
            story.append(light_rule(brand))


def render_executive_summary(story, content, styles, brand, ui, section_num):
    s = content.get("executive_summary") or {}
    render_section_header(story, section_num, ui["section_names"][0], styles, brand)
    render_paragraphs(story, s.get("description"), styles)
    if s.get("kpis"):
        story.append(Spacer(1, 6))
        if s.get("kpis_title"):
            story.append(Paragraph(s["kpis_title"], styles["h2"]))
        story.append(kpi_grid(s["kpis"], styles, brand))
        story.append(Spacer(1, 10))
    for tbl in (s.get("tables") or []):
        if tbl.get("title"):
            story.append(Paragraph(tbl["title"], styles["h2"]))
        story.append(data_table(
            tbl.get("headers", []),
            tbl.get("rows", []),
            styles, brand,
            col_widths=tbl.get("col_widths"),
            highlight_last=tbl.get("highlight_last", False),
            highlight_penultimate=tbl.get("highlight_penultimate", False),
        ))
        story.append(Spacer(1, 6))
    if s.get("callout"):
        story.append(info_box(s["callout"], brand))
    story.append(PageBreak())


def render_generic_section(story, section_data, section_idx, ui, styles, brand):
    """Generic renderer for sections 2-12 — they all share the same shape."""
    if not section_data:
        return
    section_num = section_idx + 1
    section_title = section_data.get("title") or ui["section_names"][section_idx]
    render_section_header(story, section_num, section_title, styles, brand)
    render_paragraphs(story, section_data.get("intro"), styles)

    for h2 in (section_data.get("h2_blocks") or []):
        if h2.get("title"):
            story.append(Paragraph(h2["title"], styles["h2"]))
        render_paragraphs(story, h2.get("body"), styles)
        if h2.get("subsections"):
            render_subsections(story, h2["subsections"], styles, brand)
        if h2.get("kpis"):
            story.append(kpi_grid(h2["kpis"], styles, brand))
            story.append(Spacer(1, 10))
        for tbl in (h2.get("tables") or []):
            if tbl.get("title"):
                story.append(Paragraph(tbl["title"], styles["h3"]))
            story.append(data_table(
                tbl.get("headers", []),
                tbl.get("rows", []),
                styles, brand,
                col_widths=tbl.get("col_widths"),
                highlight_last=tbl.get("highlight_last", False),
                highlight_penultimate=tbl.get("highlight_penultimate", False),
            ))
            story.append(Spacer(1, 6))
        if h2.get("bullets"):
            render_bullets(story, h2["bullets"], styles, marker=h2.get("bullet_marker", "•"))
        if h2.get("callouts"):
            for c in h2["callouts"]:
                story.append(info_box(c, brand))
        story.append(Spacer(1, 6))

    if section_data.get("callout"):
        story.append(info_box(section_data["callout"], brand))
    if section_data.get("closing"):
        story.append(light_rule(brand))
        story.append(Paragraph(section_data["closing"], styles["caption"]))
    story.append(PageBreak())


# =============================================================================
# TOC
# =============================================================================

def render_toc(story, ui, styles, brand, content):
    """Renders Table of Contents. Custom items can be provided via content['toc']."""
    story.append(Paragraph(ui["toc_heading"], styles["h1"]))
    story.append(section_rule(brand))
    items = content.get("toc")
    if not items:
        items = [f"{i+1}.  {name}" for i, name in enumerate(ui["section_names"])]
    for item in items:
        story.append(Paragraph(item, styles["toc_item"]))
    story.append(PageBreak())


# =============================================================================
# DOCUMENT ASSEMBLY
# =============================================================================

def build_pdf(output_path: str, project: dict, content: dict, lang: str) -> str:
    brand = resolve_brand(project)
    ui = UI_STRINGS[lang]
    styles = make_styles(brand)
    product_name = project["product_name"]

    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        leftMargin=MARGIN, rightMargin=MARGIN,
        topMargin=22*mm, bottomMargin=18*mm,
        title=f"{product_name} — {ui['header_label']}",
        author=project.get("author", "AI-assisted Technical Due-Diligence"),
        subject=ui["report_subtitle"],
    )

    story: list = []
    story.append(PageBreak())  # cover via onFirstPage
    render_toc(story, ui, styles, brand, content)

    # Map section keys (with fallbacks) — order matters
    section_keys = [
        "executive_summary",
        "product_reconstruction",
        "scope_decomposition",
        "methodology",
        "team_composition",
        "effort_estimate",
        "cost_estimate",
        "market_comparison",
        "market_value_estimate",
        "assumptions_limitations",
        "next_steps",
        "appendix",
    ]

    # Section 1 is special (has KPI grid + summary tables)
    render_executive_summary(story, content, styles, brand, ui, 1)

    # Sections 2-12 use generic renderer
    for i, key in enumerate(section_keys[1:], start=1):
        section_data = content.get(key)
        if section_data:
            render_generic_section(story, section_data, i, ui, styles, brand)

    def first_page(c, d):
        cover_page(c, d, brand, ui, project, lang)

    def later_pages(c, d):
        on_page(c, d, brand, ui, project)

    doc.build(story, onFirstPage=first_page, onLaterPages=later_pages)
    return output_path


# =============================================================================
# CLI
# =============================================================================

def main():
    p = argparse.ArgumentParser(
        description="Generate a Software Valuation PDF report from JSON data."
    )
    p.add_argument("--project", default="data/project.json")
    p.add_argument("--data-dir", default="data/valuation",
                   help="Directory containing per-language JSON files (default: data/valuation)")
    p.add_argument("--lang", default=None,
                   help="Single language to render (e.g. en, hu).")
    p.add_argument("--all-languages", action="store_true",
                   help="Render every language declared in project.json.")
    p.add_argument("--output", default=None,
                   help="Output PDF path (single language only).")
    p.add_argument("--output-dir", default="output")
    args = p.parse_args()

    project = load_json(args.project)
    if "product_name" not in project:
        sys.exit("ERROR: project.json missing 'product_name'")
    if "languages" not in project:
        sys.exit("ERROR: project.json missing 'languages'")

    if args.all_languages:
        languages = project["languages"]
    elif args.lang:
        languages = [args.lang]
    else:
        languages = [project["languages"][0]]

    for lang in languages:
        if lang not in UI_STRINGS:
            sys.exit(f"ERROR: language '{lang}' not supported. Built-in: {list(UI_STRINGS.keys())}")

    os.makedirs(args.output_dir, exist_ok=True)
    written = []
    for lang in languages:
        content_path = Path(args.data_dir) / f"{lang}.json"
        content = load_json(content_path)
        validate_valuation(content, lang)
        if args.output and not args.all_languages:
            out = args.output
            os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
        else:
            slug = project.get("valuation_output_slug") or "valuation-report"
            out = os.path.join(args.output_dir, f"{slug}-{lang}.pdf")
        build_pdf(out, project, content, lang)
        written.append(out)
        print(f"✓  Written: {out}")
    print(f"\nDone — {len(written)} PDF(s) generated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
