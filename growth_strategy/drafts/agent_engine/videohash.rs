//! Similar-VIDEO detection via perceptual keyframe fingerprinting — the video sibling of
//! `imagehash.rs`. Near-duplicate video (the same clip re-encoded, re-resolutioned, trimmed
//! or re-wrapped in a different container) is invisible to byte-exact dedup because one
//! re-encode changes every byte. We instead fingerprint the *pixels*.
//!
//! Pipeline (mirrors the perceptual-image approach, lifted to a time axis):
//!   1. Sample N evenly-spaced keyframes from the video via bundled **ffmpeg** (resolved the
//!      same way `mediaprobe` resolves its tools: beside the EXE first, then PATH). Frames are
//!      scaled tiny and piped out as PNG on stdout (image2pipe) — no temp files.
//!   2. dHash each keyframe to a 64-bit gradient hash using the SAME `image_hasher` config as
//!      `imagehash::compute_dhash` (8×8 Gradient → u64 LE), so the per-frame hashes are
//!      consistent with the image pipeline.
//!   3. Aggregate the per-keyframe hashes into a fixed-length SIGNATURE (a Vec<u64>), cache it
//!      in `video_hash` (migration 0006) as a packed BLOB so re-detection is near-instant and
//!      only newly-seen videos are sampled — the history-aware moat, now for video.
//!   4. Cluster: a BK-tree over a representative key per video (the first keyframe hash) finds
//!      candidate neighbours cheaply, then a full per-keyframe VECTOR distance confirms the
//!      pair before union-find groups them — so re-encodes cluster while unrelated clips don't.
//!
//! Robustness rule (same as the probe pass): one hung or corrupt video must NEVER stall the
//! whole pass — every ffmpeg invocation runs under a hard timeout and is killed if it overruns.
//! A missing ffmpeg degrades gracefully: nothing is sampled, the UI prompts to install it.

use crate::db::repo;
use crate::model::{FileView, SimilarVideoGroup, SimilarVideoMember, SimilarVideoResult};
use rayon::prelude::*;
use rusqlite::Connection;
use std::collections::HashMap;
use std::path::Path;
use std::process::{Command, Output, Stdio};
use std::time::Duration;
use wait_timeout::ChildExt;

/// Bits per keyframe dHash (8×8 gradient hash) — matches `imagehash::HASH_BITS`.
const HASH_BITS: u32 = 64;

/// Keyframes sampled per video. A small fixed vector is enough to discriminate re-encodes of
/// the same content while keeping sampling fast (the throughput target is < ~2s/video). Kept in
/// sync with the `video_hash.keyframes` count actually stored.
pub const KEYFRAMES: u32 = 8;

/// Longest side of each sampled keyframe before hashing. dHash only needs a tiny image, and a
/// small scale keeps the PNG pipe cheap.
const FRAME_MAX_PX: u32 = 64;

/// Per-video sampling timeout. ffmpeg seeking + decoding a handful of frames is fast; this only
/// catches a hang (a malformed file ffmpeg gets stuck on, or a drive that went away mid-pass).
pub const SAMPLE_TIMEOUT_SECS: u64 = 60;

/// Suppress the console window that spawning a CLI tool would otherwise flash on Windows
/// (identical guard to `mediaprobe::hide_window`; kept local so this module is self-contained).
#[cfg(windows)]
fn hide_window(cmd: &mut Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    cmd.creation_flags(CREATE_NO_WINDOW);
}
#[cfg(not(windows))]
fn hide_window(_cmd: &mut Command) {}

/// Run a child process to completion with a hard timeout; kill it if it overruns. ffmpeg's PNG
/// output is read after exit (the pipe is drained by `wait_with_output`), so this cannot deadlock
/// for the small, bounded frames we request. None on spawn failure or timeout.
fn run_with_timeout(mut cmd: Command, secs: u64) -> Option<Output> {
    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = cmd.spawn().ok()?;
    match child.wait_timeout(Duration::from_secs(secs)) {
        Ok(Some(_status)) => child.wait_with_output().ok(),
        _ => {
            let _ = child.kill();
            let _ = child.wait();
            None
        }
    }
}

