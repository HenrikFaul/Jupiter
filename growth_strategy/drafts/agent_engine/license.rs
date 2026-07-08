//! Offline license verification + entitlement gating (Free / Pro).
//!
//! Sift is a zero-backend, portable app: the index travels in `Sift-Data\` next to the EXE
//! (see `paths.rs`). Monetization therefore has to work the same way — entirely client-side,
//! on an air-gapped machine, with no activation server. This module verifies an
//! **Ed25519-signed** license token whose public key is baked into the binary, and caches the
//! resolved entitlement in `Sift-Data\license.json` (resolved via the same `data_dir` used for
//! quarantine/exports). Every activate/deactivate is written through the append-only
//! `audit_log` (intent → outcome), exactly like deletions, so entitlement changes are auditable.
//!
//! Token format (compact, base64url, no padding):
//!     `<base64url(payload_json)>.<base64url(signature_64_bytes)>`
//! where `payload_json` is the canonical JSON of [`LicensePayload`] (camelCase fields) and the
//! signature is the raw 64-byte Ed25519 signature over the *exact payload JSON bytes* (the part
//! before the dot, base64url-decoded). The matching secret seed lives only in the offline
//! signer (`examples/mint_license.rs`); it is NEVER in the shipped binary.
//!
//! Threat model (intentional, and acceptable for this segment): a purely client-side check can
//! always be patched by a determined attacker who recompiles. We do not pretend otherwise. The
//! goal is an honest, friction-free, offline-respecting gate for paying power users — not DRM.
//! Fail-loud on every malformed/tampered/expired token (mirrors `deletion_service.rs` errors).

use crate::db::repo;
use rusqlite::Connection;
use serde::{Deserialize, Serialize};
use std::path::Path;

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine as _;
use ed25519_dalek::{Signature, VerifyingKey};

/// The Ed25519 PUBLIC key that authenticates license tokens, compiled into the binary.
/// This is the verifying half of a real keypair; the signing seed is held offline by support
/// (see `examples/mint_license.rs`). Rotating the key = ship a new build with new bytes here.
const PUBLIC_KEY: [u8; 32] = [
    179, 207, 44, 90, 121, 213, 206, 4, 39, 153, 111, 135, 113, 26, 153, 116, 229, 223, 174, 193,
    108, 199, 151, 236, 12, 141, 74, 6, 249, 81, 92, 33,
];

/// Entitlement tier. `Free` is the default for an unlicensed (or invalid/expired) install; the
/// free scan → review → safe-delete loop stays fully usable at this tier. `Pro` unlocks the
/// high-effort passes (similar-image, media analysis, bulk delete, smart selection).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Tier {
    Free,
    Pro,
}

impl Tier {
    /// Lowercase wire string (`"free"` / `"pro"`) for the DTO / TS contract.
    pub fn as_str(self) -> &'static str {
        match self {
            Tier::Free => "free",
            Tier::Pro => "pro",
        }
    }
}

/// The signed claims inside a license token. Field names are camelCase on the wire so the same
/// JSON the signer produces is what we verify byte-for-byte (no re-serialization ambiguity).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LicensePayload {
    pub tier: String, // "free" | "pro"
    pub email: String,
    #[serde(rename = "issuedAt")]
    pub issued_at: i64,
    /// Unix seconds; `None` = perpetual license (no expiry).
    #[serde(rename = "expiresAt")]
    pub expires_at: Option<i64>,
}

impl LicensePayload {
    pub fn tier(&self) -> Tier {
        if self.tier.eq_ignore_ascii_case("pro") {
            Tier::Pro
        } else {
            Tier::Free
        }
    }
}

/// What `license.json` stores on disk: the raw token (so we can re-verify on every launch — we
/// never trust a cached "pro" flag without re-checking the signature) plus when we saved it.
#[derive(Debug, Clone, Serialize, Deserialize)]
struct StoredLicense {
    token: String,
    saved_at: i64,
}

fn license_path(data_dir: &Path) -> std::path::PathBuf {
    data_dir.join("license.json")
}

