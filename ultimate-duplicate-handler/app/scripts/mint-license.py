#!/usr/bin/env python3
"""Sift offline license minter (initiative #1).

Signs a Free/Pro license token with the Ed25519 SECRET seed whose PUBLIC half is baked into
the Sift binary (engine/license.rs PUBLIC_KEY). Run it OFFLINE after a sale to issue a key the
customer pastes into Settings -> License. The token verifies fully client-side, no server.

    python scripts/mint-license.py --tier pro --email buyer@example.com
    python scripts/mint-license.py --tier pro --email buyer@example.com --days 365

SECURITY: this file contains the signing seed. KEEP IT SECRET — anyone with it can mint Pro
keys. It is NOT shipped in the app (the binary only carries the verifying/public key). To
rotate, generate a new keypair, put the new public bytes in engine/license.rs, and update SEED
here. Requires `pip install cryptography`.
"""
import argparse
import base64
import json
import time

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

# The 32-byte signing seed matching engine/license.rs PUBLIC_KEY (and its cfg(test) TEST_SEED).
SEED = bytes([
    211, 219, 193, 157, 147, 215, 1, 87, 58, 238, 129, 162, 183, 235, 243, 223,
    66, 217, 223, 238, 178, 203, 91, 146, 54, 77, 142, 244, 157, 51, 236, 2,
])


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def main() -> int:
    ap = argparse.ArgumentParser(description="Mint a Sift license token.")
    ap.add_argument("--tier", choices=["free", "pro"], default="pro")
    ap.add_argument("--email", required=True)
    ap.add_argument("--days", type=int, default=0, help="validity in days (0 = perpetual)")
    args = ap.parse_args()

    issued_at = int(time.time())
    expires_at = issued_at + args.days * 86400 if args.days > 0 else None
    # Field order/spacing is irrelevant — Sift verifies the signature over THESE bytes, then
    # deserializes by field name. We just need valid JSON with the four claim fields.
    payload = {"tier": args.tier, "email": args.email, "issuedAt": issued_at, "expiresAt": expires_at}
    payload_bytes = json.dumps(payload, separators=(",", ":")).encode("utf-8")

    sk = Ed25519PrivateKey.from_private_bytes(SEED)
    sig = sk.sign(payload_bytes)

    token = f"{b64url(payload_bytes)}.{b64url(sig)}"
    print(token)
    print(f"\n  tier={args.tier}  email={args.email}  " +
          ("perpetual" if expires_at is None else f"expires {time.strftime('%Y-%m-%d', time.localtime(expires_at))}"),
          flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
