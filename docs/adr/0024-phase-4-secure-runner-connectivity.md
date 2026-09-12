# ADR-0024: Phase 4.6 secure Runner connectivity

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented and accepted locally on 2026-09-08; 44 PASS／0 PARTIAL／0 NOT_RUN
- Date: 2026-09-07
- Contract: [Phase 4.6 secure Runner connectivity](../design/phase-4-runner-connectivity-contract.md)

## Context

Before this decision, Runner served unauthenticated HTTP. API v2 was an explicit loopback development opt-in using the fixed `local-development` principal. Android received one build-time Runner URL, allowed `http` or `https`, attached no credential, had no Keystore envelope, and exposed only a disabled Phase 4.6 route.

Phase 4 requires a release client to use HTTPS even through ADB reverse, optional LAN only after explicit configuration, a Runner-specific local trust root, manual pairing with PC approval, per-device credentials, revocation, and authentication for all normal endpoints. The design must preserve the debug loopback workflow without presenting it as secure, and must not turn existing development ownership into paired authority automatically.

## Decision

1. Separate `DEVELOPMENT_HTTP` and `PAIRED_HTTPS` startup modes. Only the former may use unauthenticated loopback HTTP, and the two connectors are not served concurrently.
2. Generate a Runner-local ECDSA P-256 root and renewable leaf certificates through an explicit local initialization flow. Android pins the root SPKI SHA-256 and still verifies certificate time, chain, EKU, and SAN hostname／IP.
3. Use a versioned manual pairing payload. QR generation and scanning are deferred without an assigned phase; no camera or Google Play services dependency is introduced.
4. Require a five-minute single-use 256-bit invitation secret, bounded attempts, a pending request, and explicit local PC approval.
5. Have Android generate the bearer secret before approval and send only its hash to the Runner. The Runner never receives or returns the raw bearer after request creation and stores only the canonical token hash.
6. Encrypt pending and active Android credential files using Android Keystore AES-256-GCM in `noBackupFilesDir`. Do not require interactive authentication so existing background synchronization can operate.
7. Authenticate all normal v1 and v2 routes in paired mode, including health, capabilities, logs, and artifact content. Disable redirects and derive `principalId` exclusively from server authentication context.
8. Support one active Runner in the initial Android UI while keying persisted records by `runnerId` for safe replacement and future expansion.
9. Provide pairing administration, lost-device revocation, CA replacement, and ownership adoption only through a local Runner CLI. Do not add a LAN administration API.
10. Never auto-adopt `local-development` or revoked-principal resources. Permit only a previewed, expiring, atomic, audited local-CLI adoption which creates no RCE, scan, retry, trust, or install authority.
11. Use Android Room21 and Runner SQLite12 for the planned schema changes. Preserve all prior records and use no destructive fallback.
12. Add `runner-authentication@1` only after the paired HTTPS implementation exists and the request is authenticated.

## Alternatives rejected

- Unauthenticated LAN with a warning: exposes Job, log, artifact, and mutation surfaces to the local network.
- HTTP bearer tokens over ADB or LAN as the product path: contradicts the release HTTPS boundary and can expose reusable credentials.
- Trust on first use: does not provide the required out-of-band Runner identity check.
- System-wide CA installation: expands trust outside ReproDroid and makes cleanup／ownership ambiguous.
- Leaf-certificate pinning only: prevents normal renewal. The local root pin provides a stable trust boundary while retaining SAN and validity checks.
- mTLS in the initial implementation: adds client-certificate lifecycle and recovery complexity without removing the need for pairing and revocation UX.
- QR-only pairing: requires camera and decoder choices and removes the auditable manual fallback. QR is deferred entirely for 4.6.
- Server-generated bearer returned after approval: makes response loss and one-time token delivery harder to reconcile. Client generation lets the Runner activate only a hash.
- Storing the bearer in Room or preferences: mixes secret bytes with logical backup／migration data.
- Password hashing for the bearer token: the bearer is a full-entropy random secret, not a human password. A bounded ID lookup and constant-time SHA-256 comparison are sufficient for this initial local threat model.
- Automatic ownership migration to the first device: grants new remote authority from old unauthenticated state without explicit operator review.
- Serving secure and insecure connectors together: creates downgrade and routing ambiguity.
- Automatic firewall, WSL, router, or mDNS setup: changes external state and expands network exposure beyond the Runner's authority.

## Consequences

Secure operation requires explicit local initialization and pairing. Losing the root key requires re-pairing; losing the Android Keystore key makes the local credential unusable. A lost device can be revoked from the PC without deleting historical resources. Existing development resources remain inaccessible to a new paired principal until an operator explicitly adopts them.

The implementation touches both products and both databases. Authentication context now reaches every normal read and mutation, and Android enforces HTTPS in application policy rather than relying only on manifest metadata. Production release signing and public distribution remain Phase 4.8; 4.6 used only a task-local non-public acceptance artifact.

## Verification

The contract defines RC46-01 through RC46-44. Automated and product evidence are tracked independently in private implementation records: 44 PASS, 0 PARTIAL and 0 NOT_RUN at local acceptance closeout. The completed product paths include same-root leaf replacement, explicit root replacement and re-pairing, pairing process-death recovery, task-key signed release pairing／revocation, and Android 16 opt-in allow／deny／later revoke／re-grant over exact emulator private-host endpoint `10.0.2.2:8443` without ADB reverse. The public verification state is recorded in [Current status](../status/current.md).