/// dHash one already-decoded keyframe to a 64-bit gradient hash. Uses the SAME `image_hasher`
/// config as `imagehash::compute_dhash` so per-frame hashes are consistent across the two passes.
fn dhash_image(img: &image::DynamicImage) -> u64 {
    let hasher = image_hasher::HasherConfig::new()
        .hash_size(8, 8)
        .hash_alg(image_hasher::HashAlg::Gradient)
        .to_hasher();
    let h = hasher.hash_image(img);
    let b = h.as_bytes();
    let mut arr = [0u8; 8];
    let n = b.len().min(8);
    arr[..n].copy_from_slice(&b[..n]);
    u64::from_le_bytes(arr)
}

/// Extract `n_keyframes` evenly-spaced frames from `path` via bundled ffmpeg and dHash each into
/// a signature vector. `None` when the video can't be sampled at all (no ffmpeg, corrupt file,
/// timeout, or zero decodable frames) — the caller records that as `state='error'`.
///
/// ffmpeg command (image2pipe to stdout, no temp files):
///   ffmpeg -v error -i <path> -vf "thumbnail,scale=64:-1" -frames:v N -vsync vfr -f image2pipe -c:v png -
/// `thumbnail` picks representative frames (more stable than a blind `select`), `scale` shrinks
/// them, and `image2pipe`+`png` concatenates the PNGs on stdout, which we split on the PNG magic.
pub fn sample_signature(ffmpeg: &str, path: &Path, n_keyframes: u32) -> Option<Vec<u64>> {
    if ffmpeg.is_empty() {
        return None;
    }
    let vf = format!("thumbnail,scale={FRAME_MAX_PX}:-1");
    let frames = n_keyframes.max(1).to_string();
    let mut cmd = Command::new(ffmpeg);
    cmd.args(["-v", "error", "-i"]);
    cmd.arg(path);
    cmd.args(["-vf", &vf, "-frames:v", &frames, "-vsync", "vfr", "-f", "image2pipe", "-c:v", "png", "-"]);
    hide_window(&mut cmd);

    let out = run_with_timeout(cmd, SAMPLE_TIMEOUT_SECS)?;
    if !out.status.success() && out.stdout.is_empty() {
        return None;
    }
    let hashes: Vec<u64> = split_png_stream(&out.stdout)
        .iter()
        .filter_map(|png| image::load_from_memory(png).ok())
        .map(|img| dhash_image(&img))
        .collect();
    if hashes.is_empty() {
        None
    } else {
        Some(hashes)
    }
}

/// A single representative keyframe of `path` as a PNG (longest side `max`), for the UI
/// preview. Extracts one frame via ffmpeg's `thumbnail` filter and pipes it out as PNG — the
/// video analogue of `imagehash::thumbnail_png`. `None` when ffmpeg is missing or the video
/// can't be decoded (the UI then shows a "no preview" placeholder, exactly like images).
pub fn video_thumbnail_png(ffmpeg: &str, path: &Path, max: u32) -> Option<Vec<u8>> {
    if ffmpeg.is_empty() {
        return None;
    }
    let vf = format!("thumbnail,scale={}:-1", max.clamp(32, 512));
    let mut cmd = Command::new(ffmpeg);
    cmd.args(["-v", "error", "-i"]);
    cmd.arg(path);
    cmd.args(["-vf", &vf, "-frames:v", "1", "-f", "image2pipe", "-c:v", "png", "-"]);
    hide_window(&mut cmd);
    let out = run_with_timeout(cmd, SAMPLE_TIMEOUT_SECS)?;
    let frame = split_png_stream(&out.stdout).into_iter().next()?;
    if frame.is_empty() {
        None
    } else {
        Some(frame)
    }
}

/// Split a concatenated PNG byte stream (ffmpeg image2pipe) into individual PNG blobs on the
/// 8-byte PNG signature. Bounded and allocation-light; tolerates a trailing partial frame.
fn split_png_stream(buf: &[u8]) -> Vec<Vec<u8>> {
    const SIG: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    let mut starts: Vec<usize> = Vec::new();
    let mut i = 0usize;
    while i + SIG.len() <= buf.len() {
        if buf[i..i + SIG.len()] == SIG {
            starts.push(i);
            i += SIG.len();
        } else {
            i += 1;
        }
    }
    let mut frames = Vec::with_capacity(starts.len());
    for (k, &s) in starts.iter().enumerate() {
        let e = starts.get(k + 1).copied().unwrap_or(buf.len());
        frames.push(buf[s..e].to_vec());
    }
    frames
}

