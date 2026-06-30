#!/usr/bin/env python3
"""Markdown renderer for the Growth Strategy report.

The toolkit's generate_growth_pdf.py renderer is not present in this folder,
so this script produces the Markdown twins described in AI_INSTRUCTIONS.md
section 6.5 directly from data/project.json + data/growth_strategy.json.
Pure renderer: reads JSON, emits output/growth-strategy-<lang>.md.
"""
import io
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = json.loads((HERE / "data" / "project.json").read_text(encoding="utf-8"))
GROWTH = json.loads((HERE / "data" / "growth_strategy.json").read_text(encoding="utf-8"))

UI = {
    "en": {
        "report": "Growth Strategy Report",
        "prepared": "Prepared",
        "repo": "Repository",
        "version": "Version",
        "confidence": "Confidence",
        "author": "Author",
        "baseline": "Baseline valuation (today)",
        "target": "Target valuation (initiatives shipped)",
        "multiple": "Value multiple",
        "matrix": "Summary Matrix",
        "rank": "#",
        "initiative": "Initiative",
        "impact": "Valuation impact",
        "effort": "Effort",
        "kpi": "Primary KPI to move",
        "buildable": "Buildable in current app",
        "yes": "yes",
        "roadmap": "roadmap",
        "toc": "Contents",
        "impl": "Implementation prompt (paste-ready for an AI coding assistant)",
        "metrics": "Success metrics",
        "metric": "Metric",
        "targetv": "Target",
        "regen": "Regeneration prompt",
        "totals": "Portfolio Total",
        "totals_body": "Sum of the 10 initiative impact ranges: **{sum}**. Applied to the baseline of **{base}**, the portfolio supports the target valuation of **{tgt}** ({mult}).",
        "assumptions": "Valuation Assumptions",
        "a_method": "Baseline method",
        "a_target": "Target method",
        "a_currency": "Currency basis",
        "a_uncert": "Biggest uncertainty",
        "effort_l": {"S": "S (small)", "M": "M (medium)", "L": "L (large)"},
        "generated_note": "Generated from `data/project.json` + `data/growth_strategy.json` on {date}. The PDF renderer (`generate_growth_pdf.py`) is not bundled in this toolkit copy; this Markdown edition is the canonical deliverable.",
    },
    "hu": {
        "report": "Növekedési Stratégia Jelentés",
        "prepared": "Készült",
        "repo": "Repository",
        "version": "Verzió",
        "confidence": "Megbízhatóság",
        "author": "Szerző",
        "baseline": "Jelenlegi értékelés (ma)",
        "target": "Cél-értékelés (kezdeményezések leszállítva)",
        "multiple": "Érték-szorzó",
        "matrix": "Összefoglaló mátrix",
        "rank": "#",
        "initiative": "Kezdeményezés",
        "impact": "Értékelési hatás",
        "effort": "Ráfordítás",
        "kpi": "Elsődleges mozgatott KPI",
        "buildable": "Építhető a jelenlegi appban",
        "yes": "igen",
        "roadmap": "roadmap",
        "toc": "Tartalom",
        "impl": "Implementációs prompt (AI kódoló asszisztensbe beilleszthető)",
        "metrics": "Sikermetrikák",
        "metric": "Metrika",
        "targetv": "Cél",
        "regen": "Újragenerálási prompt",
        "totals": "Portfólió összesen",
        "totals_body": "A 10 kezdeményezés hatástartományának összege: **{sum}**. A(z) **{base}** kiindulási értékre vetítve a portfólió a(z) **{tgt}** cél-értékelést támasztja alá ({mult}).",
        "assumptions": "Értékelési feltételezések",
        "a_method": "Kiindulási módszer",
        "a_target": "Cél módszer",
        "a_currency": "Deviza-alap",
        "a_uncert": "Legnagyobb bizonytalanság",
        "effort_l": {"S": "S (kicsi)", "M": "M (közepes)", "L": "L (nagy)"},
        "generated_note": "Generálva a `data/project.json` + `data/growth_strategy.json` fájlokból, {date}. A PDF renderer (`generate_growth_pdf.py`) ebben a toolkit-példányban nincs csomagolva; ez a Markdown kiadás a kanonikus leszállítandó.",
    },
}


def parse_value(v: str):
    m = re.match(r"\+€(\d+)k–€(\d+)k", v)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def slug(s: str) -> str:
    s = re.sub(r"[^\w\s-]", "", s.lower())
    return re.sub(r"[\s]+", "-", s).strip("-")