/// Verify a license token fully offline. Returns the decoded payload ONLY when:
///   * the token is well-formed (`payload.signature`, both base64url),
///   * the Ed25519 signature over the payload JSON bytes checks against [`PUBLIC_KEY`], and
///   * the license has not expired.
/// Otherwise returns a clear, user-facing error (fail-loud — never silently downgrade).
pub fn verify(token: &str) -> Result<LicensePayload, String> {
    let token = token.trim();
    let (payload_b64, sig_b64) = token
        .split_once('.')
        .ok_or("Malformed license key (expected `payload.signature`).")?;
    if payload_b64.is_empty() || sig_b64.is_empty() {
        return Err("Malformed license key (empty payload or signature).".into());
    }

    let payload_bytes = URL_SAFE_NO_PAD
        .decode(payload_b64.as_bytes())
        .map_err(|_| "Malformed license key (payload is not valid base64url).".to_string())?;
    let sig_bytes = URL_SAFE_NO_PAD
        .decode(sig_b64.as_bytes())
        .map_err(|_| "Malformed license key (signature is not valid base64url).".to_string())?;

    let sig_arr: [u8; 64] = sig_bytes
        .as_slice()
        .try_into()
        .map_err(|_| "Malformed license key (signature must be 64 bytes).".to_string())?;
    let signature = Signature::from_bytes(&sig_arr);

    let verifying_key = VerifyingKey::from_bytes(&PUBLIC_KEY)
        .map_err(|_| "Internal error: baked-in license key is invalid.".to_string())?;
    verifying_key
        .verify_strict(&payload_bytes, &signature)
        .map_err(|_| "License signature is invalid — this key was not issued for Sift.".to_string())?;

    let payload: LicensePayload = serde_json::from_slice(&payload_bytes)
        .map_err(|e| format!("License payload could not be read: {e}"))?;

    if let Some(exp) = payload.expires_at {
        if repo::now() > exp {
            return Err("This license has expired. Renew it to keep Pro features.".into());
        }
    }
    Ok(payload)
}

/// Resolve the current entitlement at startup (and after activate/deactivate). Reads the cached
/// token from `Sift-Data\license.json` and RE-VERIFIES it (signature + expiry) every time, so a
/// tampered cache or a now-expired token degrades safely to `Free`. Never errors — a missing or
/// unreadable license simply means Free.
pub fn load_entitlement(data_dir: &Path) -> Tier {
    let path = license_path(data_dir);
    let Ok(bytes) = std::fs::read(&path) else {
        return Tier::Free;
    };
    let Ok(stored) = serde_json::from_slice::<StoredLicense>(&bytes) else {
        return Tier::Free;
    };
    match verify(&stored.token) {
        Ok(payload) => payload.tier(),
        Err(_) => Tier::Free,
    }
}

/// Verify + persist a license token, returning the resolved payload. Writes the token to
/// `Sift-Data\license.json` and AUDITS the activation (intent → success) in the same append-only
/// `audit_log` as deletions. Refuses to persist an invalid/expired token (verify() runs first).
pub fn save_entitlement(
    conn: &Connection,
    data_dir: &Path,
    token: &str,
) -> Result<LicensePayload, String> {
    let payload = verify(token)?; // fail-loud BEFORE writing anything

    let detail = serde_json::json!({
        "tier": payload.tier,
        "email": payload.email,
        "issuedAt": payload.issued_at,
        "expiresAt": payload.expires_at,
    })
    .to_string();
    let audit_id = repo::audit_intent(
        conn,
        "license_activate",
        None,
        None,
        None,
        None,
        true,
        Some(&detail),
    )
    .map_err(|e| e.to_string())?;

    let stored = StoredLicense { token: token.trim().to_string(), saved_at: repo::now() };
    let json = serde_json::to_vec_pretty(&stored).map_err(|e| e.to_string())?;
    if let Err(e) = std::fs::write(license_path(data_dir), &json) {
        let _ = repo::audit_outcome(conn, audit_id, "failed");
        return Err(format!("Could not save the license file: {e}"));
    }
    repo::audit_outcome(conn, audit_id, "success").map_err(|e| e.to_string())?;
    Ok(payload)
}

