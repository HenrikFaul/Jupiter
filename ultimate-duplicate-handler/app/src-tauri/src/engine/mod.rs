//! Engine module surface. The engine is authoritative; Tauri commands and the UI are
//! thin layers over it (CODING_RULES: engine-authoritative, UI is preview).
//!
//! Dependency order within the engine (CODING_RULES §9): identity/pathkey (pure) →
//! hashing (pure I/O) → safety (pure) → walker/scanner (orchestration) → cluster.

pub mod actions;
pub mod bookmarks;
pub mod deletion_service;
pub mod dupfolder;
pub mod hashing;
pub mod identity;
pub mod imagehash;
pub mod license;
pub mod mediaprobe;
pub mod ntfs;
pub mod pathkey;
pub mod rename_service;
pub mod reports;
pub mod restore_service;
pub mod safety;
pub mod scan_service;
pub mod scheduler;
pub mod selection;
pub mod videohash;
pub mod walker;

pub use safety::{validate_plan, Decision, PresenceSummary, SafetyReport, SafetyViolation};
pub use selection::{Candidate as SelectionCandidate, SelectionDecision, SelectionRule};
