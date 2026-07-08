#!/usr/bin/env python3
"""
Repo Auto-Scanner
=================

Scans the parent repository (where this growth_strategy/ folder lives) and
collects objective signals that help the AI fill in valuation/growth JSON data:

  - File counts by language/extension
  - Lines of code (excluding deps, build artifacts, lockfiles)
  - Detected stack from manifest files (package.json, requirements.txt, ...)
  - Git activity (first/last commit, contributors) when in a git repo
  - Directory layout summary

Output: writes data/repo_scan.json (and prints a short summary).

Usage:
    python scripts/scan_repo.py
    python scripts/scan_repo.py --root /path/to/repo
    python scripts/scan_repo.py --output data/repo_scan.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

# Files / dirs to skip when counting LOC and listing
SKIP_DIRS = {
    "node_modules", "dist", "build", "out", ".next", ".nuxt", ".turbo",
    "target", "venv", ".venv", "env", "__pycache__", ".pytest_cache",
    ".mypy_cache", ".git", ".idea", ".vscode", "coverage", ".cache",
    "vendor", "Pods", ".gradle", "DerivedData", ".terraform",
    "growth_strategy", "valuation", "doc creation", "output", ".reports",
}
SKIP_FILES = {
    "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock",
    "Gemfile.lock", "composer.lock", "Cargo.lock", "go.sum",
    "bun.lockb", "Package.resolved", ".DS_Store",
}

# Extension → human-readable language
EXT_TO_LANG = {
    ".ts": "TypeScript", ".tsx": "TypeScript (TSX)",
    ".js": "JavaScript", ".jsx": "JavaScript (JSX)",
    ".mjs": "JavaScript", ".cjs": "JavaScript",
    ".py": "Python", ".rs": "Rust", ".go": "Go",
    ".java": "Java", ".kt": "Kotlin", ".swift": "Swift",
    ".rb": "Ruby", ".php": "PHP", ".cs": "C#",
    ".c": "C", ".h": "C/C++ header", ".cc": "C++", ".cpp": "C++", ".cxx": "C++",
    ".vue": "Vue", ".svelte": "Svelte",
    ".sql": "SQL", ".graphql": "GraphQL", ".gql": "GraphQL",
    ".sh": "Shell", ".bash": "Shell", ".zsh": "Shell",
    ".scss": "SCSS", ".sass": "Sass", ".less": "Less", ".css": "CSS",
    ".html": "HTML", ".htm": "HTML",
    ".md": "Markdown", ".mdx": "MDX",
    ".yaml": "YAML", ".yml": "YAML", ".toml": "TOML", ".json": "JSON",
    ".lua": "Lua", ".dart": "Dart", ".ex": "Elixir", ".exs": "Elixir",
    ".erl": "Erlang", ".clj": "Clojure", ".scala": "Scala",
    ".r": "R", ".jl": "Julia", ".pl": "Perl",
}

# Code-bearing extensions (used for LOC totals)
CODE_EXTS = {
    ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".py", ".rs", ".go",
    ".java", ".kt", ".swift", ".rb", ".php", ".cs", ".c", ".cpp", ".cc",
    ".cxx", ".h", ".hpp", ".vue", ".svelte", ".dart", ".scala", ".clj",
    ".ex", ".exs", ".lua", ".sql",
}

# Stack-indicator manifest files
STACK_INDICATORS = {
    "package.json":    "Node.js/npm",
    "yarn.lock":       "Yarn",
    "pnpm-lock.yaml":  "pnpm",
    "bun.lockb":       "Bun",
    "requirements.txt": "Python",
    "pyproject.toml":  "Python (modern)",
    "Pipfile":         "Python (pipenv)",
    "poetry.lock":     "Python (poetry)",
    "Cargo.toml":      "Rust",
    "go.mod":          "Go modules",
    "Gemfile":         "Ruby",
    "composer.json":   "PHP/Composer",
    "pom.xml":         "Java/Maven",
    "build.gradle":    "Java/Gradle",
    "build.gradle.kts": "Kotlin/Gradle",
    "Package.swift":   "Swift",
    "pubspec.yaml":    "Flutter/Dart",
    "mix.exs":         "Elixir/Mix",
    "Dockerfile":      "Docker",
    "docker-compose.yml": "Docker Compose",
    "docker-compose.yaml": "Docker Compose",
    "supabase/config.toml": "Supabase",
    "vercel.json":     "Vercel",
    "netlify.toml":    "Netlify",
    "fly.toml":        "Fly.io",
    "wrangler.toml":   "Cloudflare Workers",
    "capacitor.config.ts": "Capacitor (mobile)",
    "capacitor.config.json": "Capacitor (mobile)",
    "expo.json":       "Expo (React Native)",
    "app.json":        "Possibly Expo / React Native",
    "next.config.js":  "Next.js",
    "next.config.ts":  "Next.js",
    "vite.config.ts":  "Vite",
    "vite.config.js":  "Vite",
    "nuxt.config.ts":  "Nuxt",
    "remix.config.js": "Remix",
    "astro.config.mjs": "Astro",
    "svelte.config.js": "SvelteKit",
    "angular.json":    "Angular",
    "tsconfig.json":   "TypeScript",
    "tailwind.config.js": "Tailwind CSS",
    "tailwind.config.ts": "Tailwind CSS",
}


def run_git(args: list[str], cwd: Path) -> str | None:
    try:
        r = subprocess.run(
            ["git", *args],
            cwd=cwd, capture_output=True, text=True, timeout=15,
        )
        if r.returncode == 0:
            return r.stdout.strip()
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    return None


def count_file_loc(path: Path) -> int:
    """Best-effort line count. Returns 0 on read failure."""
    try:
        with path.open("rb") as f:
            return sum(1 for _ in f)
    except (OSError, PermissionError):
        return 0


def scan_repo(root: Path) -> dict:
    """Walk the repo and aggregate metrics."""
    file_count_by_ext: Counter = Counter()
    loc_by_ext: Counter = Counter()
    total_files = 0
    total_loc = 0
    stack_signals: set[str] = set()
    detected_manifests: list[str] = []

    for dirpath, dirnames, filenames in os.walk(root):
        # Skip directories in-place
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")]
        rel_dir = os.path.relpath(dirpath, root)
        for fn in filenames:
            if fn in SKIP_FILES or fn.startswith("."):
                continue
            full = Path(dirpath) / fn
            ext = full.suffix.lower()
            file_count_by_ext[ext] += 1
            total_files += 1

            # Manifest detection (by filename, also nested ones like supabase/config.toml)
            rel_path = str((full.relative_to(root))).replace("\\", "/")
            for marker, label in STACK_INDICATORS.items():
                if rel_path == marker or rel_path.endswith("/" + marker) or fn == marker:
                    stack_signals.add(label)
                    if marker not in detected_manifests:
                        detected_manifests.append(rel_path)

            # LOC for code files only
            if ext in CODE_EXTS:
                loc = count_file_loc(full)
                loc_by_ext[ext] += loc
                total_loc += loc

    # Group ext stats by language label
    lang_stats: dict[str, dict[str, int]] = defaultdict(lambda: {"files": 0, "loc": 0})
    for ext, count in file_count_by_ext.items():
        label = EXT_TO_LANG.get(ext, ext or "(no ext)")
        lang_stats[label]["files"] += count
        lang_stats[label]["loc"] += loc_by_ext.get(ext, 0)

    # Sort by LOC desc
    sorted_langs = sorted(
        ({"language": k, **v} for k, v in lang_stats.items()),
        key=lambda x: (x["loc"], x["files"]), reverse=True,
    )

    # Git info
    git_info = {}
    if (root / ".git").exists():
        first = run_git(["log", "--reverse", "--format=%aI", "--max-count=1"], root)
        last = run_git(["log", "-1", "--format=%aI"], root)
        commits = run_git(["rev-list", "--count", "HEAD"], root)
        contributors = run_git(["shortlog", "-sn", "--no-merges"], root)
        contributor_count = len(contributors.split("\n")) if contributors else 0
        branch = run_git(["rev-parse", "--abbrev-ref", "HEAD"], root)
        remote = run_git(["config", "--get", "remote.origin.url"], root)
        git_info = {
            "first_commit": first,
            "last_commit":  last,
            "commit_count": int(commits) if commits and commits.isdigit() else None,
            "contributor_count": contributor_count,
            "branch":       branch,
            "remote_url":   remote,
        }

    # Probe key manifests for dependency hints
    dependency_hints = []
    pkg = root / "package.json"
    if pkg.exists():
        try:
            with pkg.open("r", encoding="utf-8") as f:
                pj = json.load(f)
            deps = list((pj.get("dependencies") or {}).keys())
            dev_deps = list((pj.get("devDependencies") or {}).keys())
            dependency_hints.append({
                "manifest": "package.json",
                "name": pj.get("name"),
                "version": pj.get("version"),
                "dep_count": len(deps),
                "dev_dep_count": len(dev_deps),
                "top_deps": deps[:40],
            })
        except (json.JSONDecodeError, OSError):
            pass

    req = root / "requirements.txt"
    if req.exists():
        try:
            lines = req.read_text(encoding="utf-8", errors="ignore").splitlines()
            deps = [l.split("==")[0].split(">=")[0].strip()
                    for l in lines if l.strip() and not l.startswith("#")]
            dependency_hints.append({
                "manifest": "requirements.txt",
                "dep_count": len(deps),
                "top_deps": deps[:40],
            })
        except OSError:
            pass

    pyproject = root / "pyproject.toml"
    if pyproject.exists():
        dependency_hints.append({"manifest": "pyproject.toml"})

    return {
        "scanned_at":         datetime.now().isoformat(),
        "scan_root":          str(root),
        "total_files":        total_files,
        "total_loc":          total_loc,
        "languages":          sorted_langs[:25],
        "detected_stack":     sorted(stack_signals),
        "detected_manifests": detected_manifests,
        "dependency_hints":   dependency_hints,
        "git":                git_info,
    }


def main():
    p = argparse.ArgumentParser(description="Scan a repository for valuation/growth report inputs.")
    p.add_argument("--root", default=None,
                   help="Repo root to scan. Defaults to parent of growth_strategy/.")
    p.add_argument("--output", default="data/repo_scan.json")
    p.add_argument("--print-summary", action="store_true", default=True)
    args = p.parse_args()

    # Default root = parent of script's parent (growth_strategy/scripts/.. /..)
    if args.root:
        root = Path(args.root).resolve()
    else:
        here = Path(__file__).resolve()
        root = here.parent.parent  # <toolkit>/ -> repo root
    if not root.exists():
        sys.exit(f"ERROR: scan root does not exist: {root}")

    print(f"Scanning: {root}")
    data = scan_repo(root)

    out_path = Path(args.output)
    # Resolve relative to growth_strategy/ folder (parent of scripts/)
    if not out_path.is_absolute():
        out_path = Path(__file__).resolve().parent / out_path
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"✓  Wrote {out_path}")

    if args.print_summary:
        print(f"\n— Summary —")
        print(f"  Total files: {data['total_files']:,}")
        print(f"  Total LOC:   {data['total_loc']:,}")
        print(f"  Top languages:")
        for lang in data["languages"][:6]:
            print(f"    {lang['language']:<25} files={lang['files']:>6}  loc={lang['loc']:>8,}")
        if data["detected_stack"]:
            print(f"  Detected stack: {', '.join(data['detected_stack'][:10])}")
        if data["git"].get("commit_count"):
            print(f"  Git: {data['git']['commit_count']} commits, "
                  f"{data['git']['contributor_count']} contributors, "
                  f"{data['git']['first_commit'][:10]} → {data['git']['last_commit'][:10]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
