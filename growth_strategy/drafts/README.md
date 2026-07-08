# Feature drafts — NOW SHIPPED ✅

> **Update:** initiatives **#1 (License), #4 (Similar-Video) and #7 (Cloud/NAS)** were
> subsequently **integrated into the app and shipped** (one at a time, compile-gated, 58/58
> tests green). The files below are kept only as the originating agent drafts for reference;
> the live, integrated versions are in `app/src` and `app/src-tauri/src`. #1 ships with the
> paywall **off** (`license::ENFORCEMENT_ENABLED = false`) so nothing is gated until launch.

---

The originals below were **agent-drafted** implementations, sidelined out of the compiled tree
to keep the build green after a concurrent multi-agent run, then integrated by hand. Each is grounded in real Sift files
and has a full paste-ready implementation prompt in `../output/growth-strategy-en.md` (the
numbered initiative) and `../data/growth_strategy.json`.

| Initiative | Files here | Why staged (the one decision needed) |
|---|---|---|
| **#1 Commercial Launch (license)** | `agent_engine/license.rs`, `agent_screens/LicenseCard.tsx` | Needs (a) a fresh Ed25519 keypair — the draft bakes a public key but the matching **private key** for minting Pro tokens must be generated and kept, and (b) the **business decision**: gating currently-free features (similar images, media analysis, bulk actions) behind Pro is a *paywall* — shipping it default-Free would remove features today's users have. Recommended: integrate with the default tier **unlocked**, flip to Free-default at commercial launch. Also needs `ed25519-dalek = "2"` in `Cargo.toml`. |
| **#4 Similar-VIDEO detection** | `agent_engine/videohash.rs`, `agent_migrations/0006_video_hash.sql`, `agent_screens/SimilarVideos.tsx`, `agent_screens/VideoThumbnail.tsx` | Large; needs the migration registered (next free version), `videohash` added to `engine/mod.rs`, a `detect_similar_videos` command, and a Similar-Images tab. Reuses the existing BK-tree (`imagehash.rs`) + bundled ffmpeg (`mediaprobe.rs`). |
| **#7 Cloud / NAS awareness** | `agent_migrations/0006_network_cloud.sql`, `agent_screens/NetworkShareInput.tsx` | Needs UNC-volume identity + a cloud-placeholder skip in `walker.rs`, and a "Add network folder" affordance in Sources. Migration registered at the next free version. |

## How to integrate one safely (the lesson)

1. Pick **one** initiative.
2. Add its new module to `engine/mod.rs`, register its migration in `db/mod.rs` (next
   `user_version`), add its command(s) to `commands.rs` + `main.rs`, its DTO(s) to `model.rs`,
   its wrappers to `contract.ts`, and its screen + nav route.
3. `cargo test --lib` + `tsc` + `vite build` after **each** initiative — never batch.
4. Do central-file edits **yourself**, one writer at a time. If you fan out to agents, either
   `git init` first (so each agent gets an isolated worktree) or have agents *return* packages
   rather than write files.

The build script for the signed installer (`scripts/build-installer.ps1`, initiative #5) is
already wired; supply an OV/EV code-signing cert via the `SIFT_CODESIGN_PFX` env var.
