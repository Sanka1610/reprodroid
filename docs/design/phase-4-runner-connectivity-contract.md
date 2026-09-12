# Phase 4.6 secure Runner connectivity implementation contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented and accepted locally on 2026-09-08; acceptance ledger is 44 PASS／0 PARTIAL／0 NOT_RUN
- Date: 2026-09-07
- Scope: HTTPS, manual pairing, authentication, revocation, explicit LAN, legacy-principal adoption
- Decision: [ADR-0024](../adr/0024-phase-4-secure-runner-connectivity.md)
- Parent contracts: [Phase 4 foundation](phase-4-foundation-contract.md), [Runner API v2 foundation](../api/runner-api-v2.md)
- Implemented baseline change: Android Room20 to Room21 and Runner SQLite11 to SQLite12

## 1. Status and authority

The user accepted this contract and authorized implementation through local `develop` integration on 2026-09-07. Detailed implementation evidence is retained privately; the public verification state is recorded in [Current status](../status/current.md). All 44 acceptance items had the required automated and product／integration evidence at the recorded local acceptance closeout.

The Runner owns its CA private key, server certificate, pairing invitations, authenticated principals, token hashes, revocation state, and server-side ownership. Android owns the active Runner selection, non-secret Runner reference, pinned CA material, encrypted bearer token file, connection UI, and local credential deletion. A client-supplied `principalId`, display name, endpoint, or known resource ID never grants authority.

Phase 4.5 and earlier `PARTIAL`／`NOT_RUN` ledgers remain independent. Phase 4.6 does not convert scheduled release checks, public-project generic builds, migration, notification, or earlier product evidence into success.

## 2. Invariants and non-goals

The following boundaries do not change.

1. Pairing does not replace RCE acknowledgement, source-scan review, comparison truth, trust, signer, update, or PackageInstaller confirmation.
2. A token is not passed to source, Gradle, Docker, build environment variables, logs, audit export, backup, notification, or provider requests.
3. Authentication failure never falls back to HTTP, another Runner, an old token, or the `local-development` principal.
4. Runner IDs are independent of endpoint URLs. An endpoint match alone does not identify a Runner.
5. Android local deletion does not assert Runner-side revocation. Runner-side revocation does not delete history, jobs, holds, artifacts, toolchains, or audit records.
6. Revocation does not cancel an active Job automatically. Runner timeout, cancellation, recovery, and resource cleanup remain authoritative.
7. The Runner does not modify firewall, WSL forwarding, router, DNS, Android permission, or public-cloud configuration.
8. The Runner is not exposed directly to the public internet.
9. WebSocket, mTLS, automatic discovery, private repositories, remote administration, and shared diagnostics are not part of 4.6.
10. QR generation and QR scanning are not implemented in 4.6. They remain an unassigned future option. No CAMERA permission, QR library, or Google Play services dependency is added.

## 3. Transport modes and startup gates

The Runner has one active application connector per process. It does not serve paired HTTPS and unauthenticated HTTP concurrently.

| Mode | Bind | API authority | Intended use |
|---|---|---|---|
| `DEVELOPMENT_HTTP` | `127.0.0.1` or `::1` only | fixed `local-development` principal | debug APK plus ADB reverse only |
| `PAIRED_HTTPS` | exact loopback or exact user-specified LAN address | authenticated paired principal | secure debug/release operation |

Initial configuration uses `REPRODROID_TRANSPORT_MODE=DEVELOPMENT_HTTP|PAIRED_HTTPS`. An unset value preserves the existing loopback development behavior during the 4.6 migration, but it is reported as development-only and never as paired or secure. An empty or unknown value fails startup.

`DEVELOPMENT_HTTP` requires a loopback bind, rejects pairing routes, rejects release-client claims, and cannot be expanded with `REPRODROID_ALLOW_UNAUTHENTICATED_NON_LOOPBACK`. The old non-loopback risk-acknowledgement variable is rejected when set to `true` after the 4.6 migration.

