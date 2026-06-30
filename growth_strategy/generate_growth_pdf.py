#!/usr/bin/env python3
"""
Generic Growth Strategy PDF Generator
=====================================

A fully data-driven renderer. Reads two JSON files:
  - project.json          : branding, languages, baseline valuation, target
  - growth_strategy.json  : list of ranked initiatives with full content

Produces a branded multi-page PDF (one PDF per declared language).

Usage:
    python generate_growth_pdf.py
    python generate_growth_pdf.py --project data/project.json --data data/growth_strategy.json --lang en --output output/growth-en.pdf
    python generate_growth_pdf.py --all-languages          # render every language declared in project.json

Author: growth_strategy toolkit  (drop-in any repo)
License: MIT
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
    PageBreak,
)

# Make sibling module importable regardless of working directory.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from _fonts import setup_fonts  # noqa: E402

# Register Unicode-capable fonts (fixes Hungarian ő/ű, accents, dashes).
FONTS = setup_fonts()


# =============================================================================
# UI STRINGS (built-in language packs)
# =============================================================================
# Static UI labels for the report skeleton. Initiative content itself comes from
# the JSON data file in the requested language. To add a new language, add a key
# below; if a language is requested but the user-provided initiatives don't have
# that language key, generation will fail with a clear message.
# =============================================================================

UI_STRINGS: dict[str, dict[str, str]] = {
    "en": {
        "report_label":      "Growth Strategy Report",
        "header_brand":      "GROWTH STRATEGY",
        "cover_eyebrow":     "STRATEGIC PRODUCT & MARKET INTELLIGENCE",
        "cover_subtitle":    "How to become the market leader",
        "kpi_target":        "Target Valuation",
        "kpi_multiple":      "Value Multiple",
        "kpi_initiatives":   "Growth Initiatives",
        "prepared_label":    "Prepared",
        "confidential":      "Confidential",
        "baseline_label":    "Baseline Valuation",
        "target_label":      "Target Valuation",
        "toc_heading":       "TABLE OF CONTENTS",
        "toc_col_rank":      "#",
        "toc_col_init":      "Initiative",
        "toc_col_impact":    "Est. Impact",
        "toc_total":         "TOTAL COMBINED POTENTIAL",
        "rank_word":         "RANK",
        "point_desc":        "DESCRIPTION & MARKET EVIDENCE",
        "point_impl":        "IMPLEMENTATION PROMPT",
        "point_value":       "VALUE INCREASE ESTIMATION",
        "point_regen":       "REGENERATION PROMPT",
        "value_box_label":   "ESTIMATED VALUATION IMPACT",
        "metric_col":        "Metric",
        "impact_col":        "Impact",
        "matrix_heading":    "COMPLETE VALUE MATRIX",
        "matrix_subheading": "All Growth Initiatives",
        "matrix_col_rank":   "Rank",
        "matrix_col_init":   "Initiative",
        "matrix_col_impact": "Estimated Impact",
        "page":              "Page",
        "footer_note":       "Confidential — Strategic Intelligence Report",
        "exec_summary":      "EXECUTIVE SUMMARY",
        "exec_what":         "What this report contains",
        "exec_how":          "How to use it",
        "exec_what_body":    "A ranked set of high-leverage growth initiatives for {product}, each scored by expected valuation impact, with a ready-to-paste implementation prompt and a regeneration prompt for iterative deepening.",
        "exec_how_body":     "Read the matrix on the final page for the overview. Each ranked entry stands alone — pick any to deep-dive. The Implementation Prompt section is engineered to be pasted directly into an AI coding assistant inside the codebase.",
        "method_note":       "Methodology: market multiples sourced from public valuation databases (PitchBook, Crunchbase, SaaS Capital Index), competitor pricing pages, and analyst reports (Gartner, IDC, Forrester). Valuation impact is range-based and triangulated across replacement-cost, comparable-ARR-multiple, and strategic-premium lenses.",
    },
    "hu": {
        "report_label":      "Növekedési Stratégia",
        "header_brand":      "NÖVEKEDÉSI STRATÉGIA",
        "cover_eyebrow":     "STRATÉGIAI TERMÉK ÉS PIACI INTELLIGENCIA",
        "cover_subtitle":    "Hogyan váljon piacvezetővé",
        "kpi_target":        "Célértékelés",
        "kpi_multiple":      "Értékszorzó",
        "kpi_initiatives":   "Növekedési Kezdeményezés",
        "prepared_label":    "Készítve",
        "confidential":      "Bizalmas",
        "baseline_label":    "Kiindulási értékelés",
        "target_label":      "Célértékelés",
        "toc_heading":       "TARTALOMJEGYZÉK",
        "toc_col_rank":      "#",
        "toc_col_init":      "Kezdeményezés",
        "toc_col_impact":    "Becsült Hatás",
        "toc_total":         "TELJES KOMBINÁLT POTENCIÁL",
        "rank_word":         "RANG",
        "point_desc":        "LEÍRÁS ÉS PIACI BIZONYÍTÉKOK",
        "point_impl":        "MEGVALÓSÍTÁSI PROMPT",
        "point_value":       "ÉRTÉKNÖVEKEDÉSI BECSLÉS",
        "point_regen":       "ÚJRAGENERÁLÁSI PROMPT",
        "value_box_label":   "BECSÜLT ÉRTÉKNÖVEKEDÉS",
        "metric_col":        "Metrika",
        "impact_col":        "Hatás",
        "matrix_heading":    "TELJES ÉRTÉKMÁTRIX",
        "matrix_subheading": "Az összes Növekedési Kezdeményezés",
        "matrix_col_rank":   "Rang",
        "matrix_col_init":   "Kezdeményezés",
        "matrix_col_impact": "Becsült Hatás",
        "page":              "Oldal",
        "footer_note":       "Bizalmas — Stratégiai Intelligencia Jelentés",
        "exec_summary":      "VEZETŐI ÖSSZEFOGLALÓ",
        "exec_what":         "Mit tartalmaz a jelentés",
        "exec_how":          "Hogyan használjuk",
        "exec_what_body":    "Egy rangsorolt készlet a(z) {product} számára magas hatékonyságú növekedési kezdeményezésekből, mindegyik várható értéknövekedési hatással értékelve, kész-beilleszthető megvalósítási prompttal és iteratív mélyítésre szolgáló újragenerálási prompttal.",
        "exec_how_body":     "Az utolsó oldalon található értékmátrix nyújt áttekintést. Minden rangsorolt elem önállóan értelmezhető — válasszon bármelyiket részletes elemzéshez. A Megvalósítási Prompt szekciók közvetlen beillesztésre vannak megtervezve egy AI kódoló asszisztensbe a kódbázisban.",
        "method_note":       "Módszertan: piaci szorzók nyilvános értékelési adatbázisokból (PitchBook, Crunchbase, SaaS Capital Index), versenytárs árazási oldalakról és elemzői jelentésekből (Gartner, IDC, Forrester). Az értéknövekedési hatás tartomány-alapú, a helyettesítési költség, az összehasonlítható ARR-szorzó és a stratégiai prémium szempontjából háromszögelt.",
    },
    "de": {
        "report_label":      "Wachstumsstrategie",
        "header_brand":      "WACHSTUMSSTRATEGIE",
        "cover_eyebrow":     "STRATEGISCHE PRODUKT- UND MARKTINTELLIGENZ",
        "cover_subtitle":    "Wie Sie Marktführer werden",
        "kpi_target":        "Zielbewertung",
        "kpi_multiple":      "Wertmultiplikator",
        "kpi_initiatives":   "Wachstumsinitiativen",
        "prepared_label":    "Erstellt",
        "confidential":      "Vertraulich",
        "baseline_label":    "Basisbewertung",
        "target_label":      "Zielbewertung",
        "toc_heading":       "INHALTSVERZEICHNIS",
        "toc_col_rank":      "#",
        "toc_col_init":      "Initiative",
        "toc_col_impact":    "Gesch. Auswirkung",
        "toc_total":         "GESAMTPOTENZIAL",
        "rank_word":         "RANG",
        "point_desc":        "BESCHREIBUNG & MARKTNACHWEISE",
        "point_impl":        "IMPLEMENTIERUNGS-PROMPT",
        "point_value":       "WERTSTEIGERUNGS-SCHÄTZUNG",
        "point_regen":       "REGENERATIONS-PROMPT",
        "value_box_label":   "GESCHÄTZTE BEWERTUNGSAUSWIRKUNG",
        "metric_col":        "Metrik",
        "impact_col":        "Auswirkung",
        "matrix_heading":    "VOLLSTÄNDIGE WERTMATRIX",
        "matrix_subheading": "Alle Wachstumsinitiativen",
        "matrix_col_rank":   "Rang",
        "matrix_col_init":   "Initiative",
        "matrix_col_impact": "Geschätzte Auswirkung",
        "page":              "Seite",
        "footer_note":       "Vertraulich — Strategischer Intelligenzbericht",
        "exec_summary":      "ZUSAMMENFASSUNG",
        "exec_what":         "Inhalt dieses Berichts",
        "exec_how":          "So nutzen Sie ihn",
        "exec_what_body":    "Eine geordnete Liste hochwirksamer Wachstumsinitiativen für {product}, jeweils nach erwarteter Bewertungsauswirkung bewertet, mit fertigen Implementierungs-Prompts und Regenerations-Prompts zur iterativen Vertiefung.",
        "exec_how_body":     "Lesen Sie die Matrix auf der letzten Seite für den Überblick. Jeder Eintrag steht für sich allein — wählen Sie einen für die Tiefenanalyse. Die Implementierungs-Prompt-Abschnitte sind so konzipiert, dass sie direkt in einen KI-Coding-Assistenten innerhalb der Codebasis eingefügt werden können.",
        "method_note":       "Methodik: Marktmultiplikatoren aus öffentlichen Bewertungsdatenbanken (PitchBook, Crunchbase, SaaS Capital Index), Preisseiten von Wettbewerbern und Analystenberichten (Gartner, IDC, Forrester). Die Bewertungsauswirkung ist bereichsbasiert und über die Linsen Ersatzkosten, vergleichbares ARR-Vielfaches und strategische Prämie trianguliert.",
    },
}


# =============================================================================
# BRANDING (resolved from project.json with sensible defaults)
# =============================================================================
DEFAULT_BRAND = {
    "primary":      "#6366F1",  # indigo-500
    "primary_dark": "#0F172A",  # slate-900
    "primary_mid":  "#1E293B",  # slate-800
    "accent":       "#818CF8",  # indigo-400
    "success":      "#10B981",  # emerald-500
    "warning":      "#F59E0B",  # amber-500
    "danger":       "#EF4444",  # red-500
    "table_header": "#4F46E5",  # indigo-600
    "row_zebra_1":  "#F8F9FF",
    "row_zebra_2":  "#EEF2FF",
    "border":       "#C7D2FE",
    "text_body":    "#1E293B",
    "text_muted":   "#64748B",
    "code_bg":      "#1E293B",
    "code_text":    "#E2E8F0",
}

PAGE_W, PAGE_H = A4
MARGIN = 18 * mm


# =============================================================================
# DATA LOADING & VALIDATION
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


def validate_project(proj: dict) -> None:
    required = ["product_name", "languages", "baseline_valuation", "target_valuation"]
    missing = [k for k in required if k not in proj]
    if missing:
        sys.exit(f"ERROR: project.json missing required keys: {missing}")
    if not isinstance(proj["languages"], list) or not proj["languages"]:
        sys.exit("ERROR: project.json 'languages' must be a non-empty list, e.g. ['en','hu']")
    for lang in proj["languages"]:
        if lang not in UI_STRINGS:
            sys.exit(
                f"ERROR: language '{lang}' not supported. Built-in: {list(UI_STRINGS.keys())}.\n"
                f"To add a language, extend UI_STRINGS in generate_growth_pdf.py."
            )


def validate_growth(growth: dict, languages: list[str]) -> None:
    if "initiatives" not in growth or not isinstance(growth["initiatives"], list):
        sys.exit("ERROR: growth_strategy.json must have an 'initiatives' list")
    if not growth["initiatives"]:
        sys.exit("ERROR: growth_strategy.json 'initiatives' list is empty")
    for i, init in enumerate(growth["initiatives"], 1):
        if "translations" not in init:
            sys.exit(f"ERROR: initiative #{i} missing 'translations' object")
        for lang in languages:
            if lang not in init["translations"]:
                sys.exit(
                    f"ERROR: initiative #{i} missing translation for language '{lang}'.\n"
                    f"Each initiative must contain: translations.{lang}.{{title,desc,impl,metrics,regen}} + 'value' (shared)."
                )
            t = init["translations"][lang]
            for k in ("title", "desc", "impl", "metrics", "regen"):
                if k not in t:
                    sys.exit(f"ERROR: initiative #{i} translation [{lang}] missing key: {k}")
        if "value" not in init:
            sys.exit(f"ERROR: initiative #{i} missing 'value' (e.g. '+€100k–€200k')")


def resolve_brand(proj: dict) -> dict[str, colors.Color]:
    """Merge project brand overrides on top of DEFAULT_BRAND, return resolved colour objects."""
    merged = {**DEFAULT_BRAND, **(proj.get("brand") or {})}
    return {k: colors.HexColor(v) if isinstance(v, str) and v.startswith("#") else v
            for k, v in merged.items()}


def rank_palette(brand: dict) -> list[colors.Color]:
    """Cycle of accent colours used to differentiate ranked items."""
    return [
        brand["primary"],
        colors.HexColor("#8B5CF6"),
        colors.HexColor("#3B82F6"),
        colors.HexColor("#0EA5E9"),
        brand["success"],
        colors.HexColor("#059669"),
        brand["warning"],
        brand["danger"],
    ]


# =============================================================================
# STYLES
# =============================================================================

def make_styles(brand: dict) -> dict[str, ParagraphStyle]:
    WHITE = colors.white
    TEXT_BODY = brand["text_body"]
    TEXT_MUTED = brand["text_muted"]
    PRIMARY = brand["primary"]
    ACCENT = brand["accent"]
    SUCCESS = brand["success"]

    return {
        "cover_title": ParagraphStyle("cover_title",
            fontName=FONTS["bold"], fontSize=28, textColor=WHITE,
            leading=34, alignment=TA_CENTER, spaceAfter=6),
        "cover_eyebrow": ParagraphStyle("cover_eyebrow",
            fontName=FONTS["bold"], fontSize=10, textColor=ACCENT,
            leading=14, alignment=TA_CENTER, spaceAfter=8),
        "cover_sub": ParagraphStyle("cover_sub",
            fontName=FONTS["regular"], fontSize=14, textColor=ACCENT,
            leading=20, alignment=TA_CENTER, spaceAfter=4),
        "cover_meta": ParagraphStyle("cover_meta",
            fontName=FONTS["regular"], fontSize=10, textColor=colors.HexColor("#94A3B8"),
            leading=14, alignment=TA_CENTER),
        "body": ParagraphStyle("body",
            fontName=FONTS["regular"], fontSize=9.5, textColor=TEXT_BODY,
            leading=14, spaceAfter=4, alignment=TA_JUSTIFY),
        "body_bold": ParagraphStyle("body_bold",
            fontName=FONTS["bold"], fontSize=9.5, textColor=TEXT_BODY,
            leading=14, spaceAfter=4),
        "bullet": ParagraphStyle("bullet",
            fontName=FONTS["regular"], fontSize=9, textColor=TEXT_BODY,
            leading=13, leftIndent=12, spaceAfter=3),
        "code": ParagraphStyle("code",
            fontName=FONTS["mono"], fontSize=7.5, textColor=brand["code_text"],
            leading=11, spaceAfter=2, backColor=brand["code_bg"]),
        "table_hdr": ParagraphStyle("table_hdr",
            fontName=FONTS["bold"], fontSize=8.5, textColor=WHITE,
            leading=12, alignment=TA_CENTER),
        "table_cell": ParagraphStyle("table_cell",
            fontName=FONTS["regular"], fontSize=8.5, textColor=TEXT_BODY,
            leading=12),
        "table_cell_bold": ParagraphStyle("table_cell_bold",
            fontName=FONTS["bold"], fontSize=8.5, textColor=PRIMARY,
            leading=12),
        "toc_title": ParagraphStyle("toc_title",
            fontName=FONTS["regular"], fontSize=9, textColor=TEXT_BODY,
            leading=13),
        "muted": ParagraphStyle("muted",
            fontName=FONTS["regular"], fontSize=8, textColor=TEXT_MUTED,
            leading=12, spaceAfter=4),
        "muted_center": ParagraphStyle("muted_center",
            fontName=FONTS["oblique"], fontSize=8, textColor=TEXT_MUTED,
            leading=12, alignment=TA_CENTER),
        "exec_h": ParagraphStyle("exec_h",
            fontName=FONTS["bold"], fontSize=11, textColor=PRIMARY,
            leading=16, spaceBefore=10, spaceAfter=4),
    }


# =============================================================================
# PAGE TEMPLATE
# =============================================================================

class PageTemplate:
    def __init__(self, brand: dict, ui: dict, product_name: str):
        self.brand = brand
        self.ui = ui
        self.product_name = product_name

    def on_page(self, c: canvas.Canvas, doc):
        pn = doc.page
        c.saveState()
        WHITE = colors.white
        # Header bar
        c.setFillColor(self.brand["primary_dark"])
        c.rect(0, PAGE_H - 14*mm, PAGE_W, 14*mm, fill=1, stroke=0)
        c.setFillColor(self.brand["primary"])
        c.rect(0, PAGE_H - 14*mm, 4*mm, 14*mm, fill=1, stroke=0)
        c.setFont(FONTS["bold"], 8)
        c.setFillColor(WHITE)
        c.drawString(MARGIN, PAGE_H - 9*mm, self.product_name.upper())
        c.setFont(FONTS["regular"], 8)
        c.setFillColor(self.brand["accent"])
        c.drawRightString(PAGE_W - MARGIN, PAGE_H - 9*mm, self.ui["report_label"])
        # Footer bar
        c.setFillColor(self.brand["primary_dark"])
        c.rect(0, 0, PAGE_W, 12*mm, fill=1, stroke=0)
        c.setFont(FONTS["regular"], 7.5)
        c.setFillColor(self.brand["text_muted"])
        c.drawString(MARGIN, 4.5*mm, self.ui["footer_note"])
        c.setFillColor(self.brand["accent"])
        c.drawRightString(PAGE_W - MARGIN, 4.5*mm, f"{self.ui['page']} {pn}")
        c.restoreState()


# =============================================================================
# COVER PAGE
# =============================================================================

def build_cover(story, styles, brand, ui, proj, n_initiatives, lang):
    WHITE = colors.white
    product = proj["product_name"]
    tagline = (proj.get("tagline_translations") or {}).get(lang) or proj.get("tagline") or ""
    main_title = (proj.get("title_translations") or {}).get(lang) or f"TOP {n_initiatives} GROWTH STRATEGY"
    subtitle_extra = (proj.get("subtitle_translations") or {}).get(lang) or ui["cover_subtitle"]
    target_text = proj.get("target_valuation", "")
    baseline_text = proj.get("baseline_valuation", "")
    value_multiple = proj.get("value_multiple", "")
    prepared_date = proj.get("prepared_date") or datetime.now().strftime("%B %Y")

    story.append(Spacer(1, 25 * mm))

    # Eyebrow
    story.append(Paragraph(ui["cover_eyebrow"], styles["cover_eyebrow"]))
    story.append(Spacer(1, 4 * mm))

    # Title block
    title_data = [[Paragraph(main_title.replace("\\n", "<br/>"), styles["cover_title"])]]
    title_table = Table(title_data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[90])
    title_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary_dark"]),
        ("TOPPADDING", (0, 0), (-1, -1), 20),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(title_table)
    story.append(Spacer(1, 4 * mm))

    # Subtitle block (product context)
    sub_text = subtitle_extra
    if tagline:
        sub_text = f"{subtitle_extra}<br/><font color='#94A3B8' size='10'>{tagline}</font>"
    sub_data = [[Paragraph(sub_text, styles["cover_sub"])]]
    sub_table = Table(sub_data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[60])
    sub_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary_mid"]),
        ("TOPPADDING", (0, 0), (-1, -1), 10),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(sub_table)
    story.append(Spacer(1, 6 * mm))

    # KPI bar
    kpi_label_style = ParagraphStyle("kv", fontName=FONTS["bold"],
        fontSize=16, textColor=WHITE, leading=20, alignment=TA_CENTER)
    kpi_desc_style = ParagraphStyle("kd", fontName=FONTS["regular"],
        fontSize=8, textColor=brand["text_muted"], leading=12, alignment=TA_CENTER)

    kpi_data = [
        [
            Paragraph(target_text, ParagraphStyle("k1", parent=kpi_label_style,
                textColor=brand["success"])),
            Paragraph(value_multiple or "—", ParagraphStyle("k2", parent=kpi_label_style,
                textColor=brand["primary"])),
            Paragraph(str(n_initiatives), ParagraphStyle("k3", parent=kpi_label_style,
                textColor=brand["warning"])),
        ],
        [
            Paragraph(ui["kpi_target"], kpi_desc_style),
            Paragraph(ui["kpi_multiple"], kpi_desc_style),
            Paragraph(ui["kpi_initiatives"], kpi_desc_style),
        ]
    ]
    kpi_table = Table(kpi_data, colWidths=[(PAGE_W - 2*MARGIN)/3] * 3,
                      rowHeights=[28, 20])
    kpi_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary_mid"]),
        ("LINEAFTER", (0, 0), (1, -1), 0.5, colors.HexColor("#334155")),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(kpi_table)
    story.append(Spacer(1, 8 * mm))

    # Meta block
    meta_lines = [
        f"<b>{product}</b> — {tagline}" if tagline else f"<b>{product}</b>",
        f"{ui['prepared_label']}: {prepared_date}  |  {ui['confidential']}",
    ]
    if baseline_text and target_text:
        meta_lines.append(
            f"<font color='#94A3B8'>{ui['baseline_label']}: {baseline_text}</font>  "
            f"→  <font color='#A5F3FC'>{ui['target_label']}: {target_text}</font>"
        )

    meta_data = [[Paragraph(line, styles["cover_meta"])] for line in meta_lines]
    meta_table = Table(meta_data, colWidths=[PAGE_W - 2*MARGIN])
    meta_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary_dark"]),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    story.append(meta_table)
    story.append(PageBreak())


# =============================================================================
# EXECUTIVE SUMMARY PAGE
# =============================================================================

def build_exec_summary(story, styles, brand, ui, proj, n_initiatives, lang):
    WHITE = colors.white
    product = proj["product_name"]

    hdr_data = [[Paragraph(ui["exec_summary"], ParagraphStyle("eh",
        fontName=FONTS["bold"], fontSize=14, textColor=WHITE, leading=20))]]
    hdr_t = Table(hdr_data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[30])
    hdr_t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary"]),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
    ]))
    story.append(Spacer(1, 5 * mm))
    story.append(hdr_t)
    story.append(Spacer(1, 5 * mm))

    story.append(Paragraph(ui["exec_what"], styles["exec_h"]))
    story.append(Paragraph(ui["exec_what_body"].format(product=product), styles["body"]))
    story.append(Spacer(1, 3 * mm))

    story.append(Paragraph(ui["exec_how"], styles["exec_h"]))
    story.append(Paragraph(ui["exec_how_body"], styles["body"]))
    story.append(Spacer(1, 6 * mm))

    story.append(Paragraph(ui["method_note"], styles["muted"]))

    # Custom summary paragraph if provided
    extra = (proj.get("exec_summary_translations") or {}).get(lang)
    if extra:
        story.append(Spacer(1, 3 * mm))
        story.append(Paragraph(extra, styles["body"]))

    story.append(PageBreak())


# =============================================================================
# TABLE OF CONTENTS
# =============================================================================

def build_toc(story, styles, brand, ui, initiatives, lang):
    WHITE = colors.white
    rank_colors = rank_palette(brand)

    story.append(Spacer(1, 5 * mm))
    hdr_data = [[Paragraph(ui["toc_heading"], ParagraphStyle("th",
        fontName=FONTS["bold"], fontSize=16, textColor=WHITE, leading=22))]]
    hdr_table = Table(hdr_data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[32])
    hdr_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary"]),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
    ]))
    story.append(hdr_table)
    story.append(Spacer(1, 4 * mm))

    col_w = [12*mm, PAGE_W - 2*MARGIN - 12*mm - 36*mm, 36*mm]
    rows = [[
        Paragraph(ui["toc_col_rank"], styles["table_hdr"]),
        Paragraph(ui["toc_col_init"], styles["table_hdr"]),
        Paragraph(ui["toc_col_impact"], styles["table_hdr"]),
    ]]
    total_low = total_high = 0
    for i, init in enumerate(initiatives, 1):
        color = rank_colors[(i - 1) % len(rank_colors)]
        title = init["translations"][lang]["title"]
        value = init["value"]
        rng = _parse_range(value)
        if rng:
            total_low += rng[0]
            total_high += rng[1]
        rows.append([
            Paragraph(f"<b>{i}</b>", ParagraphStyle("rn",
                fontName=FONTS["bold"], fontSize=9, textColor=color,
                leading=12, alignment=TA_CENTER)),
            Paragraph(title, styles["toc_title"]),
            Paragraph(f"<b>{value}</b>", ParagraphStyle("imp",
                fontName=FONTS["bold"], fontSize=8, textColor=brand["success"],
                leading=12, alignment=TA_RIGHT)),
        ])

    toc_table = Table(rows, colWidths=col_w)
    ts = TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), brand["table_header"]),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("GRID", (0, 0), (-1, -1), 0.3, brand["border"]),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ])
    for i in range(1, len(rows)):
        bg = brand["row_zebra_1"] if i % 2 == 1 else brand["row_zebra_2"]
        ts.add("BACKGROUND", (0, i), (-1, i), bg)
    toc_table.setStyle(ts)
    story.append(toc_table)
    story.append(Spacer(1, 6 * mm))

    # Total row
    total_str = _format_total(total_low, total_high) if (total_low or total_high) else ""
    if total_str:
        total_data = [[
            Paragraph(ui["toc_total"], ParagraphStyle("tl",
                fontName=FONTS["bold"], fontSize=11, textColor=WHITE, leading=15)),
            Paragraph(total_str, ParagraphStyle("tv",
                fontName=FONTS["bold"], fontSize=14, textColor=brand["success"],
                leading=18, alignment=TA_RIGHT)),
        ]]
        total_table = Table(total_data, colWidths=[PAGE_W - 2*MARGIN - 60*mm, 60*mm],
                            rowHeights=[30])
        total_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), brand["primary_dark"]),
            ("LEFTPADDING", (0, 0), (-1, -1), 12),
            ("RIGHTPADDING", (0, 0), (-1, -1), 12),
            ("TOPPADDING", (0, 0), (-1, -1), 8),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ]))
        story.append(total_table)
    story.append(PageBreak())


# =============================================================================
# INITIATIVE BLOCKS
# =============================================================================

def rank_header_block(rank_num, title, brand, ui):
    WHITE = colors.white
    color = rank_palette(brand)[(rank_num - 1) % len(rank_palette(brand))]
    rank_p = Paragraph(f"{ui['rank_word']} {rank_num}", ParagraphStyle("rl",
        fontName=FONTS["bold"], fontSize=10, textColor=WHITE, leading=14))
    title_p = Paragraph(title, ParagraphStyle("rht",
        fontName=FONTS["bold"], fontSize=13, textColor=WHITE, leading=17))
    data = [[rank_p, ""], [title_p, ""]]
    t = Table(data, colWidths=[PAGE_W - 2*MARGIN - 20*mm, 20*mm], rowHeights=[18, 22])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), color),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("SPAN", (0, 0), (1, 0)),
        ("SPAN", (0, 1), (1, 1)),
    ]))
    return t


def point_label_block(number, label, brand, lang):
    text = f"POINT {number} — {label}" if lang in ("en", "de") else f"{number}. PONT — {label}"
    return Paragraph(text, ParagraphStyle("plb",
        fontName=FONTS["bold"], fontSize=8, textColor=brand["primary"],
        leading=12, spaceBefore=8, spaceAfter=2))


def value_box(impact_text, brand, ui):
    WHITE = colors.white
    data = [
        [Paragraph(ui["value_box_label"], ParagraphStyle("vbl",
            fontName=FONTS["bold"], fontSize=8, textColor=brand["accent"], leading=12))],
        [Paragraph(impact_text, ParagraphStyle("vbv",
            fontName=FONTS["bold"], fontSize=18, textColor=brand["success"],
            leading=22, alignment=TA_CENTER))],
    ]
    t = Table(data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[18, 28])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary_dark"]),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("RIGHTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (0, 0), 6),
        ("BOTTOMPADDING", (0, 1), (0, 1), 6),
        ("ALIGN", (0, 1), (0, 1), "CENTER"),
    ]))
    return t


def metrics_table(rows, styles, brand, ui):
    hdr = [
        Paragraph(ui["metric_col"], styles["table_hdr"]),
        Paragraph(ui["impact_col"], styles["table_hdr"]),
    ]
    data = [hdr]
    for row in rows:
        if isinstance(row, dict):
            m = row.get("metric", "")
            v = row.get("impact", "")
        else:
            m, v = row[0], row[1]
        data.append([
            Paragraph(m, styles["table_cell"]),
            Paragraph(v, styles["table_cell_bold"]),
        ])
    col_w = [(PAGE_W - 2*MARGIN) * 0.55, (PAGE_W - 2*MARGIN) * 0.45]
    t = Table(data, colWidths=col_w)
    ts = TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), brand["table_header"]),
        ("GRID", (0, 0), (-1, -1), 0.3, brand["border"]),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ])
    for i in range(1, len(data)):
        ts.add("BACKGROUND", (0, i), (-1, i),
               brand["row_zebra_1"] if i % 2 == 1 else brand["row_zebra_2"])
    t.setStyle(ts)
    return t


def code_block(text, styles, brand):
    """Render a code/prompt snippet block (truncated to 35 lines to fit on page)."""
    if isinstance(text, list):
        lines = text
    else:
        lines = text.strip().split("\n")
    safe_lines = []
    for line in lines[:35]:
        if line.strip():
            safe = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            safe = safe.replace(" ", "&nbsp;")
        else:
            safe = "&nbsp;"
        safe_lines.append([Paragraph(safe, styles["code"])])

    if not safe_lines:
        return Spacer(1, 2 * mm)

    t = Table(safe_lines, colWidths=[PAGE_W - 2*MARGIN])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["code_bg"]),
        ("TOPPADDING", (0, 0), (-1, -1), 1),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 1),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
    ]))
    return t


def add_rank(story, styles, brand, ui, rank_num, init, lang):
    """Render one ranked initiative across one page break."""
    t = init["translations"][lang]
    story.append(Spacer(1, 5 * mm))
    story.append(rank_header_block(rank_num, t["title"], brand, ui))
    story.append(Spacer(1, 3 * mm))

    # Point 2 — Description
    story.append(point_label_block(2, ui["point_desc"], brand, lang))
    desc = t["desc"]
    if isinstance(desc, str):
        desc = [desc]
    for para in desc:
        story.append(Paragraph(para, styles["body"]))
    story.append(Spacer(1, 3 * mm))

    # Point 3 — Implementation prompt
    story.append(point_label_block(3, ui["point_impl"], brand, lang))
    story.append(code_block(t["impl"], styles, brand))
    story.append(Spacer(1, 3 * mm))

    # Point 4 — Value estimation table + box
    story.append(point_label_block(4, ui["point_value"], brand, lang))
    story.append(metrics_table(t["metrics"], styles, brand, ui))
    story.append(Spacer(1, 3 * mm))
    story.append(value_box(init["value"], brand, ui))
    story.append(Spacer(1, 3 * mm))

    # Point 5 — Regeneration prompt
    story.append(point_label_block(5, ui["point_regen"], brand, lang))
    story.append(code_block(t["regen"], styles, brand))
    story.append(PageBreak())


# =============================================================================
# SUMMARY MATRIX (final page)
# =============================================================================

def build_summary_matrix(story, styles, brand, ui, proj, initiatives, lang):
    WHITE = colors.white
    rank_colors = rank_palette(brand)
    target = proj.get("target_valuation", "")
    baseline = proj.get("baseline_valuation", "")
    multiple = proj.get("value_multiple", "")

    story.append(Spacer(1, 5 * mm))
    hdr_data = [[Paragraph(f"{ui['matrix_heading']} — {ui['matrix_subheading']}",
        ParagraphStyle("sh", fontName=FONTS["bold"],
        fontSize=13, textColor=WHITE, leading=18))]]
    hdr_t = Table(hdr_data, colWidths=[PAGE_W - 2*MARGIN], rowHeights=[30])
    hdr_t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), brand["primary"]),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
    ]))
    story.append(hdr_t)
    story.append(Spacer(1, 4 * mm))

    col_w = [12*mm, PAGE_W - 2*MARGIN - 12*mm - 36*mm, 36*mm]
    header_row = [
        Paragraph(ui["matrix_col_rank"], styles["table_hdr"]),
        Paragraph(ui["matrix_col_init"], styles["table_hdr"]),
        Paragraph(ui["matrix_col_impact"], styles["table_hdr"]),
    ]
    data = [header_row]
    total_low = total_high = 0
    for i, init in enumerate(initiatives, 1):
        color = rank_colors[(i - 1) % len(rank_colors)]
        title = init["translations"][lang]["title"]
        value = init["value"]
        prefixed_value = value if value.startswith(("+", "-")) else f"+{value}"
        rng = _parse_range(value)
        if rng:
            total_low += rng[0]
            total_high += rng[1]
        data.append([
            Paragraph(f"<b>{i}</b>", ParagraphStyle("rn2",
                fontName=FONTS["bold"], fontSize=9, textColor=color,
                leading=12, alignment=TA_CENTER)),
            Paragraph(title, styles["toc_title"]),
            Paragraph(f"<b>{prefixed_value}</b>", ParagraphStyle("imp2",
                fontName=FONTS["bold"], fontSize=8.5, textColor=brand["success"],
                leading=12, alignment=TA_RIGHT)),
        ])

    total_str = _format_total(total_low, total_high) if (total_low or total_high) else ""
    if total_str:
        data.append([
            Paragraph("", styles["table_cell"]),
            Paragraph(f"<b>{ui['toc_total']}</b>", ParagraphStyle("tr",
                fontName=FONTS["bold"], fontSize=10, textColor=WHITE, leading=14)),
            Paragraph(f"<b>{total_str}</b>", ParagraphStyle("tv2",
                fontName=FONTS["bold"], fontSize=11, textColor=brand["success"],
                leading=14, alignment=TA_RIGHT)),
        ])

    t = Table(data, colWidths=col_w)
    ts = TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), brand["table_header"]),
        ("GRID", (0, 0), (-1, -2 if total_str else -1), 0.3, brand["border"]),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ])
    end_idx = len(data) - 2 if total_str else len(data) - 1
    for i in range(1, end_idx + 1):
        ts.add("BACKGROUND", (0, i), (-1, i),
               brand["row_zebra_1"] if i % 2 == 1 else brand["row_zebra_2"])
    if total_str:
        ts.add("BACKGROUND", (0, -1), (-1, -1), brand["primary_dark"])
    t.setStyle(ts)
    story.append(t)
    story.append(Spacer(1, 6 * mm))

    # Baseline / target callout
    if baseline and target:
        bl_data = [
            [Paragraph(f"{ui['baseline_label']}: {baseline}",
                ParagraphStyle("bl", fontName=FONTS["regular"], fontSize=9,
                textColor=brand["text_muted"], leading=13))],
            [Paragraph(f"→  {ui['target_label']}: {target}"
                + (f"  ({multiple})" if multiple else ""),
                ParagraphStyle("tgt", fontName=FONTS["bold"],
                fontSize=11, textColor=brand["success"], leading=16))],
        ]
        bl_t = Table(bl_data, colWidths=[PAGE_W - 2*MARGIN])
        bl_t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), brand["primary_dark"]),
            ("LEFTPADDING", (0, 0), (-1, -1), 14),
            ("TOPPADDING", (0, 0), (-1, -1), 8),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ]))
        story.append(bl_t)


# =============================================================================
# HELPERS — VALUE PARSING (for auto-total computation)
# =============================================================================

def _parse_range(value_str: str) -> tuple[int, int] | None:
    """
    Parse strings like '+€800k–€1,350k' / '€100k-200k' / '+€1.2M-€2M' into (low, high) in € (integer).
    Returns None if parsing fails (gracefully skipped in totals).
    """
    if not value_str:
        return None
    s = value_str.replace(",", ".").replace(" ", "").replace("+", "")
    # Find numeric+suffix pairs (handle both en-dash and hyphen)
    import re
    pat = re.compile(r"€?(\d+(?:\.\d+)?)\s*([kKmM])?")
    parts = re.split(r"[–-]", s)
    if len(parts) != 2:
        return None
    nums = []
    for part in parts:
        m = pat.search(part)
        if not m:
            return None
        n = float(m.group(1))
        suf = (m.group(2) or "").lower()
        if suf == "k":
            n *= 1_000
        elif suf == "m":
            n *= 1_000_000
        nums.append(int(n))
    return (min(nums), max(nums))


def _format_total(low: int, high: int) -> str:
    def fmt(n: int) -> str:
        if n >= 1_000_000:
            return f"€{n / 1_000_000:.2f}M".replace(".00M", "M")
        return f"€{n // 1000}k"
    return f"+{fmt(low)}–{fmt(high)}"


# =============================================================================
# DOCUMENT ASSEMBLY
# =============================================================================

def build_pdf(output_path: str, project: dict, growth: dict, lang: str) -> str:
    brand = resolve_brand(project)
    ui = UI_STRINGS[lang]
    styles = make_styles(brand)
    product_name = project["product_name"]
    initiatives = growth["initiatives"]

    # Auto-fill target valuation from totals if not provided
    if not project.get("target_valuation"):
        total_low = total_high = 0
        for init in initiatives:
            r = _parse_range(init["value"])
            if r:
                total_low += r[0]
                total_high += r[1]
        if total_high:
            project["target_valuation"] = _format_total(total_low, total_high).lstrip("+")

    tmpl = PageTemplate(brand, ui, product_name)
    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        leftMargin=MARGIN, rightMargin=MARGIN,
        topMargin=16*mm, bottomMargin=14*mm,
        title=f"{product_name} — {ui['report_label']}",
        author=project.get("author", f"{product_name} Strategic Intelligence"),
    )

    story: list = []
    build_cover(story, styles, brand, ui, project, len(initiatives), lang)
    build_exec_summary(story, styles, brand, ui, project, len(initiatives), lang)
    build_toc(story, styles, brand, ui, initiatives, lang)
    for i, init in enumerate(initiatives, 1):
        add_rank(story, styles, brand, ui, i, init, lang)
    build_summary_matrix(story, styles, brand, ui, project, initiatives, lang)

    doc.build(story, onFirstPage=tmpl.on_page, onLaterPages=tmpl.on_page)
    return output_path


# =============================================================================
# CLI
# =============================================================================

def main():
    p = argparse.ArgumentParser(
        description="Generate a Growth Strategy PDF report from JSON data."
    )
    p.add_argument("--project", default="data/project.json",
                   help="Path to project.json (default: data/project.json)")
    p.add_argument("--data", default="data/growth_strategy.json",
                   help="Path to growth_strategy.json (default: data/growth_strategy.json)")
    p.add_argument("--lang", default=None,
                   help="Language code (e.g. en, hu). Overrides project.json default.")
    p.add_argument("--all-languages", action="store_true",
                   help="Render a PDF for every language declared in project.json.")
    p.add_argument("--output", default=None,
                   help="Output PDF path (single language only).")
    p.add_argument("--output-dir", default="output",
                   help="Output directory (used with --all-languages, default: output/)")
    args = p.parse_args()

    project = load_json(args.project)
    growth = load_json(args.data)
    validate_project(project)

    if args.all_languages:
        languages = project["languages"]
    elif args.lang:
        languages = [args.lang]
    else:
        languages = [project["languages"][0]]

    validate_growth(growth, languages)

    os.makedirs(args.output_dir, exist_ok=True)
    written = []
    for lang in languages:
        if args.output and not args.all_languages:
            out = args.output
            os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
        else:
            slug = project.get("output_slug") or "growth-strategy"
            out = os.path.join(args.output_dir, f"{slug}-{lang}.pdf")
        build_pdf(out, project, growth, lang)
        written.append(out)
        print(f"✓  Written: {out}")

    print(f"\nDone — {len(written)} PDF(s) generated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
