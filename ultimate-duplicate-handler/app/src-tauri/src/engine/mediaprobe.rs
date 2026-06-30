//! Media integrity & technical-quality probe (ffprobe-backed).
//!
//! Best-practice split (per the product brief): use **ffprobe** to (a) decide whether a
//! media file can be read at all — catching truncated/corrupt containers and unreadable
//! streams — and (b) read the technical metadata (duration, resolution, bitrate, codec,
//! fps, audio presence, stream count). From those signals we derive a coarse *integrity*
//! status and a *quality* grade with human-readable, deliberately hedged ("likely…")
//! warnings. Results are CACHED in `media_meta`, so re-analysis is incremental — only
//! newly-seen media is probed (the same history-aware advantage as the hash cache).
//!
//! ffprobe (part of FFmpeg) is an OPTIONAL external dependency. If it is not found — next
//! to the EXE (portable) or on PATH — the pass degrades gracefully: nothing is analyzed and
//! the UI prompts the user to install it. We NEVER mark a file corrupt merely because
//! ffprobe is missing (that would be a false accusation — fail loud, don't fail wrong).
//!
//! `deep=true` adds an optional, slow full-decode pass via ffmpeg (`-f null`) that actually
//! decodes every frame to catch mid-file corruption ffprobe's header scan would miss.

use crate::model::{FfprobeStatus, MediaMeta};
use rayon::prelude::*;
use serde_json::Value;
use std::path::Path;
use std::process::{Command, Output, Stdio};
use std::time::Duration;
use wait_timeout::ChildExt;

const MB: f64 = 1_048_576.0;
/// Per-file ffprobe timeout (header scan is fast; this only catches hangs — a malformed file
/// ffprobe gets stuck on, or a drive that went away mid-pass). One bad file must never stall
/// the whole analysis. The deep full-decode pass gets a much larger budget (it reads the file).
pub const PROBE_TIMEOUT_SECS: u64 = 25;
pub const DEEP_TIMEOUT_SECS: u64 = 300;

/// What kind of file we're analyzing — decides ffprobe (media) vs a generic integrity check.
#[derive(Clone, Copy, PartialEq, Eq)]
enum MediaKind {
    Video,
    Audio,
    Image,
    Other,
}

const VIDEO_EXT_SET: &[&str] = &[
    "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "m2ts", "ts", "mts",
    "3gp", "vob", "ogv", "divx", "asf", "rm", "rmvb",
];
const AUDIO_EXT_SET: &[&str] = &[
    "mp3", "flac", "wav", "aac", "m4a", "ogg", "oga", "opus", "wma", "aiff", "aif", "alac", "ape",
    "ac3", "dts", "amr",
];
const IMAGE_EXT_SET: &[&str] = &[
    "jpg", "jpeg", "jfif", "png", "gif", "bmp", "tiff", "tif", "webp", "heic", "heif", "avif",
];

fn classify_ext(ext: &str) -> MediaKind {
    let e = ext.trim().trim_start_matches('.').to_lowercase();
    if VIDEO_EXT_SET.contains(&e.as_str()) {
        MediaKind::Video
    } else if AUDIO_EXT_SET.contains(&e.as_str()) {
        MediaKind::Audio
    } else if IMAGE_EXT_SET.contains(&e.as_str()) {
        MediaKind::Image
    } else {
        MediaKind::Other
    }
}

enum RunErr {
    TimedOut,
    Spawn(String),
}

/// Run a child process to completion with a hard timeout; kill it if it overruns. ffprobe's
/// JSON output is small, so reading the pipes after exit cannot deadlock.
fn run_with_timeout(mut cmd: Command, secs: u64) -> Result<Output, RunErr> {
    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = cmd.spawn().map_err(|e| RunErr::Spawn(e.to_string()))?;
    match child.wait_timeout(Duration::from_secs(secs)) {
        Ok(Some(_status)) => child.wait_with_output().map_err(|e| RunErr::Spawn(e.to_string())),
        Ok(None) => {
            let _ = child.kill();
            let _ = child.wait();
            Err(RunErr::TimedOut)
        }
        Err(e) => Err(RunErr::Spawn(e.to_string())),
    }
}

fn now_secs() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// Suppress the console window that spawning a CLI tool would otherwise flash on Windows.
#[cfg(windows)]
fn hide_window(cmd: &mut Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    cmd.creation_flags(CREATE_NO_WINDOW);
}
#[cfg(not(windows))]
fn hide_window(_cmd: &mut Command) {}