`PAIRED_HTTPS` requires initialized key material, an exact bind address, an exact advertised endpoint host, and an HTTPS port. Wildcard bind addresses such as `0.0.0.0` and `::` are not accepted in the initial contract. The configured endpoint contains only `https`, host, and port; userinfo, query, fragment, and non-root path are rejected. The initial default HTTPS port is 8443. Port collision or invalid key material fails startup without opening an HTTP connector.

ADB operation uses an explicit reverse mapping such as `tcp:8443 -> tcp:8443` and an endpoint whose hostname or IP is present in the leaf certificate. LAN operation requires an exact IP or DNS name selected by the user. Changing between loopback and LAN is not an implicit fallback.

## 4. Runner identity, local CA, and leaf certificate

The existing persistent `runnerId` remains the Runner identity. TLS initialization must not replace it. Missing, duplicated, malformed, or conflicting identity data fails closed.

The initial certificate profile is:

| Item | Initial contract |
|---|---|
| Root key | ECDSA P-256 generated from a cryptographically secure RNG |
| Root signature | SHA-256 with ECDSA |
| Root validity | 5 years; `notBefore` is creation time minus 5 minutes |
| Leaf key | ECDSA P-256; independent from the root key |
| Leaf validity | 30 days; `notBefore` is creation time minus 5 minutes |
| Renewal | generate a new leaf when 7 days or less remain; keep the same root |
| TLS protocol | TLS 1.3 preferred; TLS 1.2 allowed; older protocols rejected |
| Pin | `sha256/` plus Base64 SHA-256 of the root certificate SubjectPublicKeyInfo |
| SAN | exact configured endpoint IP as IP SAN or DNS name as DNS SAN |

The certificate common name is not used instead of SAN verification. Expired or not-yet-valid certificates, hostname mismatch, incomplete chains, wrong EKU, malformed key usage, wrong Runner pin, and unsupported algorithms are rejected before any pairing secret or bearer token is sent.

The paired HTTPS connector presents the leaf and issuing local root as a complete chain. On first pairing, Android treats the presented self-signed root as a connection-specific trust-anchor candidate only after its SPKI SHA-256 matches the manual payload pin and the complete leaf／root profile validates. Android then persists the authenticated root certificate in its private connection store for later handshakes. It does not consult an unauthenticated download URL, accept another system root, or use the pin as a substitute for time, SAN, signature-chain, key-usage, and EKU checks.

The CA private key and leaf private key live under a dedicated Runner state subdirectory with directory mode 0700 and file mode 0600 where POSIX permissions are supported. The implementation rejects symlinks, non-regular files, path escape, group/world-writable parents, unexpected ownership where it can be checked, partial files, and key/certificate mismatch. Creation and renewal use owner-specific staging, fsync, and atomic replacement on the same filesystem. Private key bytes never enter SQLite.

First initialization is an explicit local CLI action. Secure startup does not silently generate a replacement root when an expected root is missing or corrupt. Leaf renewal under the same root is automatic and audited. Root replacement is an explicit local action, changes the pin, revokes the old trust relationship, and requires re-pairing.

## 5. Manual pairing payload

Phase 4.6 uses manual input or paste. The Runner CLI emits a bounded textual payload and the same values in a human-readable field list. It does not emit a QR code.

The one-line transport form is the unpadded Base64url encoding of a strict UTF-8 JSON object. Its fields are exactly `schemaVersion`, `endpoint`, `runnerId`, `rootSpkiSha256`, `invitationId`, `invitationSecret`, and `expiresAt`; `schemaVersion` is initially `1`. The separately printed field list is a display of the same values, not a second accepted wire format.

The payload is versioned and contains only:

- pairing schema version;
- exact HTTPS endpoint;
- canonical `runnerId`;
- root SPKI SHA-256 pin;
- canonical invitation ID;
- 256-bit random Base64url invitation secret without padding;
- invitation expiry in UTC RFC 3339.

The decoded payload is at most 4 KiB and uses strict UTF-8. Unknown versions, duplicate fields, unexpected fields, invalid Base64url, non-canonical UUIDs, expired timestamps, non-HTTPS endpoints, URL credentials, query, fragment, and non-root paths are rejected before network access.