def render(lang: str) -> str:
    ui = UI[lang]
    inits = GROWTH["initiatives"]
    lo = sum(parse_value(i["value"])[0] for i in inits)
    hi = sum(parse_value(i["value"])[1] for i in inits)
    total = f"+€{lo / 1000:.2f}M–€{hi / 1000:.2f}M".replace(".00M", ".0M")

    title = PROJECT["title_translations"][lang].replace("\n", " — ")
    sub = PROJECT["subtitle_translations"][lang]
    out = []
    w = out.append

    w(f"# {PROJECT['product_name']} — {title}")
    w("")
    w(f"### {sub}")
    w("")
    w(f"*{PROJECT['tagline_translations'][lang]}*")
    w("")
    w(f"| | |")
    w(f"|---|---|")
    w(f"| {ui['prepared']} | {PROJECT['prepared_date']} |")
    w(f"| {ui['repo']} | `{PROJECT['repository']}` |")
    w(f"| {ui['version']} | v{PROJECT['version']} |")
    w(f"| {ui['baseline']} | **{PROJECT['baseline_valuation']}** |")
    w(f"| {ui['target']} | **{PROJECT['target_valuation']}** |")
    w(f"| {ui['multiple']} | **{PROJECT['value_multiple']}** |")
    w(f"| {ui['confidence']} | {PROJECT['confidence_label']} |")
    w(f"| {ui['author']} | {PROJECT['author']} |")
    w("")
    w(f"> {ui['generated_note'].format(date=PROJECT['prepared_date'])}")
    w("")

    # Summary matrix
    w(f"## {ui['matrix']}")
    w("")
    w(f"| {ui['rank']} | {ui['initiative']} | {ui['impact']} | {ui['effort']} | {ui['buildable']} | {ui['kpi']} |")
    w("|---:|---|---|---|---|---|")
    for n, i in enumerate(inits, 1):
        t = i["translations"][lang]
        kpi0 = f"{t['metrics'][0][0]}: {t['metrics'][0][1]}"
        b = ui["yes"] if i.get("build_in_app") else ui["roadmap"]
        w(f"| {n} | {t['title']} | **{i['value']}** | {ui['effort_l'][i['effort']]} | {b} | {kpi0} |")
    w(f"| | **{ui['totals']}** | **{total}** | | | |")
    w("")

    # TOC
    w(f"## {ui['toc']}")
    w("")
    for n, i in enumerate(inits, 1):
        t = i["translations"][lang]
        w(f"{n}. [{t['title']}](#{slug(str(n) + ' ' + t['title'])}) — **{i['value']}**")
    w("")
    w("---")
    w("")

    # Initiatives
    for n, i in enumerate(inits, 1):
        t = i["translations"][lang]
        w(f"## {n}. {t['title']}")
        w("")
        w(f"**{ui['impact']}: {i['value']}** · {ui['effort']}: {ui['effort_l'][i['effort']]} · "
          f"{ui['buildable']}: {ui['yes'] if i.get('build_in_app') else ui['roadmap']}")
        w("")
        for p in t["desc"]:
            w(p)
            w("")
        w(f"### {ui['impl']}")
        w("")
        for k, step in enumerate(t["impl"], 1):
            w(f"{k}. {step}")
        w("")
        w(f"### {ui['metrics']}")
        w("")
        w(f"| {ui['metric']} | {ui['targetv']} |")
        w("|---|---|")
        for m in t["metrics"]:
            w(f"| {m[0]} | **{m[1]}** |")
        w("")
        w(f"### {ui['regen']}")
        w("")
        w("```")
        w(t["regen"])
        w("```")
        w("")
        w("---")
        w("")

    # Totals + assumptions
    w(f"## {ui['totals']}")
    w("")
    w(ui["totals_body"].format(sum=total, base=PROJECT["baseline_valuation"],
                               tgt=PROJECT["target_valuation"], mult=PROJECT["value_multiple"]))
    w("")
    a = PROJECT.get("valuation_assumptions", {})
    if a:
        w(f"## {ui['assumptions']}")
        w("")
        w(f"- **{ui['a_method']}:** {a.get('method', '')}")
        w(f"- **{ui['a_target']}:** {a.get('target_method', '')}")
        w(f"- **{ui['a_currency']}:** {a.get('currency_basis', '')}")
        w(f"- **{ui['a_uncert']}:** {a.get('biggest_uncertainty', '')}")
        w("")
    return "\n".join(out)


def main():
    outdir = HERE / "output"
    outdir.mkdir(exist_ok=True)
    for lang in PROJECT["languages"]:
        path = outdir / f"{PROJECT['output_slug']}-{lang}.md"
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(render(lang))
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