// --------------------------------------------------------------------------
// Signature <-> BLOB packing (keyframes × 8 bytes, little-endian) + distance
// --------------------------------------------------------------------------

/// Pack a signature (Vec<u64>) into the `video_hash.signature` BLOB layout: each u64 as 8 LE
/// bytes, concatenated. The exact inverse of `unpack_signature`.
pub fn pack_signature(sig: &[u64]) -> Vec<u8> {
    let mut out = Vec::with_capacity(sig.len() * 8);
    for h in sig {
        out.extend_from_slice(&h.to_le_bytes());
    }
    out
}

/// Decode a `video_hash.signature` BLOB back into a Vec<u64> (8 LE bytes per keyframe). A
/// trailing partial chunk (should never happen) is ignored.
pub fn unpack_signature(blob: &[u8]) -> Vec<u64> {
    blob.chunks_exact(8)
        .map(|c| {
            let mut arr = [0u8; 8];
            arr.copy_from_slice(c);
            u64::from_le_bytes(arr)
        })
        .collect()
}

/// Distance between two video signatures: the MEAN per-keyframe Hamming distance over the
/// overlapping prefix (videos are sampled with the same keyframe count, so frames line up; a
/// trimmed copy still aligns on its shared frames). Returns a value on the same 0..=64 scale as
/// the image dHash so the strictness bands transfer directly. `u32::MAX` if either side is empty.
pub fn signature_distance(a: &[u64], b: &[u64]) -> u32 {
    let n = a.len().min(b.len());
    if n == 0 {
        return u32::MAX;
    }
    let total: u32 = (0..n).map(|i| (a[i] ^ b[i]).count_ones()).sum();
    total / n as u32
}

// ===========================================================================
// Detection — hash the not-yet-hashed videos (cached), then cluster near-dups.
// ===========================================================================

