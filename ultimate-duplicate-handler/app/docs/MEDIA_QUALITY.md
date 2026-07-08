# Media integrity & quality (and duplicate keeper hints)

Sift can probe your **video files** to answer the questions a duplicate cleanup actually
needs: *is this file complete? is it damaged? is it suspiciously low quality for its length?
and when two copies exist, which one should I keep?* These signals appear as columns in
**Index Explorer** and in the **Duplicates** view, and they are filterable and sortable.

This feature is **additive and safe**: it only reads files (via ffprobe), never modifies
them, and it changes nothing about how scanning, indexing, or deletion work.

---

## Enabling it — ffprobe

Analysis is powered by **ffprobe** (part of [FFmpeg](https://ffmpeg.org/ffprobe.html)), the
industry-standard media inspector. It is an *optional* dependency:

- **Install system-wide:** `winget install Gyan.FFmpeg` (Windows), then reopen Sift.
- **Portable:** drop `ffprobe.exe` (and optionally `ffmpeg.exe`) **next to `Sift.exe`** — it
  then travels with the portable app folder. Sift looks beside the EXE first, then on `PATH`.

`Settings → Media analysis` shows whether ffprobe was found and its version. If it is
missing, Sift simply skips analysis and tells you how to enable it — **it never marks a file
as damaged just because ffprobe is unavailable.**

Run analysis from **Index Explorer → "Analyze media quality"**. It is **incremental**: only
newly-seen videos are probed; results are cached in the index, so re-running is instant and
history-aware (the same advantage as the rest of Sift's persistent index).

- **Analyze media quality** — fast. Reads each file's header/streams with ffprobe.
- **Deep check** — slower; fully decodes every frame with ffmpeg (`-f null`) to catch
  mid-file corruption a header scan would miss. Needs `ffmpeg` available too.

---

## Integrity — "can this file be read?"

| Status | Meaning | Typical cause |
| --- | --- | --- |
| **Healthy** | Probed cleanly; container + streams read without error. | normal file |
| **Suspicious** | Readable, but ffprobe emitted warnings, or key metadata (e.g. duration) is missing. | mild damage, unusual muxing |
| **Partial** | Looks **truncated** — the file ends early (e.g. *moov atom not found*, *premature end*). | interrupted download/copy |
| **Corrupted** | Decoder reports invalid/garbled data. | bit-rot, bad sectors, broken transfer |
| **Unreadable** | ffprobe could not parse it at all — no streams, no format. | not really a video / severely broken |

**"Warning" vs "Corrupted":** a *warning* (the ⚠ on the Quality badge) is about **quality**
— the file plays, but looks like a poor copy. *Corrupted/partial/unreadable* are about
**integrity** — the file is damaged and may not play fully. A file can be perfectly healthy
yet still carry a quality warning (e.g. a tiny low-bitrate rip), and vice-versa.

These are **heuristics**, deliberately hedged ("likely", "suspicious"). A *Suspicious* file
is often fine; treat the status as a prompt to look, not a verdict.

---

## Quality — "is this a good copy?"

For each video Sift reads **duration, resolution, bitrate, codec, frame rate, audio
presence, stream count**, derives an **effective bitrate** (from the container, or from
size ÷ duration when the container doesn't declare one), and grades it:

- **Good** — ≥ 720p **and** a healthy bitrate (≈ ≥ 2.5 Mbps).
- **Fair** — readable, unremarkable.
- **Poor** (⚠ warning) — one or more red flags below.

Red flags that drive a **Poor / warning** verdict:

- **Tiny for its length** — e.g. a *1:33:40* movie that is only *400 MB* (≈ 4 MB/min). Long
  runtime + small size ⇒ very low bitrate ⇒ likely a poor rip. *(This is the brief's
  headline case and is flagged.)*
- **Very low bitrate** — under ~0.75 Mbps.
- **HD but under-bitrate** — 1080p+ at under ~2 Mbps (likely heavily re-compressed).
- **Low resolution** — 360p or below.
- **Integrity problems** — corrupted/partial/unreadable files are always graded Poor.

The **Quality badge tooltip** spells out *why* (e.g. *"Likely poor quality: only 4.3 MB/min
over 94 min — suspiciously small for the length"*).

> Heuristics, not ground truth. A legitimately efficient HEVC encode can be small; a
> grainy film can need a high bitrate. The grade is decision **support**, not a fact.

---

## Which duplicate should I keep?

In the **Duplicates** inspector, when the copies in a group **differ** in technical quality,
Sift marks the recommended keeper with a **★** and explains it. The keeper is chosen by, in
order: **healthiest integrity → highest resolution → highest bitrate → largest file**.

Note: *exact* duplicates are byte-for-byte identical, so their media metadata is identical
too — there's nothing to choose between on quality, and no ★ is shown (pick by location).
The keeper hint matters most for **near-duplicates** — different encodes of the same content
— where one copy really is better than another.

For **photos**, the Similar Images view already recommends keeping the **largest** image in
each visually-similar group.

---

## Filtering & sorting

**Index Explorer** exposes the media signals as first-class filters and sortable columns:

- **Filter:** Integrity (healthy … unreadable), *Quality warnings only*, *Analyzed media only*.
- **Sort:** Duration, Resolution, Bitrate (plus the existing name/size/dates).
- **Columns (after Size):** Integrity · Duration · Resolution · Bitrate · Quality.

Handy recipes:

- *"Show me likely-bad videos"* → check **Quality warnings only**, sort by **Bitrate ↑**.
- *"Find damaged files"* → Integrity = **corrupted** (or **partial**/**unreadable**).
- *"Suspiciously small long videos"* → sort by **Duration ↓** and scan the Bitrate column.

---

## How it works (under the hood)

- **Probe:** `ffprobe -v error -show_format -show_streams -of json <file>` → parsed for
  streams/format. stderr is inspected for truncation/corruption keywords.
- **Deep check:** `ffmpeg -v error -xerror -i <file> -f null -` decodes every frame; any
  decode error downgrades the integrity verdict.
- **Storage:** results live in the `media_meta` table (migration `0005`), one row per file,
  cached and incremental. Removing a file cascades its media row away.
- **Engine module:** `src-tauri/src/engine/mediaprobe.rs` (probe + heuristics, unit-tested);
  surfaced via the `analyze_media` / `ffprobe_status` Tauri commands.