The Android pairing screen treats the payload as a secret until it expires. It does not persist the invitation secret in Room, saved state, analytics, logs, crash text, or completed credential files. Process death before completion invalidates the local attempt and requires a new invitation.

## 6. Pairing state machine

The local Runner CLI provides conceptual operations for security initialization, opening an invitation, listing pending requests, approving or rejecting a request, listing principals, revoking a principal, and previewing／executing legacy ownership adoption. Exact command spelling may follow the existing application entry-point style, but no LAN admin endpoint is introduced.

### 6.1 Invitation

Invitation state is `OPEN`, `CONSUMED`, `EXPIRED`, or `LOCKED`. One invitation is valid for 5 minutes and may create at most one pairing request. The Runner stores only the invitation-secret SHA-256, not the raw secret. A Runner has at most 8 non-terminal invitations.

An invitation is locked after 5 invalid secret attempts. In addition, pairing mutation is limited to 10 attempts per minute per observed source address and 30 attempts per minute globally. Rate-limit counters are bounded and do not expose whether an invitation ID or secret was correct. Restart preserves invitation terminal state and expiry; it does not reopen or extend an invitation.

### 6.2 Android request

Before the request, Android:

1. parses and bounds the manual payload;
2. opens TLS to the exact endpoint;
3. verifies certificate validity, SAN hostname／IP, the presented chain to its self-signed root, and that root's SPKI pin against the manual payload;
4. verifies that the TLS endpoint reports the expected `runnerId` through the restricted pairing exchange;
5. generates a 256-bit bearer secret, an independent 256-bit continuation secret, and canonical IDs locally;
6. encrypts the pending raw secrets using Android Keystore before transmitting their hashes.

The create request contains the invitation ID and raw invitation secret, a non-authoritative device display name, token ID, SHA-256 of the full bearer token, continuation ID, and SHA-256 of the continuation secret. It never sends a client-selected `principalId`.

The Runner validates the invitation atomically, marks it consumed, creates one `PENDING_APPROVAL` request, and returns only the request ID, expiry, and a bounded confirmation fingerprint. The pairing request expires with the invitation's original expiry; request creation does not extend it.

### 6.3 PC approval

The local CLI shows request ID, bounded device label, endpoint, creation time, and confirmation fingerprint. The operator explicitly approves or rejects that request. Approval creates a new canonical `principalId`, activates the supplied token hash, and writes an audit event in one transaction. It does not grant ownership of legacy or another principal's resources.

Android polls only the restricted pairing-status endpoint using the continuation credential. `APPROVED` allows the already-generated bearer token to become active; the Runner never returns the raw bearer token. `REJECTED`, `EXPIRED`, `LOCKED`, malformed, or mismatched responses delete the pending local encrypted secrets and do not create a usable connection.