/// Detect groups of perceptually-similar videos within `threshold` mean-Hamming distance over
/// their keyframe signatures (the same 0..=64 scale as similar-images; dup ≈ ≤5, similar ≈ 6–15).
/// Samples any not-yet-fingerprinted videos first (cached in `video_hash`), then clusters.
///
/// `ffmpeg` is the resolved bundled/PATH ffmpeg ("" when unavailable — then nothing new is
/// sampled, but already-cached signatures still cluster). `cancelled` lets a background pass
/// stop sampling between videos without losing what it already persisted.
pub fn detect(
    conn: &Connection,
    threshold: u32,
    ffmpeg: &str,
    cancelled: &dyn Fn() -> bool,
) -> Result<SimilarVideoResult, String> {
    // 1. Sample any not-yet-fingerprinted videos in parallel (decode dominates), then persist.
    //    We sample in chunks so a cancel between chunks is honoured promptly (long libraries).
    let todo = repo::videos_needing_hash(conn).map_err(|e| e.to_string())?;
    let mut newly = 0i64;
    if !ffmpeg.is_empty() {
        const CHUNK: usize = 8;
        for chunk in todo.chunks(CHUNK) {
            if cancelled() {
                break;
            }
            let sampled: Vec<(i64, Option<Vec<u64>>)> = chunk
                .par_iter()
                .map(|(id, path)| (*id, sample_signature(ffmpeg, Path::new(path), KEYFRAMES)))
                .collect();
            for (id, sig) in &sampled {
                match sig {
                    Some(v) => {
                        let _ = repo::store_video_signature(conn, *id, Some(&pack_signature(v)), v.len() as i64, "ok");
                        newly += 1;
                    }
                    None => {
                        let _ = repo::store_video_signature(conn, *id, None, 0, "error");
                    }
                }
            }
        }
    }

    // 2. Load every cached signature for present/online videos: (file_id, signature, size).
    let raw = repo::load_video_signatures(conn).map_err(|e| e.to_string())?;
    let items: Vec<(i64, Vec<u64>, i64)> = raw
        .into_iter()
        .map(|(id, blob, sz)| (id, unpack_signature(&blob), sz))
        .filter(|(_, sig, _)| !sig.is_empty())
        .collect();
    let n = items.len();
    if n == 0 {
        return Ok(SimilarVideoResult { newly_hashed: newly, total_hashed: 0, groups: Vec::new() });
    }

    // 3. Cluster. BK-tree over a REPRESENTATIVE key (first keyframe hash) finds candidate
    //    neighbours within a widened radius; a full signature-vector distance then confirms each
    //    pair before union (so the cheap key prunes, the real metric decides).
    let mut tree = BkTree::new();
    for (idx, (_, sig, _)) in items.iter().enumerate() {
        tree.add(sig[0], idx);
    }
    let mut uf = UnionFind::new(n);
    let mut nb = Vec::new();
    // The representative-key radius is widened (one frame can differ more than the mean) so the
    // BK-tree never prunes away a true match; the per-vector confirm keeps precision.
    let key_radius = (threshold + HASH_BITS / 4).min(HASH_BITS);
    for (idx, (_, sig, _)) in items.iter().enumerate() {
        nb.clear();
        tree.query(sig[0], key_radius, &mut nb);
        for &other in &nb {
            if other != idx && signature_distance(sig, &items[other].1) <= threshold {
                uf.union(idx, other);
            }
        }
    }

    // 4. Group indices by union-find root; keep groups with >1 member.
    let mut groups: HashMap<usize, Vec<usize>> = HashMap::new();
    for idx in 0..n {
        groups.entry(uf.find(idx)).or_default().push(idx);
    }

    // 5. Build DTOs. Representative = largest video (suggested keeper). similarity vs the rep's
    //    signature, mapped to a percentage on the same scale as similar-images.
    let mut out = Vec::new();
    let mut gid = 1i64;
    for idxs in groups.into_values() {
        if idxs.len() < 2 {
            continue;
        }
        let rep_idx = *idxs.iter().max_by_key(|&&i| items[i].2).unwrap();
        let rep_sig = items[rep_idx].1.clone();
        let ids: Vec<i64> = idxs.iter().map(|&i| items[i].0).collect();
        let views = repo::files_by_ids(conn, &ids).map_err(|e| e.to_string())?;
        let mut by_id: HashMap<i64, FileView> = views.into_iter().map(|v| (v.file_id, v)).collect();

        let mut members = Vec::new();
        let mut max_size = 0i64;
        for &i in &idxs {
            let (fid, ref sig, sz) = items[i];
            if sz > max_size {
                max_size = sz;
            }
            let dist = signature_distance(&rep_sig, sig);
            let sim = 100u32.saturating_sub(dist * 100 / HASH_BITS);
            if let Some(file) = by_id.remove(&fid) {
                members.push(SimilarVideoMember { file, similarity_pct: sim, is_representative: i == rep_idx });
            }
        }
        members.sort_by(|a, b| {
            b.is_representative
                .cmp(&a.is_representative)
                .then(b.similarity_pct.cmp(&a.similarity_pct))
        });
        out.push(SimilarVideoGroup { group_id: gid, member_count: members.len() as i64, max_size_bytes: max_size, members });
        gid += 1;
    }
    out.sort_by(|a, b| b.max_size_bytes.cmp(&a.max_size_bytes));
    Ok(SimilarVideoResult { newly_hashed: newly, total_hashed: n as i64, groups: out })
}

// --------------------------------------------------------------------------
// BK-tree (Hamming metric) + union-find — a self-contained copy of the proven
// structures in imagehash.rs, kept local so the working image pipeline is never
// touched (zero-regression). Same algorithm, independently tested below.
// --------------------------------------------------------------------------

struct BkNode {
    hash: u64,
    idxs: Vec<usize>,
    children: HashMap<u32, Box<BkNode>>,
}
struct BkTree {
    root: Option<Box<BkNode>>,
}
impl BkTree {
    fn new() -> Self {
        BkTree { root: None }
    }
    fn add(&mut self, hash: u64, idx: usize) {
        match self.root.as_mut() {
            None => self.root = Some(Box::new(BkNode { hash, idxs: vec![idx], children: HashMap::new() })),
            Some(r) => add_node(r, hash, idx),
        }
    }
    fn query(&self, hash: u64, max: u32, out: &mut Vec<usize>) {
        if let Some(r) = self.root.as_ref() {
            query_node(r, hash, max, out);
        }
    }
}
fn add_node(node: &mut BkNode, hash: u64, idx: usize) {
    let d = (node.hash ^ hash).count_ones();
    if d == 0 {
        node.idxs.push(idx);
        return;
    }
    match node.children.get_mut(&d) {
        Some(c) => add_node(c, hash, idx),
        None => {
            node.children.insert(d, Box::new(BkNode { hash, idxs: vec![idx], children: HashMap::new() }));
        }
    }
}
fn query_node(node: &BkNode, hash: u64, max: u32, out: &mut Vec<usize>) {
    let d = (node.hash ^ hash).count_ones();
    if d <= max {
        out.extend(node.idxs.iter().copied());
    }
    let lo = d.saturating_sub(max);
    let hi = d + max;
    for (&cd, c) in node.children.iter() {
        if cd >= lo && cd <= hi {
            query_node(c, hash, max, out);
        }
    }
}