/// Locate a runnable copy of `tool` (e.g. "ffprobe"): bundled beside the EXE first (so a
/// portable install can ship its own), then PATH. `None` if no candidate runs `-version`.
fn resolve_tool(tool: &str) -> Option<String> {
    let exe_name = if cfg!(windows) { format!("{tool}.exe") } else { tool.to_string() };
    let mut candidates: Vec<String> = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            for rel in [
                exe_name.clone(),
                format!("bin/{exe_name}"),
                format!("ffmpeg/bin/{exe_name}"),
                format!("ffmpeg/{exe_name}"),
            ] {
                let p = dir.join(rel);
                if p.exists() {
                    candidates.push(p.to_string_lossy().into_owned());
                }
            }
        }
    }
    candidates.push(tool.to_string()); // PATH fallback (bare name)
    for c in candidates {
        let mut cmd = Command::new(&c);
        cmd.arg("-version");
        hide_window(&mut cmd);
        if let Ok(out) = cmd.output() {
            if out.status.success() {
                return Some(c);
            }
        }
    }
    None
}

pub fn resolve_ffprobe() -> Option<String> {
    resolve_tool("ffprobe")
}

/// Locate a runnable ffmpeg (for keyframe sampling / video thumbnails — initiative #4).
pub fn resolve_ffmpeg() -> Option<String> {
    resolve_tool("ffmpeg")
}

/// Availability + version of the ffprobe backend (for the Settings indicator).
pub fn status() -> FfprobeStatus {
    match resolve_ffprobe() {
        None => FfprobeStatus::default(),
        Some(path) => {
            let mut cmd = Command::new(&path);
            cmd.arg("-version");
            hide_window(&mut cmd);
            let version = cmd.output().ok().and_then(|o| {
                String::from_utf8_lossy(&o.stdout)
                    .lines()
                    .next()
                    .map(|l| l.trim().to_string())
            });
            FfprobeStatus { available: true, path: Some(path), version }
        }
    }
}

fn parse_fps(s: &str) -> Option<f64> {
    let (n, d) = s.split_once('/')?;
    let n: f64 = n.trim().parse().ok()?;
    let d: f64 = d.trim().parse().ok()?;
    if d == 0.0 {
        return None;
    }
    let v = n / d;
    if v > 0.0 {
        Some(v)
    } else {
        None
    }
}

/// ffprobe emits numbers sometimes as JSON numbers, sometimes as quoted strings.
fn num(v: &Value) -> Option<f64> {
    match v {
        Value::Number(n) => n.as_f64(),
        Value::String(s) => s.trim().parse().ok(),
        _ => None,
    }
}

