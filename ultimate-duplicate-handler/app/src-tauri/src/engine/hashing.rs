//! Multi-stage content fingerprinting.
//!
//! The cascade is the performance heart of Sift. We NEVER full-hash a file before
//! cheaper stages have proven it could plausibly be a duplicate. Order:
//!
//!   Stage 0  size            -- grouping only, no read (already in the index)
//!   Stage 1  partial_hash    -- read at most HEAD + TAIL bytes, BLAKE3
//!   Stage 2  full_hash       -- stream the whole file, BLAKE3, only for survivors
//!
//! Rationale (codingLessonsLearnt: multi-source cascade with graceful narrowing):
//! a unique size means a unique file — no read at all. A unique partial fingerprint
//! within a size bucket means unique content — no full read. Only files that collide
//! on (size, partial) get streamed in full. On real media libraries this avoids
//! reading the overwhelming majority of bytes.
//!
//! BLAKE3 chosen over SHA-256: faster (SIMD + internal parallelism), cryptographically
//! strong (collisions are not a practical concern for dedup), 256-bit output.

use std::fs::File;
use std::io::{self, Read, Seek, SeekFrom};
use std::path::Path;

/// Bytes sampled from the head and from the tail for the partial fingerprint.
/// 64 KiB each balances disk-seek cost against discrimination power.
const PARTIAL_SAMPLE: u64 = 64 * 1024;

/// Streaming buffer for the full hash. 1 MiB keeps syscalls low without bloating RAM
/// when many workers run concurrently.
const STREAM_BUF: usize = 1024 * 1024;

/// A BLAKE3-256 digest. Stored in the index as a 32-byte BLOB.
pub type Digest = [u8; 32];

/// Errors are typed and surfaced — never swallowed (CODING_RULES §2).
#[derive(Debug, thiserror::Error)]
pub enum HashError {
    #[error("io error hashing {path}: {source}")]
    Io {
        path: String,
        #[source]
        source: io::Error,
    },
}

fn io_err(path: &Path, source: io::Error) -> HashError {
    HashError::Io { path: path.display().to_string(), source }
}

/// Stage 1: partial fingerprint from head + tail samples.
///
/// For files <= 2 * PARTIAL_SAMPLE we read the whole file (head and tail overlap),
/// which makes the partial hash identical to a full hash for small files — a useful
/// property: tiny files are fully discriminated in one pass.
pub fn partial_hash(path: &Path, size: u64) -> Result<Digest, HashError> {
    let mut f = File::open(path).map_err(|e| io_err(path, e))?;
    let mut hasher = blake3::Hasher::new();

    // Domain-separate by size so two different-sized files can never share a partial
    // hash by coincidence of sampled bytes.
    hasher.update(&size.to_le_bytes());

    if size <= PARTIAL_SAMPLE * 2 {
        // Small file: hash it entirely.
        let mut buf = Vec::with_capacity(size as usize);
        f.read_to_end(&mut buf).map_err(|e| io_err(path, e))?;
        hasher.update(&buf);
        return Ok(*hasher.finalize().as_bytes());
    }

    // Head sample.
    let mut head = vec![0u8; PARTIAL_SAMPLE as usize];
    read_exact_at(&mut f, &mut head, 0, path)?;
    hasher.update(&head);

    // Tail sample.
    let mut tail = vec![0u8; PARTIAL_SAMPLE as usize];
    read_exact_at(&mut f, &mut tail, size - PARTIAL_SAMPLE, path)?;
    hasher.update(&tail);

    Ok(*hasher.finalize().as_bytes())
}

/// Stage 2: full content hash. Streamed; memory use is bounded by STREAM_BUF
/// regardless of file size, so multi-GB files are safe under a worker pool.
pub fn full_hash(path: &Path) -> Result<Digest, HashError> {
    let mut f = File::open(path).map_err(|e| io_err(path, e))?;
    let mut hasher = blake3::Hasher::new();
    let mut buf = vec![0u8; STREAM_BUF];
    loop {
        let n = f.read(&mut buf).map_err(|e| io_err(path, e))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(*hasher.finalize().as_bytes())
}

fn read_exact_at(f: &mut File, buf: &mut [u8], offset: u64, path: &Path) -> Result<(), HashError> {
    f.seek(SeekFrom::Start(offset)).map_err(|e| io_err(path, e))?;
    f.read_exact(buf).map_err(|e| io_err(path, e))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn tmp(name: &str, bytes: &[u8]) -> std::path::PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!("sift_test_{name}"));
        let mut f = File::create(&p).unwrap();
        f.write_all(bytes).unwrap();
        p
    }

    #[test]
    fn identical_content_same_full_hash() {
        let a = tmp("a", b"hello world, this is a duplicate");
        let b = tmp("b", b"hello world, this is a duplicate");
        assert_eq!(full_hash(&a).unwrap(), full_hash(&b).unwrap());
    }

    #[test]
    fn different_content_different_full_hash() {
        let a = tmp("c", b"alpha");
        let b = tmp("d", b"omega");
        assert_ne!(full_hash(&a).unwrap(), full_hash(&b).unwrap());
    }

    #[test]
    fn small_file_partial_equals_size_domain_separated() {
        // Same bytes, asserted same partial hash (determinism — lessons: reproducible).
        let a = tmp("e", b"tiny");
        let b = tmp("f", b"tiny");
        assert_eq!(partial_hash(&a, 4).unwrap(), partial_hash(&b, 4).unwrap());
    }
}
