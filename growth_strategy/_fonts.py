"""
_fonts.py — Shared font loader for the report generators.

Why this exists
---------------
ReportLab's built-in "Helvetica" is a PDF Standard-14 font with a limited
character set. It CANNOT render Hungarian ő / ű, several accented Central- and
Eastern-European letters, smart quotes, en-dashes in some cases, etc. Those
characters silently turn into ▯ boxes in the PDF.

This module fixes that by registering the bundled DejaVu TrueType fonts (full
Latin Extended-A coverage → correct Hungarian, German, Polish, Czech, etc.).

Resolution order
----------------
1. Bundled fonts in  <toolkit>/fonts/*.ttf   (portable — works on any OS)
2. System DejaVu fonts (common on Linux)
3. Fallback to Helvetica/Courier with a printed warning

Usage
-----
    from _fonts import setup_fonts
    FONTS = setup_fonts()           # registers fonts, returns logical-name map
    style = ParagraphStyle(..., fontName=FONTS["regular"])
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# Logical name → fallback PDF Standard-14 font
_FALLBACK = {
    "regular": "Helvetica",
    "bold":    "Helvetica-Bold",
    "oblique": "Helvetica-Oblique",
    "mono":    "Courier",
}

# Logical name → (registered font name, expected bundled filename)
_DEJAVU = {
    "regular": ("DejaVuSans",        "DejaVuSans.ttf"),
    "bold":    ("DejaVuSans-Bold",   "DejaVuSans-Bold.ttf"),
    "oblique": ("DejaVuSans-Oblique","DejaVuSans-Oblique.ttf"),
    "mono":    ("DejaVuSansMono",    "DejaVuSansMono.ttf"),
}

# Candidate system locations for DejaVu (used if no bundled fonts found)
_SYSTEM_DIRS = [
    "/usr/share/fonts/truetype/dejavu",
    "/usr/share/fonts/dejavu",
    "/usr/local/share/fonts",
    "/Library/Fonts",
    os.path.expanduser("~/.fonts"),
    "C:/Windows/Fonts",
]

_cached: dict[str, str] | None = None


def _find_font_dir() -> Path | None:
    """Return the directory holding the four DejaVu TTFs, or None."""
    # 1. Bundled fonts next to this module
    bundled = Path(__file__).resolve().parent / "fonts"
    if bundled.is_dir() and (bundled / "DejaVuSans.ttf").exists():
        return bundled
    # 2. System directories
    for d in _SYSTEM_DIRS:
        p = Path(d)
        if p.is_dir() and (p / "DejaVuSans.ttf").exists():
            return p
    return None


def setup_fonts(verbose: bool = True) -> dict[str, str]:
    """
    Register Unicode fonts and return a {logical_name: font_name} map.

    Logical names: 'regular', 'bold', 'oblique', 'mono'.
    Safe to call multiple times — registration is cached.
    """
    global _cached
    if _cached is not None:
        return _cached

    font_dir = _find_font_dir()
    if font_dir is None:
        if verbose:
            print(
                "WARNING: DejaVu fonts not found (bundled or system). "
                "Falling back to Helvetica — Hungarian ő/ű and some accents "
                "may render as boxes. Keep the 'fonts/' folder next to the script.",
                file=sys.stderr,
            )
        _cached = dict(_FALLBACK)
        return _cached

    resolved: dict[str, str] = {}
    try:
        for logical, (font_name, filename) in _DEJAVU.items():
            path = font_dir / filename
            if path.exists():
                pdfmetrics.registerFont(TTFont(font_name, str(path)))
                resolved[logical] = font_name
            else:
                resolved[logical] = _FALLBACK[logical]

        # Register a font family so <b> / <i> markup inside Paragraphs works.
        if all(k in resolved and resolved[k].startswith("DejaVu")
               for k in ("regular", "bold", "oblique")):
            pdfmetrics.registerFontFamily(
                "DejaVuSans",
                normal=resolved["regular"],
                bold=resolved["bold"],
                italic=resolved["oblique"],
                boldItalic=resolved["bold"],
            )
    except Exception as e:  # pragma: no cover — defensive
        if verbose:
            print(f"WARNING: font registration failed ({e}); using Helvetica.",
                  file=sys.stderr)
        _cached = dict(_FALLBACK)
        return _cached

    # Fill any gaps with fallbacks
    for k, v in _FALLBACK.items():
        resolved.setdefault(k, v)

    _cached = resolved
    return _cached