/// Probe one media file with ffprobe: integrity verdict + technical metadata + quality grade.
fn probe(ffprobe: &str, path: &Path, size_bytes: i64, deep: bool, kind: MediaKind) -> MediaMeta {
    let mut cmd = Command::new(ffprobe);
    cmd.args(["-v", "error", "-show_format", "-show_streams", "-of", "json"]);
    cmd.arg(path);
    hide_window(&mut cmd);

    let output = match run_with_timeout(cmd, PROBE_TIMEOUT_SECS) {
        Ok(o) => o,
        Err(RunErr::TimedOut) => {
            // A hung probe is suspicious but not a proven verdict — surface it, don't lie.
            return MediaMeta {
                probe_state: "error".into(),
                integrity: "suspicious".into(),
                duration_s: None, width: None, height: None, bitrate: None, codec: None,
                fps: None, has_audio: false, stream_count: None,
                quality_grade: "unknown".into(), quality_warning: true,
                warn_reason: Some(format!("Probe timed out after {PROBE_TIMEOUT_SECS}s — file may be on a slow/disconnected drive or malformed")),
                analyzed_at: now_secs(),
            };
        }
        Err(RunErr::Spawn(e)) => {
            return MediaMeta {
                probe_state: "error".into(),
                integrity: "unreadable".into(),
                duration_s: None, width: None, height: None, bitrate: None, codec: None,
                fps: None, has_audio: false, stream_count: None,
                quality_grade: "unknown".into(), quality_warning: true,
                warn_reason: Some(format!("Could not run ffprobe: {e}")),
                analyzed_at: now_secs(),
            };
        }
    };
    let stderr = String::from_utf8_lossy(&output.stderr).to_lowercase();
    let json: Option<Value> = serde_json::from_slice(&output.stdout).ok();

    let mut width = None;
    let mut height = None;
    let mut codec = None;
    let mut fps = None;
    let mut has_audio = false;
    let mut has_video = false;
    let mut stream_count = None;
    let mut duration_s = None;
    let mut container_bitrate = None;

    if let Some(j) = &json {
        if let Some(streams) = j.get("streams").and_then(|s| s.as_array()) {
            stream_count = Some(streams.len() as i64);
            for st in streams {
                match st.get("codec_type").and_then(|c| c.as_str()) {
                    Some("video") => {
                        // Skip cover-art "video" streams (attached_pic) — they are a single
                        // still and would otherwise masquerade as the video track.
                        let attached = st
                            .get("disposition")
                            .and_then(|d| d.get("attached_pic"))
                            .and_then(|a| a.as_i64())
                            .unwrap_or(0);
                        if attached == 1 {
                            continue;
                        }
                        if !has_video {
                            has_video = true;
                            width = st.get("width").and_then(|v| v.as_i64());
                            height = st.get("height").and_then(|v| v.as_i64());
                            codec = st.get("codec_name").and_then(|v| v.as_str()).map(String::from);
                            fps = st
                                .get("avg_frame_rate")
                                .and_then(|v| v.as_str())
                                .and_then(parse_fps)
                                .or_else(|| st.get("r_frame_rate").and_then(|v| v.as_str()).and_then(parse_fps));
                        }
                    }
                    Some("audio") => has_audio = true,
                    _ => {}
                }
            }
        }
        if let Some(fmt) = j.get("format") {
            duration_s = fmt.get("duration").and_then(num).filter(|d| *d > 0.0);
            container_bitrate = fmt.get("bit_rate").and_then(num).map(|b| b as i64);
            if stream_count.is_none() {
                stream_count = fmt.get("nb_streams").and_then(|v| v.as_i64());
            }
        }
    }

    // Effective bitrate: prefer the container's declared value; otherwise derive it from
    // size/duration so the quality heuristics still have a signal.
    let bitrate = container_bitrate.or_else(|| duration_s.map(|d| ((size_bytes as f64) * 8.0 / d) as i64));

    // --- Integrity verdict (header-scan level) ---
    let nothing_readable = json.is_none() || (!has_video && !has_audio && stream_count.unwrap_or(0) == 0);
    let truncated = ["moov atom not found", "truncat", "partial file", "end of file", "premature end"]
        .iter()
        .any(|k| stderr.contains(k));
    let corrupt_kw = ["invalid data", "corrupt", "invalid nal", "error while decoding", "non-existing", "could not find codec parameters"]
        .iter()
        .any(|k| stderr.contains(k));
    let exit_ok = output.status.success();

    let mut integrity = if nothing_readable {
        "unreadable"
    } else if truncated {
        "partial"
    } else if corrupt_kw {
        "corrupted"
    } else if !exit_ok || !stderr.is_empty() {
        "suspicious"
    } else if duration_s.is_none() && has_video && kind == MediaKind::Video {
        // A VIDEO with no duration is suspicious; an image legitimately has a single video
        // stream and no duration, so this rule must not fire for images.
        "suspicious"
    } else {
        "healthy"
    }
    .to_string();
    let probe_state = if nothing_readable { "error" } else { "ok" }.to_string();

    // --- Optional deep decode (slow; reads & decodes the whole file via ffmpeg) ---
    if deep && (integrity == "healthy" || integrity == "suspicious") {
        if let Some(verdict) = deep_decode_check(path) {
            integrity = verdict;
        }
    }

    let (quality_grade, quality_warning, warn_reason) =
        grade_quality(&integrity, kind, width, height, bitrate, duration_s, size_bytes);

    MediaMeta {
        probe_state, integrity, duration_s, width, height, bitrate, codec, fps,
        has_audio, stream_count, quality_grade, quality_warning, warn_reason,
        analyzed_at: now_secs(),
    }
}

/// Full-decode integrity check (ffmpeg `-f null`). Returns a downgraded verdict if decoding
/// surfaces errors, `None` if the decode is clean or ffmpeg is unavailable (so a missing
/// ffmpeg never changes the verdict).
fn deep_decode_check(path: &Path) -> Option<String> {
    let ffmpeg = resolve_tool("ffmpeg")?;
    let mut cmd = Command::new(ffmpeg);
    cmd.args(["-v", "error", "-xerror", "-i"]);
    cmd.arg(path);
    cmd.args(["-f", "null", "-"]);
    hide_window(&mut cmd);
    let out = run_with_timeout(cmd, DEEP_TIMEOUT_SECS).ok()?; // timeout/spawn err => no verdict change
    let err = String::from_utf8_lossy(&out.stderr).to_lowercase();
    if out.status.success() && err.is_empty() {
        return None;
    }
    if ["truncat", "partial", "end of file", "premature"].iter().any(|k| err.contains(k)) {
        Some("partial".into())
    } else {
        Some("corrupted".into())
    }
}

