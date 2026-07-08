//! Similar-image detection via perceptual hashing — the headline media feature.
//!
//! Pipeline (per the standard perceptual-hash approach): decode → dHash (64-bit gradient
//! hash via `image_hasher`) → compare by Hamming distance. Two images are "similar" when
//! the distance is within the chosen strictness threshold. Hashes are CACHED in SQLite so
//! re-detection (e.g. moving the strictness slider) is near-instant — only new images are
//! decoded (the history-aware advantage over tools that rescan from scratch).
//!
//! Clustering uses a BK-tree (metric tree for discrete Hamming distance) + union-find, so
//! it scales to large libraries without the O(N²) all-pairs blow-up.

use crate::db::repo;
use crate::model::{FileView, SimilarImageGroup, SimilarImageMember, SimilarImageResult};
use rayon::prelude::*;
use rusqlite::Connection;
use std::collections::HashMap;
use std::path::Path;

const HASH_BITS: u32 = 64;

/// 64-bit difference hash. None if the image can't be decoded (corrupt/unsupported).
pub fn compute_dhash(path: &Path) -> Option<u64> {
    let img = image::open(path).ok()?;
    let hasher = image_hasher::HasherConfig::new()
        .hash_size(8, 8)
        .hash_alg(image_hasher::HashAlg::Gradient)
        .to_hasher();
    let h = hasher.hash_image(&img);
    let b = h.as_bytes();
    let mut arr = [0u8; 8];
    let n = b.len().min(8);
    arr[..n].copy_from_slice(&b[..n]);
    Some(u64::from_le_bytes(arr))
}

/// A PNG thumbnail (aspect-preserving, longest side `max`). None if undecodable.
pub fn thumbnail_png(path: &Path, max: u32) -> Option<Vec<u8>> {
    let img = image::open(path).ok()?;
    let thumb = img.thumbnail(max, max);
    let mut buf = Vec::new();
    thumb
        .write_to(&mut std::io::Cursor::new(&mut buf), image::ImageFormat::Png)
        .ok()?;
    Some(buf)
}

/// Detect groups of visually-similar images within `threshold` Hamming distance
/// (dup ≈ ≤5, similar ≈ 6–15). Hashes missing images first (cached), then clusters.
pub fn detect(conn: &Connection, threshold: u32) -> Result<SimilarImageResult, String> {
    // 1. Hash any not-yet-hashed images in parallel (decode dominates), then persist.
    let todo = repo::images_needing_hash(conn).map_err(|e| e.to_string())?;
    let hashed: Vec<(i64, Option<u64>)> = todo
        .par_iter()
        .map(|(id, path)| (*id, compute_dhash(Path::new(path))))
        .collect();
    let mut newly = 0i64;
    for (id, h) in &hashed {
        match h {
            Some(v) => {
                let _ = repo::store_image_hash(conn, *id, Some(*v as i64), "ok");
                newly += 1;
            }
            None => {
                let _ = repo::store_image_hash(conn, *id, None, "error");
            }
        }
    }

    // 2. Load every cached hash for present/online images.
    let items = repo::load_image_hashes(conn).map_err(|e| e.to_string())?; // (file_id, hash, size)
    let n = items.len();
    if n == 0 {
        return Ok(SimilarImageResult { newly_hashed: newly, total_hashed: 0, groups: Vec::new() });
    }

    // 3. Cluster: BK-tree of all hashes, then union every within-threshold pair.
    let mut tree = BkTree::new();
    for (idx, (_, h, _)) in items.iter().enumerate() {
        tree.add(*h as u64, idx);
    }
    let mut uf = UnionFind::new(n);
    let mut nb = Vec::new();
    for (idx, (_, h, _)) in items.iter().enumerate() {
        nb.clear();
        tree.query(*h as u64, threshold, &mut nb);
        for &other in &nb {
            if other != idx {
                uf.union(idx, other);
            }
        }
    }

    // 4. Group indices by union-find root; keep groups with >1 member.
    let mut groups: HashMap<usize, Vec<usize>> = HashMap::new();
    for idx in 0..n {
        let r = uf.find(idx);
        groups.entry(r).or_default().push(idx);
    }

    // 5. Build DTOs. Representative = largest image (suggested keeper). similarity vs rep.
    let mut out = Vec::new();
    let mut gid = 1i64;
    for idxs in groups.into_values() {
        if idxs.len() < 2 {
            continue;
        }
        let rep_idx = *idxs.iter().max_by_key(|&&i| items[i].2).unwrap();
        let rep_hash = items[rep_idx].1 as u64;
        let ids: Vec<i64> = idxs.iter().map(|&i| items[i].0).collect();
        let views = repo::files_by_ids(conn, &ids).map_err(|e| e.to_string())?;
        let mut by_id: HashMap<i64, FileView> = views.into_iter().map(|v| (v.file_id, v)).collect();

        let mut members = Vec::new();
        let mut max_size = 0i64;
        for &i in &idxs {
            let (fid, h, sz) = items[i];
            if sz > max_size {
                max_size = sz;
            }
            let dist = (rep_hash ^ (h as u64)).count_ones();
            let sim = 100u32.saturating_sub(dist * 100 / HASH_BITS);
            if let Some(file) = by_id.remove(&fid) {
                members.push(SimilarImageMember { file, similarity_pct: sim, is_representative: i == rep_idx });
            }
        }
        members.sort_by(|a, b| {
            b.is_representative
                .cmp(&a.is_representative)
                .then(b.similarity_pct.cmp(&a.similarity_pct))
        });
        out.push(SimilarImageGroup { group_id: gid, member_count: members.len() as i64, max_size_bytes: max_size, members });
        gid += 1;
    }
    out.sort_by(|a, b| b.max_size_bytes.cmp(&a.max_size_bytes));
    Ok(SimilarImageResult { newly_hashed: newly, total_hashed: n as i64, groups: out })
}

// --------------------------------------------------------------------------
// BK-tree (Hamming metric) + union-find
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
    fn bktree_finds_within_threshold() {
        let mut t = BkTree::new();
        t.add(0b0000, 0);
        t.add(0b0011, 1); // distance 2 from 0
        t.add(0b1111_1111, 2); // far
        let mut out = Vec::new();
        t.query(0b0000, 2, &mut out);
        out.sort();
        assert_eq!(out, vec![0, 1], "items within Hamming 2 are returned, far one excluded");
    }

    #[test]
    fn unionfind_groups() {
        let mut uf = UnionFind::new(4);
        uf.union(0, 1);
        uf.union(1, 2);
        assert_eq!(uf.find(0), uf.find(2));
        assert_ne!(uf.find(0), uf.find(3));
    }
}
