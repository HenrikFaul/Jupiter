//! Sift core library. Splitting the engine into a library crate (separate from the
//! Tauri binary) means the whole index/dedup/safety engine is unit-testable with
//! plain `cargo test`, no UI or window required (verification discipline — FORGE §8).

pub mod db;
pub mod engine;
pub mod model;
pub mod paths;

/// Re-export the most-used types at the crate root for ergonomic `use sift_core::...`.
pub use engine::safety;
pub use model::*;
