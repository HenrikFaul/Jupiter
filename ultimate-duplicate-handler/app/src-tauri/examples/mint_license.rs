//! Offline license signer — issue Sift Pro keys WITHOUT any server.
//!
//! This is the support/back-office counterpart to `engine/license.rs`. It holds the Ed25519
//! SIGNING seed (the secret half of the keypair whose public half is baked into the shipped
//! binary) and emits a compact `payload.signature` token the app verifies entirely offline.
//!
//! Run it with cargo's example runner:
//!     cargo run --example mint_license -- --email buyer@example.com
//!     cargo run --example mint_license -- --email buyer@example.com --days 365
//!     cargo run --example mint_license -- --email buyer@example.com --tier pro
//!
//! Token format (matches `engine::license::verify`):
//!     <base64url(payload_json)>.<base64url(signature_64_bytes)>
//! where payload_json = {"tier","email","issuedAt","expiresAt"} (camelCase).
//!
//! SECURITY: keep this seed offline. Anyone with it can mint Pro keys. To rotate, generate a new
//! keypair, replace `SEED` here and `PUBLIC_KEY` in engine/license.rs, and ship a new build.

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine as _;
use ed25519_dalek::{Signer, SigningKey};
use std::time::{SystemTime, UNIX_EPOCH};

/// The 32-byte Ed25519 signing seed. Its public half is the `PUBLIC_KEY` baked into the app.
/// (Deterministically derived so this committed example reproduces the same keypair; replace
/// both halves to rotate.)
const SEED: [u8; 32] = [
    211, 219, 193, 157, 147, 215, 1, 87, 58, 238, 129, 162, 183, 235, 243, 223, 66, 217, 223, 238,
    178, 203, 91, 146, 54, 77, 142, 244, 157, 51, 236, 2,
];

/// The public key the app embeds — printed for convenience so you can paste it into
/// `engine/license.rs` after a rotation.
fn print_public_key(signing: &SigningKey) {
    let pk = signing.verifying_key().to_bytes();
    let csv = pk.iter().map(|b| b.to_string()).collect::<Vec<_>>().join(", ");
    eprintln!("// PUBLIC_KEY for engine/license.rs:\n[{csv}]");
}

fn now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let mut email: Option<String> = None;
    let mut tier = "pro".to_string();
    let mut days: Option<i64> = None;
    let mut show_pubkey = false;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--email" => {
                email = args.get(i + 1).cloned();
                i += 2;
            }
            "--tier" => {
                tier = args.get(i + 1).cloned().unwrap_or_else(|| "pro".into());
                i += 2;
            }
            "--days" => {
                days = args.get(i + 1).and_then(|s| s.parse::<i64>().ok());
                i += 2;
            }
            "--print-public-key" => {
                show_pubkey = true;
                i += 1;
            }
            other => {
                eprintln!("unknown argument: {other}");
                i += 1;
            }
        }
    }

    let signing = SigningKey::from_bytes(&SEED);

    if show_pubkey {
        print_public_key(&signing);
        return;
    }

    let email = match email {
        Some(e) if !e.trim().is_empty() => e,
        _ => {
            eprintln!(
                "usage: cargo run --example mint_license -- --email <addr> [--tier pro|free] [--days N]"
            );
            std::process::exit(2);
        }
    };

    let issued_at = now();
    let expires_at = days.map(|d| issued_at + d * 86_400);

    // Build the EXACT payload JSON the app will verify (camelCase, field order matters only for
    // byte-stability — serde_json keeps struct field order, which mirrors license.rs).
    let payload = serde_json::json!({
        "tier": tier,
        "email": email,
        "issuedAt": issued_at,
        "expiresAt": expires_at,
    });
    let payload_json = serde_json::to_vec(&payload).expect("serialize payload");

    let signature = signing.sign(&payload_json);
    let token = format!(
        "{}.{}",
        URL_SAFE_NO_PAD.encode(&payload_json),
        URL_SAFE_NO_PAD.encode(signature.to_bytes())
    );

    // The token on stdout (so it can be piped/emailed); diagnostics on stderr.
    eprintln!(
        "issued {tier} license for {email}{}",
        expires_at.map(|e| format!(" (expires at unix {e})")).unwrap_or_else(|| " (perpetual)".into())
    );
    println!("{token}");
}
