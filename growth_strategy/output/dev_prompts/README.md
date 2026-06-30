# Jupiter — Growth Initiative Development Prompts

Ten ultra-detailed, ready-to-paste development prompts — one per growth
initiative in the Jupiter growth strategy report. Each prompt is written so
that an AI coding agent (Claude Code, Cursor, Windsurf, etc.) can implement the
initiative end-to-end for **Jupiter** (`com.jupiter.filemanager`, native
Kotlin / Compose / Material 3 / MVVM + Clean / Hilt Android file manager).

**Valuation thesis:** baseline build-cost-plus-IP of **€140k–€320k** → target
**€1.4M–€3.2M** once these initiatives ship (an 8–10× re-rating from a
pre-revenue MVP to a revenue-generating, feature-complete product).

## Prompts

| # | Initiative | Value | File |
|---|------------|-------|------|
| 1 | Jupiter Pro — Monetization & Entitlement Engine | **+€350k–€700k** | [01_jupiter-pro-monetization.md](01_jupiter-pro-monetization.md) |
| 2 | Real Cloud Storage — Google Drive / Dropbox / OneDrive (OAuth + REST) | **+€220k–€420k** | [02_cloud-storage-oauth.md](02_cloud-storage-oauth.md) |
| 3 | Localization & Global Reach (12+ languages) | **+€120k–€260k** | [03_localization-global-reach.md](03_localization-global-reach.md) |
| 4 | AI Pro Suite (Claude) — Summarize, Smart-Rename, Auto-Organize, Semantic Search | **+€160k–€320k** | [04_ai-pro-suite.md](04_ai-pro-suite.md) |
| 5 | Home-screen Widgets, App Shortcuts & Quick-Settings Tile | **+€90k–€180k** | [05_widgets-shortcuts-tile.md](05_widgets-shortcuts-tile.md) |
| 6 | Privacy-first Opt-in Product Analytics & Crash Reporting | **+€80k–€170k** | [06_privacy-analytics-crash.md](06_privacy-analytics-crash.md) |
| 7 | Activation & Retention Loop (Onboarding, What's-New, Rating, Re-engagement) | **+€90k–€190k** | [07_activation-retention-loop.md](07_activation-retention-loop.md) |
| 8 | High-performance Search Index (Room FTS) + Saved Searches + Content Search | **+€110k–€220k** | [08_search-index-fts.md](08_search-index-fts.md) |
| 9 | Personalization & Theming (Material You Accent, Icon/Density Themes, Custom Home) | **+€70k–€150k** | [09_personalization-theming.md](09_personalization-theming.md) |
| 10 | Share & Interop Hub (Save to Jupiter, Quick-Share, SAF DocumentsProvider) | **+€100k–€200k** | [10_share-interop-hub.md](10_share-interop-hub.md) |

## How to use

1. Pick the initiative you want to ship. Initiative **1 (Jupiter Pro)** is the
   single biggest valuation lever and the recommended starting point, since
   most other prompts feed its paywall.
2. Open the matching `.md` file and **paste its entire contents** into an AI
   coding agent (Claude Code, Cursor, Windsurf, etc.) with the Jupiter
   repository open as the working directory.
3. Let the agent work through the prompt's phases in order (codebase context →
   pre-conditions → implementation phases → testing → definition of done). Each
   prompt references real file paths in the repo and is self-contained — it is
   designed to require zero follow-up questions.
4. Review the resulting diff, run the smoke test in the prompt's testing
   section, and use the prompt's rollback plan if anything needs reverting.

> Prompts are independent and can be run in any order, but several reference
> the entitlement gates from initiative 1 — implement Jupiter Pro first to get
> the most leverage out of the rest.