/// Remove the cached license (revert to Free) and audit the deactivation. Idempotent: deleting
/// an already-absent license file is success, not an error.
pub fn clear_entitlement(conn: &Connection, data_dir: &Path) -> Result<(), String> {
    let audit_id = repo::audit_intent(conn, "license_deactivate", None, None, None, None, true, None)
        .map_err(|e| e.to_string())?;
    let path = license_path(data_dir);
    match std::fs::remove_file(&path) {
        Ok(()) => {}
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
        Err(e) => {
            let _ = repo::audit_outcome(conn, audit_id, "failed");
            return Err(format!("Could not remove the license file: {e}"));
        }
    }
    repo::audit_outcome(conn, audit_id, "success").map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    // The matching SIGNING seed for the baked-in PUBLIC_KEY. Kept here ONLY for tests so the
    // module is self-verifying; the shipped binary never signs anything. (Same value the
    // offline `examples/mint_license.rs` signer uses.)
    const TEST_SEED: [u8; 32] = [
        211, 219, 193, 157, 147, 215, 1, 87, 58, 238, 129, 162, 183, 235, 243, 223, 66, 217, 223,
        238, 178, 203, 91, 146, 54, 77, 142, 244, 157, 51, 236, 2,
    ];

    fn mint(payload: &LicensePayload) -> String {
        use ed25519_dalek::{Signer, SigningKey};
        let signing = SigningKey::from_bytes(&TEST_SEED);
        // Sanity: the seed really does correspond to the baked-in public key.
        assert_eq!(signing.verifying_key().to_bytes(), PUBLIC_KEY);
        let payload_json = serde_json::to_vec(payload).unwrap();
        let sig = signing.sign(&payload_json);
        format!(
            "{}.{}",
            URL_SAFE_NO_PAD.encode(&payload_json),
            URL_SAFE_NO_PAD.encode(sig.to_bytes())
        )
    }

    fn pro_payload(expires_at: Option<i64>) -> LicensePayload {
        LicensePayload {
            tier: "pro".into(),
            email: "buyer@example.com".into(),
            issued_at: 1_700_000_000,
            expires_at,
        }
    }

    #[test]
    fn valid_perpetual_token_verifies_as_pro() {
        let token = mint(&pro_payload(None));
        let payload = verify(&token).expect("valid token must verify");
        assert_eq!(payload.tier(), Tier::Pro);
        assert_eq!(payload.email, "buyer@example.com");
    }

    #[test]
    fn tampered_signature_is_rejected() {
        let token = mint(&pro_payload(None));
        // Flip a character in the signature segment.
        let (p, s) = token.split_once('.').unwrap();
        let mut s: Vec<char> = s.chars().collect();
        s[0] = if s[0] == 'A' { 'B' } else { 'A' };
        let tampered = format!("{p}.{}", s.into_iter().collect::<String>());
        assert!(verify(&tampered).is_err(), "tampered signature must fail");
    }

    #[test]
    fn tampered_payload_is_rejected() {
        // Sign a Free payload, then swap in a Pro payload while keeping the old signature.
        let free = LicensePayload { tier: "free".into(), ..pro_payload(None) };
        let token = mint(&free);
        let (_p, s) = token.split_once('.').unwrap();
        let forged_payload = serde_json::to_vec(&pro_payload(None)).unwrap();
        let forged = format!("{}.{s}", URL_SAFE_NO_PAD.encode(&forged_payload));
        assert!(verify(&forged).is_err(), "payload swap must invalidate the signature");
    }

    #[test]
    fn expired_token_is_rejected() {
        let token = mint(&pro_payload(Some(1))); // expired in 1970
        let err = verify(&token).unwrap_err();
        assert!(err.contains("expired"), "expired token must be rejected: {err}");
    }

    #[test]
    fn malformed_tokens_are_rejected() {
        assert!(verify("not-a-token").is_err());
        assert!(verify("").is_err());
        assert!(verify("only-one-part.").is_err());
        assert!(verify("@@@.@@@").is_err());
    }
}
