# Repo Value Intelligence Toolkit

Drop this folder into **any** software repository and an AI agent will produce two
branded, presentation-quality PDF reports about that product:

1. **Software Valuation Report** — a 12-section technical due-diligence: what it
   would cost to build, which team you'd need, and what it's worth on the market.
2. **Growth Strategy Report** — a ranked set of high-leverage initiatives that
   would increase the product's valuation, each with a paste-ready implementation
   prompt.

Both reports are generated in **English and Hungarian** out of the box (more
languages are easy to add). This toolkit is fully **product-agnostic** — there is
no hard-coded reference to any specific application. It reads *your* repo and
writes reports about *your* product.

---

## How to use it (3 steps)

### 1. Drop the folder in
Copy this `growth_strategy/` folder into the root of the repository you want
reports about.

### 2. Tell your AI assistant
Open the repo in an AI-enabled editor (Cursor, Claude Code, Windsurf, Copilot
Chat, ChatGPT desktop, …) and say:

> **"Read `growth_strategy/AI_INSTRUCTIONS.md` and follow it end-to-end."**

The AI will scan the repo, fill in the JSON data files, and run the generators.

### 3. Collect the PDFs
Four files appear in `growth_strategy/output/`:

```
valuation-report-en.pdf      growth-strategy-en.pdf
valuation-report-hu.pdf      growth-strategy-hu.pdf
```

That's it. No manual data entry required — though you can edit the JSON yourself
any time and re-run.

---

## How it works

The toolkit cleanly separates **rendering** from **content**:

```
                ┌─────────────────────┐
   your repo ──▶│   scan_repo.py      │──▶ data/repo_scan.json  (objective stats)
                └─────────────────────┘
                          │
          AI reads repo + scan, writes ▼
                ┌─────────────────────┐
                │  data/project.json  │   branding, languages, valuations
                │  data/growth_*.json │   ranked growth initiatives
                │  data/valuation/*   │   per-language valuation content
                └─────────────────────┘
                          │
            pure renderers ▼ (you never edit these)
        ┌──────────────────────┐   ┌──────────────────────┐
        │ generate_growth_pdf  │   │ generate_valuation_pdf│
        └──────────────────────┘   └──────────────────────┘
                          │
                          ▼
                  output/*.pdf  (EN + HU)
```

The Python scripts are **dumb on purpose** — they only know how to turn JSON into
a polished PDF. All the intelligence (reading the repo, estimating, ranking) is
done by the AI following `AI_INSTRUCTIONS.md`. This means the output is
deterministic, re-runnable, and easy to tweak by hand.

---

## Files

| File | Purpose |
|---|---|
| `AI_INSTRUCTIONS.md` | The protocol the AI follows. **Start here if you're an AI.** |
| `generate_growth_pdf.py` | Renders the Growth Strategy PDF from JSON. |
| `generate_valuation_pdf.py` | Renders the Valuation PDF from JSON. |
| `scan_repo.py` | Scans the host repo for objective stats (LOC, stack, git history). |
| `_fonts.py` + `fonts/` | Bundled Unicode fonts (correct Hungarian ő/ű, accents, dashes). |
| `requirements.txt` | One dependency: `reportlab`. |
| `data/` | The JSON the AI fills in. Generated PDFs read from here. |
| `examples/` | A complete worked example (a fictional HR-SaaS) — reference shape. |
| `output/` | Where the generated PDFs land. |

---

## Run it manually

```bash
pip install -r requirements.txt

python scan_repo.py                              # optional: refresh repo stats
python generate_valuation_pdf.py --all-languages
python generate_growth_pdf.py    --all-languages
```

Useful flags: `--lang en` (single language), `--output path.pdf` (custom path),
`--project path.json` / `--data path.json` (custom inputs).

---

## Try the example first

The `examples/` folder contains a complete, working dataset. To see the toolkit
produce real PDFs immediately:

```bash
cp examples/project.example.json            data/project.json
cp examples/growth_strategy.example.json    data/growth_strategy.json
mkdir -p data/valuation
cp examples/valuation/en.example.json       data/valuation/en.json
cp examples/valuation/hu.example.json       data/valuation/hu.json

python generate_valuation_pdf.py --all-languages
python generate_growth_pdf.py    --all-languages
```

Then open `output/` to see what a finished report looks like. When you run the AI
on your own repo, it replaces this example data with your product's reality.

---

## Customization

- **Branding** — set `brand` colours and `logo_letter` in `data/project.json`.
- **Languages** — `en`, `hu`, `de` are built in. To add one, extend `UI_STRINGS`
  in both `generate_*.py` files and add the language to `project.json`.
- **Number of growth initiatives** — any count works; the renderer adapts.
- **Currency** — use whatever symbol fits the product's market in the JSON values.

---

## Requirements

- Python 3.10+
- `reportlab` (installed via `requirements.txt`)
- Fonts are **bundled** — no system font installation needed; works on any OS.

---

## License

MIT for the toolkit code. Bundled DejaVu fonts retain their own permissive
license — see `fonts/DejaVu-LICENSE.txt`.