Pairing request states are `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `EXPIRED`, and `FAILED`. Repeated approval／rejection is idempotent for the same terminal request. Approval after expiry, approval of a consumed request with different data, or approval when durable state cannot be committed fails without activating the token.

## 7. Bearer token and authentication

The bearer token contains a versioned prefix, a non-secret token ID, and a 256-bit Base64url secret. The complete value is bounded to 256 ASCII bytes and is sent only in the `Authorization: Bearer` header. Query parameters, cookies, URL userinfo, request bodies, redirects, and proxy credentials are not used for authentication.

Runner SQLite stores token ID, SHA-256 of the complete canonical token, principal ID, created time, last-used time rounded to a non-sensitive resolution, and revocation state. It does not store the raw token. Lookup uses token ID followed by constant-time hash comparison. Unknown, malformed, or revoked credentials return the same bounded 401 response without principal／token enumeration.

The initial bearer has no automatic time-based expiry because existing background synchronization must survive unattended operation. The UI and CLI show credential age, and rotation is explicit re-pairing. This does not weaken revocation: a compromised token remains valid only until Runner-side revocation, so lost-device revocation is mandatory operational guidance. Adding automatic expiry or refresh tokens requires a new credential contract and migration.

In `PAIRED_HTTPS`, every normal `/v1` and `/v2` route, including health, capabilities, operation status, Job reads, logs, Manifest, scan, artifact metadata, and artifact content, requires an active token. Only the minimum versioned pairing routes are outside bearer authentication, and they remain protected by TLS, invitation／continuation credentials, expiry, attempt limits, and PC approval.

The authenticated principal is placed in the server request context and passed to every ownership check. JSON or path values cannot override it. Existing v2 contract and idempotency checks run after authentication and before side effects. A replay using a revoked token does not return a cached operation result.

HTTP redirects are disabled for Runner requests. A 3xx response is an error and the client does not forward `Authorization`, invitation secret, or continuation secret to another origin or path.

## 8. Android credential storage and connection model

Room21 stores non-secret Runner connection records keyed by `runnerId`, including endpoint, root pin, CA certificate reference and digest, display state, credential file reference, principal ID, created／updated time, revocation knowledge, and whether the record is active. It never stores raw bearer, invitation, continuation, or Android Keystore key bytes.

The initial UI supports one active Runner. Multiple records may be retained only to support safe replacement and future expansion, but background work and normal operations use exactly one explicitly active record. Ambiguous or multiple-active state fails closed and requires user repair.

Raw pending and active token material is stored in `noBackupFilesDir` in a versioned private envelope encrypted with an Android Keystore AES-256 key using GCM and a provider-generated random IV. The key allows encrypt／decrypt only, uses no biometric or per-use user-authentication requirement, and therefore remains usable by existing background Job synchronization. StrongBox is not required. Authentication tag failure, missing key, invalidated key, unexpected envelope version, wrong runner／principal binding, truncated file, duplicate record, or file／Room mismatch makes the connection unusable and requires explicit local cleanup or re-pairing.

Credential writes use a private temporary file, fsync, and atomic rename. Old plaintext or partial files are never accepted. Android backup remains disabled and the credential file is excluded from any later `.rdbak` or log export.

The release client rejects every HTTP Runner endpoint in application code. A debug client may use HTTP only when built and launched in explicit development mode and only for loopback. Paired mode always uses HTTPS, regardless of build type. The current build-time URL may remain only as a development default; it is not a paired Runner registry.

## 9. Endpoint changes and certificate renewal

Leaf renewal under the same pinned root, with a valid SAN for the unchanged endpoint, does not require re-pairing. Android still performs full certificate-time, SAN, chain, and pin validation on every new TLS connection.

Changing the endpoint host or port is an explicit user action. If the old authenticated endpoint is available, Android may request and verify a bounded endpoint-change statement from the same `runnerId` and pinned root before activating the new endpoint. If it is unavailable, a new manual pairing payload is required. A DNS result, redirect, certificate common name, or matching display name cannot change the endpoint.

Changing or losing the root key always requires re-pairing. The app does not accept a second pin merely because the Runner ID or endpoint matches.

## 10. Revocation and local deletion

An authenticated Android client may request self-revocation. It deletes its local credential only after a durable successful revocation response or after the user chooses the separate local-delete action. A response loss is reconciled through an authenticated status read while the token remains valid; a 401 is displayed as unknown／revoked, not as confirmed local deletion.

The PC CLI can revoke a principal without the device. Revocation becomes effective for new requests immediately after the durable state change. Existing network calls may finish only if authentication and authorization were already completed and no later domain recheck is required; queued side effects not yet durably accepted do not start. Active Jobs continue under Runner-owned timeout／cleanup rules.

Re-pairing creates a new principal and token. It does not reactivate a revoked token or automatically inherit the old principal's resources. Any ownership transfer uses the explicit adoption flow below.

## 11. Legacy and revoked-principal ownership adoption

The fixed `local-development` principal and any revoked principal are never silently converted to a new paired principal. To preserve deliberate upgrade continuity, the local CLI provides two steps:

1. a bounded preview listing source principal, target active principal, resource kinds, counts, conflicts, active／review／cleanup states, and an expiry-bound preview ID;
2. a separate execute command that repeats all checks and requires the exact preview ID.

The initial legacy adoption supports one source principal and one target principal at a time. It changes only server-side principal ownership fields for versioned resources enumerated by the contract. It does not create RCE acknowledgement, scan review, retry permission, trust, install eligibility, token, pairing authority, or Android local history. Active Jobs, pending cleanup, unresolved reconciliation, idempotency collisions, missing rows, changed counts, unknown schema, or partial database failure stop the entire adoption transaction.

The preview expires after 10 minutes and is single-use. Execute writes an audit header and per-resource counts in the same transaction as ownership changes. There is no partial adoption. Android re-fetches capabilities and owned state after adoption; it does not infer success from the CLI exit code alone.

## 12. API and capability contract

Paired secure operation adds capability `runner-authentication@1`. It is returned only after bearer authentication from a `PAIRED_HTTPS` Runner. `foundationContractVersion` remains 1; authentication is an additive capability and transport gate, not raw comparison protocol v2.

The pairing exchange is `/pairing/v1` and is versioned independently from Runner API v1／v2. Its routes and exact initial JSON shapes are:

- `GET /pairing/v1/identity` returns `{schemaVersion, runnerId}` after the TLS chain／pin／SAN checks and before Android sends an invitation secret;
- `POST /pairing/v1/requests` accepts exactly `{schemaVersion, runnerId, invitationId, invitationSecret, deviceDisplayName, tokenId, tokenSha256, continuationId, continuationSha256}` and returns HTTP 202 with `{schemaVersion, runnerId, requestId, state, expiresAt, confirmationFingerprint}`;
- `GET /pairing/v1/requests/{requestId}` requires `Authorization: ReproDroid-Continuation <continuationId>.<continuationSecret>` and returns the preceding response fields plus `principalId` only for `APPROVED`;
- `POST /pairing/v1/requests/{requestId}/cancel` requires the same continuation authorization and a strict empty JSON object, and returns HTTP 204 for the matching pending request or an idempotent matching terminal request;
- authenticated self-revocation is the normal bearer-protected `POST /v2/authentication/self-revoke` with a strict empty JSON object, returning `{schemaVersion, runnerId, principalId, state, revokedAt}`.

`tokenSha256` and `continuationSha256` are lowercase 64-character hexadecimal SHA-256 values. The canonical bearer is `rdb1.<canonical tokenId UUID>.<43-character unpadded Base64url secret>`. The continuation credential uses the same canonical UUID and 43-character secret constraints but is never accepted by a normal API route.

The pairing HTTP surface is otherwise limited to identity, request creation, status, and cancellation of the same pending request. Principal listing, approval, rejection, revocation of other devices, CA replacement, and ownership adoption are local CLI operations, not pairing HTTP routes.

All pairing JSON uses strict parsing, duplicate-key rejection, maximum nesting 16, maximum response 64 KiB, maximum string 4 KiB unless a smaller field limit applies, and actual-byte counting. Pairing errors do not contain raw secrets, hashes, certificate paths, key aliases, internal endpoints, tokens, or stack traces.

## 13. Schema and migration boundaries

Runner SQLite12 adds normalized tables for certificate metadata, pairing invitations, pairing requests, principals, credentials, revocation events, and ownership-adoption previews／audit. Private key and raw secret bytes remain outside SQLite. All foreign keys and uniqueness constraints are created before the schema version advances.

Android Room21 adds Runner connection metadata and credential references. Migration 20 to 21 preserves every existing app, release, schedule, candidate, notification, Job, comparison, trust, hold, toolchain reference, and setting. It does not synthesize a paired Runner from `BuildConfig.RUNNER_BASE_URL` and does not copy secrets into Room.

Room21 also adds a nullable `jobs.runnerId` reference for outbound resource binding. Existing Jobs remain `NULL`; migration and pairing never guess their Runner or principal. A paired Job may be sent only through a request session bound to the same Runner, and a newly returned ID must not overwrite another Runner's local history. Legacy unbound history stays readable locally but is not silently attached to a new paired connection. Server-side adoption and refreshed server-owned state are separate from any Android local-history binding decision.

Both migrations use the existing no-destructive-fallback policy. Unknown newer schema, insufficient capacity, failed snapshot, interrupted migration, missing key material, or identity conflict preserves the original database and reports a recoverable failure. Migration success alone is not pairing or TLS acceptance.

## 14. UI contract

The existing `settings/authentication` route becomes the secure Runner screen. It provides:

- current active Runner, endpoint, Runner ID, pin fingerprint, transport mode, and local/server revocation knowledge;
- manual pairing payload input or paste and a field-level validation summary;
- pending request state and bounded confirmation fingerprint;
- connection／certificate／authentication status without exposing credentials;
- self-revoke, local-delete, endpoint-change, and re-pair actions with distinct confirmation text;
- a clear indication that PC approval and PC-side lost-device revocation are required;
- a clear indication that pairing does not authorize builds, scan continuation, trust, or installation.

There is no QR scanner, camera affordance, automatic LAN discovery, token reveal, copy-token action, or "secure" label for development HTTP. English base resources and Japanese resources remain key-aligned.

## 15. Android 16 LAN permission boundary

The current application targets SDK 36. Phase 4.6 does not declare `ACCESS_LOCAL_NETWORK`, which is the target SDK 37 path. Android 16 LAN acceptance uses the platform's opt-in local-network-protection test mode to verify allow, deny, later revocation, error display, and retry after user grant. Loopback／ADB and LAN results are recorded separately.

Future target SDK 37 migration must add the then-current manifest and runtime permission contract as a separate reviewed change. Phase 4.6 does not predeclare a future permission or treat Android 16 implicit LAN access as proof of Android 17 compatibility.

## 16. Negative-test and acceptance ledger

All items start as `NOT_RUN`. Unit or fixture coverage and product-path evidence are reported separately.

| ID | Case | Required observation |
|---|---|---|
| RC46-01 | unset／unknown transport mode | bounded migration default or startup refusal; no unintended connector |
| RC46-02 | development HTTP with non-loopback or wildcard bind | startup refusal; no old override bypass |
| RC46-03 | paired mode with missing／corrupt／symlink key material | startup refusal; no new root or HTTP fallback |
| RC46-04 | first explicit CA initialization and restart | same runnerId, root pin, restricted files; no secret log |
| RC46-05 | leaf renewal under same root | new valid leaf, same pin, uninterrupted re-authentication |
| RC46-06 | root replacement | old client rejects; new manual pairing required |
| RC46-07 | TLS 1.1／expired／not-yet-valid／wrong EKU | handshake fails before credential transmission |
| RC46-08 | hostname／IP SAN mismatch | rejection before invitation or bearer transmission |
| RC46-09 | wrong root pin with otherwise valid chain | rejection and no TOFU／system-CA fallback |
| RC46-10 | redirect from pairing or normal API | rejection; no credential forwarded |
| RC46-11 | malformed／oversized／unknown manual payload | local rejection before network access |
| RC46-12 | invitation expiry | no request or extension; terminal state survives restart |
| RC46-13 | invitation secret reuse | exactly one request; later use rejected uniformly |
| RC46-14 | invalid-attempt and rate limits | lock／429 boundary without ID enumeration |
| RC46-15 | response loss／duplicate pairing create | no duplicate principal or active token |
| RC46-16 | PC reject／request expiry／approval after expiry | no active bearer; pending local secret removed |
| RC46-17 | Runner restart while pending | state and expiry preserved; no auto-approval |
| RC46-18 | token hash insert failure during approval | no approved state or partially active principal |
| RC46-19 | missing／malformed／unknown bearer | uniform 401; no protected response body |
| RC46-20 | revoked bearer and same idempotency key replay | 401; no cached operation or side effect |
| RC46-21 | principal ID in JSON or another principal's resource ID | server-context principal wins; access rejected |
| RC46-22 | health／capabilities／logs／artifact without auth | rejected in paired mode |
| RC46-23 | Authorization in logs／errors／Docker／export | no raw or reversible credential occurrence |
| RC46-24 | Android Keystore key missing／invalidated／wrong alias | fail closed; no plaintext fallback |
| RC46-25 | credential envelope truncation／tag failure／Room mismatch | fail closed; no request sent |
| RC46-26 | Android process death during pairing／credential publish | no usable partial token; new invitation required if needed |
| RC46-27 | debug development HTTP and release HTTP | debug explicit loopback only; release rejects before request |
| RC46-28 | HTTPS over ADB reverse | pairing and authenticated normal API succeed without HTTP |
| RC46-29 | explicit Android 16 LAN allow | exact endpoint HTTPS and authenticated API succeed |
| RC46-30 | Android 16 LAN deny／later revoke／re-grant | bounded error, no fallback, explicit retry after grant |
| RC46-31 | Android local delete only | local secret gone; server revocation not claimed |
| RC46-32 | authenticated self-revoke and response loss | durable reconciliation; no false success |
| RC46-33 | PC lost-device revocation | next request rejected; history and active cleanup preserved |
| RC46-34 | re-pair after revocation | new principal／token; no automatic old ownership |
| RC46-35 | legacy adoption preview changes before execute | stale rejection; no partial ownership change |
| RC46-36 | adoption with active／unknown／conflicting resources | entire transaction rejected |
| RC46-37 | successful explicit legacy adoption | exact audited counts; no RCE／scan／trust privilege creation |
| RC46-38 | Room20 to 21 migration and process-death cut points | all existing data preserved; no synthesized pairing |
| RC46-39 | SQLite11 to 12 migration and process-death cut points | original DB recoverable; identity and prior resources preserved |
| RC46-40 | unknown API／capability／schema | affected operation stops; local history remains |
| RC46-41 | raw comparison／trust／signer／install regression | no state promotion or policy change from pairing |
| RC46-42 | scheduled release check with Runner unavailable | 4.5 metadata-only path remains independent |
| RC46-43 | QR／camera／Google Play services inventory | no implementation, permission, or dependency added |
| RC46-44 | non-public locally signed release product path | HTTPS only; pairing／revocation work; artifact not treated as distributable release |

## 17. Verification order

1. Strict model, certificate, pin, token, rate-limit, redirect, and state-machine unit tests.
2. Runner SQLite11 to 12 migration, restart, response-loss, concurrent approval／revocation, and actual local TLS integration.
3. Android Room20 to 21 generated schema, migration, Keystore envelope instrumentation, process-death, and UI state tests.
4. Full Runner regression, Android debug／release JVM tests, lint, assemble, and AndroidTest APK compilation.
5. Dedicated task-owned Runner state and disposable emulator acceptance for HTTPS over ADB reverse.
6. Explicit LAN acceptance on Android 16 opt-in protection, without automatic network or firewall changes.
7. A non-public release APK signed with a task-specific key outside the repositories. Distribution signing remains 4.8.
8. Existing truth, RCE, scan, comparison, install, storage, toolchain, and scheduled-check non-interference checks.
9. Documentation, exact full HEAD, compatibility metadata, diff, secret scan, and clean-worktree closeout.

Missing device, signing key approval, LAN configuration, or product-path execution remains `NOT_RUN`; it is not replaced by unit tests or compilation.

## 18. Code-entry sequence

1. Runner certificate／identity store and SQLite12 migration.
2. Runner pairing state machine, local CLI, token store, authentication middleware, and ownership propagation.
3. Runner HTTPS connector and explicit development-mode migration.
4. Android Room21 connection metadata and Keystore-backed credential envelope.
5. Android manual pairing client, strict TLS／pin verifier, authenticated Runner client, and redirect refusal.
6. Authentication／revocation／local-delete／endpoint UI wiring; remove the 4.6 placeholder only when functional.
7. Legacy ownership preview／execute, migration and negative tests.
8. Automated regression, product acceptance, documentation synchronization, and local commits.

Implementation must use pinned dependency versions and record license／NOTICE obligations before adding a certificate or CLI library. QR or Google Play services must not be introduced as a convenience dependency.

## 19. References

- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android `KeyGenParameterSpec`](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)
- [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Ktor client SSL](https://ktor.io/docs/client-ssl.html)
- [Ktor server SSL](https://ktor.io/docs/server-ssl.html)
- [RFC 6750: Bearer Token Usage](https://www.rfc-editor.org/rfc/rfc6750)
- [RFC 5280: X.509 PKI Certificate Profile](https://www.rfc-editor.org/rfc/rfc5280)