/// Coarse, deliberately-hedged technical-quality grade. Returns (grade, is_warning, reason).
/// Heuristics — not ground truth: a technically valid file can still be a poor copy.
/// Grading is kind-aware: video by bitrate/resolution/size-for-duration, image by resolution,
/// audio by bitrate. (Non-media never reaches here — it uses `generic_integrity`.)
fn grade_quality(
    integrity: &str,
    kind: MediaKind,
    w: Option<i64>,
    h: Option<i64>,
    bitrate: Option<i64>,
    dur: Option<f64>,
    size: i64,
) -> (String, bool, Option<String>) {
    // Integrity problems dominate — a damaged file is "poor" regardless of its numbers.
    if integrity == "corrupted" || integrity == "unreadable" || integrity == "partial" {
        return ("poor".into(), true, Some(format!("File appears {integrity} — may not play fully")));
    }
    let sus = integrity == "suspicious";

    // --- Image: grade purely by resolution (pixel count). ---
    if kind == MediaKind::Image {
        let px = w.unwrap_or(0) * h.unwrap_or(0);
        let (grade, reason) = if px == 0 {
            ("unknown", None)
        } else if px < 160_000 {
            // < ~0.16 MP (e.g. under 400×400) — thumbnail-grade.
            ("poor", Some(format!("low resolution ({}x{})", w.unwrap_or(0), h.unwrap_or(0))))
        } else if px >= 1_500_000 {
            ("good", None)
        } else {
            ("fair", None)
        };
        let warning = grade == "poor" || sus;
        let r = reason
            .map(|x| format!("Likely poor quality: {x}"))
            .or_else(|| sus.then(|| "Probed with warnings — file may be damaged".to_string()));
        return (grade.into(), warning, r);
    }

    // --- Audio: grade by bitrate. ---
    if kind == MediaKind::Audio {
        let kbps = bitrate.map(|b| b as f64 / 1000.0);
        let (grade, reason) = match kbps {
            None => ("unknown", None),
            Some(k) if k < 96.0 => ("poor", Some(format!("very low bitrate ({k:.0} kbps)"))),
            Some(k) if k >= 192.0 => ("good", None),
            Some(_) => ("fair", None),
        };
        let warning = grade == "poor" || sus;
        let r = reason
            .map(|x| format!("Likely poor quality: {x}"))
            .or_else(|| sus.then(|| "Probed with warnings — file may be damaged".to_string()));
        return (grade.into(), warning, r);
    }

    // --- Video (default): bitrate / resolution / size-for-duration. ---
    let height = h.unwrap_or(0);
    let mbps = bitrate.map(|b| b as f64 / 1_000_000.0);
    let duration = dur.unwrap_or(0.0);
    let mb_per_min = (duration > 0.0).then(|| (size as f64 / MB) / (duration / 60.0));
    let mins = duration / 60.0;

    let mut reasons: Vec<String> = Vec::new();
    // The brief's headline case: long runtime but tiny file => suspiciously low quality.
    if let Some(mpm) = mb_per_min {
        if duration > 600.0 && mpm < 6.0 {
            reasons.push(format!("only {mpm:.1} MB/min over {mins:.0} min — suspiciously small for the length"));
        }
    }
    if let Some(m) = mbps {
        if m < 0.75 {
            reasons.push(format!("very low bitrate ({m:.2} Mbps)"));
        } else if height >= 1080 && m < 2.0 {
            reasons.push(format!("low bitrate ({m:.2} Mbps) for {height}p — likely heavily re-compressed"));
        }
    }
    if height > 0 && height <= 360 {
        reasons.push(format!("low resolution ({}x{height})", w.unwrap_or(0)));
    }

    let poor = !reasons.is_empty();
    let good = !poor && height >= 720 && mbps.map(|m| m >= 2.5).unwrap_or(false);
    let grade = if poor { "poor" } else if good { "good" } else { "fair" };

    let warning = poor || integrity == "suspicious";
    let reason = if !reasons.is_empty() {
        Some(format!("Likely poor quality: {}", reasons.join("; ")))
    } else if integrity == "suspicious" {
        Some("Probed with warnings — file may be slightly damaged".to_string())
    } else {
        None
    };
    (grade.into(), warning, reason)
}