struct UnionFind {
    parent: Vec<usize>,
}
impl UnionFind {
    fn new(n: usize) -> Self {
        UnionFind { parent: (0..n).collect() }
    }
    fn find(&mut self, x: usize) -> usize {
        let mut r = x;
        while self.parent[r] != r {
            r = self.parent[r];
        }
        let mut c = x;
        while self.parent[c] != c {
            let next = self.parent[c];
            self.parent[c] = r;
            c = next;
        }
        r
    }
    fn union(&mut self, a: usize, b: usize) {
        let ra = self.find(a);
        let rb = self.find(b);
        if ra != rb {
            self.parent[ra] = rb;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pack_roundtrips_signature() {
        let sig = vec![0u64, 0x00FF_00FF_00FF_00FF, u64::MAX, 42];
        let blob = pack_signature(&sig);
        assert_eq!(blob.len(), sig.len() * 8, "8 bytes per keyframe");
        assert_eq!(unpack_signature(&blob), sig, "pack→unpack is the identity");
    }

    #[test]
    fn signature_distance_is_mean_hamming() {
        // Two identical signatures => distance 0.
        let a = vec![0b0000u64, 0b1111u64];
        assert_eq!(signature_distance(&a, &a), 0);
        // One frame differs by 2 bits, one by 0 => mean of {2,0} = 1.
        let b = vec![0b0011u64, 0b1111u64];
        assert_eq!(signature_distance(&a, &b), 1);
        // Empty side => max (never clusters).
        assert_eq!(signature_distance(&a, &[]), u32::MAX);
    }

    #[test]
    fn near_signatures_cluster_far_one_does_not() {
        // Build three videos: A and B are near-identical (a few bits off per frame), C is far.
        let a = vec![0u64, 0u64, 0u64];
        let b = vec![0b1u64, 0b11u64, 0u64]; // mean Hamming vs A = (1+2+0)/3 = 1
        let c = vec![u64::MAX, u64::MAX, u64::MAX]; // mean Hamming vs A = 64
        let items = [a.clone(), b.clone(), c.clone()];

        // Cluster with the same representative-key + per-vector-confirm logic as `detect`.
        let threshold = 5u32;
        let mut tree = BkTree::new();
        for (i, s) in items.iter().enumerate() {
            tree.add(s[0], i);
        }
        let mut uf = UnionFind::new(items.len());
        let key_radius = (threshold + HASH_BITS / 4).min(HASH_BITS);
        let mut nb = Vec::new();
        for (i, s) in items.iter().enumerate() {
            nb.clear();
            tree.query(s[0], key_radius, &mut nb);
            for &o in &nb {
                if o != i && signature_distance(s, &items[o]) <= threshold {
                    uf.union(i, o);
                }
            }
        }
        assert_eq!(uf.find(0), uf.find(1), "A and B are near-duplicates and must cluster");
        assert_ne!(uf.find(0), uf.find(2), "C is unrelated and must NOT cluster with A");
    }

    #[test]
    fn split_png_stream_finds_two_frames() {
        const SIG: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
        let mut buf = Vec::new();
        buf.extend_from_slice(&SIG);
        buf.extend_from_slice(b"AAAA");
        buf.extend_from_slice(&SIG);
        buf.extend_from_slice(b"BBBBBB");
        let frames = split_png_stream(&buf);
        assert_eq!(frames.len(), 2, "two PNG signatures => two frames");
        assert_eq!(frames[0].len(), SIG.len() + 4);
        assert_eq!(frames[1].len(), SIG.len() + 6);
    }
}