/// Lightweight integrity check for NON-media files (the engine never quality-grades these).
/// Honest by design: readable + non-empty => healthy; empty => suspicious; can't open/read =>
/// unreadable. It must NEVER false-accuse a valid file (e.g. a perfectly good .zip), so it only
/// checks that the bytes are reachable — not their format.
fn generic_integrity(path: &Path, size_bytes: i64) -> MediaMeta {
    let (integrity, warn_reason): (&str, Option<String>) = if size_bytes == 0 {
        ("suspicious", Some("File is empty (0 bytes)".into()))
    } else {
        match std::fs::File::open(path) {
            Ok(mut f) => {
                use std::io::Read;
                let mut buf = [0u8; 1];
                match f.read(&mut buf) {
                    Ok(_) => ("healthy", None),
                    Err(e) => ("unreadable", Some(format!("File could not be read: {e}"))),
                }
            }
            Err(e) => ("unreadable", Some(format!("File could not be opened: {e}"))),
        }
    };
    MediaMeta {
        probe_state: "unsupported".into(),
        integrity: integrity.into(),
        duration_s: None, width: None, height: None, bitrate: None, codec: None, fps: None,
        has_audio: false, stream_count: None,
        quality_grade: "unknown".into(),
        quality_warning: integrity != "healthy",
        warn_reason,
        analyzed_at: now_secs(),
    }
}

/// Analyze ONE file: ffprobe for media (video/audio/image), a generic integrity check for
/// everything else. `ffprobe` may be "" when unavailable — media files then fall back to the
/// generic check rather than failing (we just can't grade their quality).
fn analyze_one(ffprobe: &str, path: &Path, ext: &str, size_bytes: i64, deep: bool) -> MediaMeta {
    match classify_ext(ext) {
        MediaKind::Other => generic_integrity(path, size_bytes),
        kind if ffprobe.is_empty() => {
            let _ = kind;
            generic_integrity(path, size_bytes)
        }
        kind => probe(ffprobe, path, size_bytes, deep, kind),
    }
}

/// Analyze a small batch of files IN PARALLEL. Returns (file_id, metadata) per item. No DB
/// connection is touched here — the caller persists the results in a brief, batched write so
/// the analysis never monopolizes the single writer lock (that monopoly was what made a rename
/// wait 11 minutes and the pass look hung). `items` = (file_id, absolute_path, ext, size).
pub fn probe_many(ffprobe: &str, items: &[(i64, String, String, i64)], deep: bool) -> Vec<(i64, MediaMeta)> {
    items
        .par_iter()
        .map(|(id, path, ext, size)| (*id, analyze_one(ffprobe, Path::new(path), ext, *size, deep)))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fps_parses_ntsc_fraction() {
        let v = parse_fps("30000/1001").unwrap();
        assert!((v - 29.97).abs() < 0.01);
        assert_eq!(parse_fps("25/1"), Some(25.0));
        assert_eq!(parse_fps("0/0"), None);
    }

    #[test]
    fn small_long_video_is_flagged_poor() {
        // The brief's example: 1:33:40 (5620s) but only 400 MB.
        let size = 400 * 1024 * 1024;
        let (grade, warn, reason) = grade_quality("healthy", MediaKind::Video, Some(1280), Some(720), None, Some(5620.0), size);
        assert_eq!(grade, "poor");
        assert!(warn);
        assert!(reason.unwrap().contains("MB/min"));
    }

    #[test]
    fn healthy_hd_is_good() {
        // 45 min 1080p at ~6 Mbps => ~2 GB. Healthy, good.
        let size = 2_000i64 * 1024 * 1024;
        let (grade, warn, _r) = grade_quality("healthy", MediaKind::Video, Some(1920), Some(1080), Some(6_000_000), Some(2700.0), size);
        assert_eq!(grade, "good");
        assert!(!warn);
    }

    #[test]
    fn corrupted_overrides_to_poor_warning() {
        let (grade, warn, reason) = grade_quality("corrupted", MediaKind::Video, Some(1920), Some(1080), Some(8_000_000), Some(3600.0), 4_000_000_000);
        assert_eq!(grade, "poor");
        assert!(warn);
        assert!(reason.unwrap().contains("corrupted"));
    }

    #[test]
    fn low_res_is_poor() {
        let (grade, warn, _r) = grade_quality("healthy", MediaKind::Video, Some(320), Some(240), Some(800_000), Some(300.0), 30_000_000);
        assert_eq!(grade, "poor");
        assert!(warn);
    }
}
