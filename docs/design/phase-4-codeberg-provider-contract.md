# Phase 4.7: Codeberg provider expansion and APK selection contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented and locally accepted on 2026-09-09
- Date: 2026-09-08
- Decision: [ADR-0025](../adr/0025-phase-4-codeberg-provider-and-apk-selection.md)
- Previous contracts: [4.0 foundation](phase-4-foundation-contract.md), [4.1 registration](phase-4-registration-contract.md), [4.4 generic build](phase-4-generic-build-contract.md), [4.5 release checks](phase-4-release-check-contract.md), [4.6 Runner connectivity](phase-4-runner-connectivity-contract.md)

## 1. Scope and authority

Phase 4.7 supports unauthenticated public repositories on exactly `codeberg.org`. It connects repository registration, bounded Gradle source discovery, release metadata, standalone APK selection, explicit download, generic Build A/B and raw comparison. GitLab, private repositories, tokens, arbitrary Forgejo／Gitea hosts, HTML scraping, external asset links, split APKs, APKS, XAPK, APKM and AAB are outside scope.

Android owns provider connections, release and attachment metadata, download, APK inspection, selection, Room history and UI. Android satisfies the existing capability, configuration and RCE acknowledgement gates before creating a Runner Job. Runner then receives only a canonical repository URL, full SHA-1 commit and the existing versioned generic-build request, performs source acquisition and scanning, and stops at the existing digest-bound scan-review gate before build execution. Project owns this contract, API／capability documentation, compatibility metadata and acceptance records.

## 2. Provider registry and identity

The provider registry is a closed set:

| provider | instance | repository origin | API origin |
|---|---|---|---|
| `GITHUB` | `github.com` | `https://github.com` | `https://api.github.com` |
| `CODEBERG` | `codeberg.org` | `https://codeberg.org` | `https://codeberg.org/api/v1` |

There is no host-based fallback to a generic Forgejo adapter. Repository identity is provider＋instance＋canonical positive-decimal provider repository ID. Owner, repository name and canonical URL are verified locators and must not replace the provider ID. Release identity adds canonical provider release ID. Asset identity adds canonical provider asset ID within that release.

Repository URLs require HTTPS, no user info, explicit port, query or fragment, and exactly two validated path segments. Encoded separators, encoded dot segments, control characters, invalid Unicode and traversal forms are rejected. `.git` or trailing slash input may be normalized only when the provider parser can produce the same verified owner／repository identity. Codeberg owner and repository ASCII components follow the provider's case-insensitive `lower_name` lookup and are canonicalized to lowercase; tag, branch, tree path and asset filename are not case-normalized.

Codeberg repositories must explicitly return `object_format_name=sha1`. `sha256`, missing and unknown values return `UNSUPPORTED_OBJECT_FORMAT`. Commit and tag resolution still require lowercase 40-hex full SHA-1.

## 3. Bounded repository discovery

Codeberg discovery verifies repository ID, public visibility, owner/name, canonical web URL and default branch before following provider-controlled API links. It resolves the default branch to a full commit, validates the commit tree and traverses complete tree pages without fetching blob bodies. It preserves the existing exclusions for symlinks, submodules, `.git` and `.gradle` content and the existing request, depth, entry, response-byte, total-byte and elapsed-time bounds.

The tree request always sends `per_page=50`. Codeberg's `per_page` response field is an optional echo: when it is absent, the requested 50-entry size remains the client-owned page bound rather than invented provider metadata. When present, it must be a positive integer no greater than 50 and the returned page may not exceed it. `page`, `total_count` and `truncated` remain mandatory; `total_count` must remain stable, the accumulated entry count may never exceed it, and completion requires exact equality with `total_count`. Live Codeberg responses can retain `truncated=true` on the final non-empty page even when the accumulated entries equal the declared total, so that flag is validated but is not used as a completion predicate. An empty page before exact equality is invalid progress.

An unread page, missing or non-Boolean `truncated` field, duplicate path, inconsistent mode, cycle, ID／SHA mismatch or exhausted bound is `INCOMPLETE` or invalid discovery, never an empty tree. Neither `truncated=true` nor `truncated=false` overrides the stable exact-count rule: the client continues while entries remain and stops as soon as the accumulated count equals `total_count`. Source-only registration may be saved only from the verified repository identity and a complete or explicitly bounded discovery result permitted by the existing registration contract.

## 4. Release and channel semantics

Codeberg release DTOs remain separate from GitHub DTOs. Draft releases are excluded. Stable-only excludes prerelease; include-prerelease considers both states. Unknown or malformed booleans, IDs, tags or timestamps fail closed.

`created_at` and `published_at` are stored independently. Missing `published_at` is null and is not copied from `created_at`. Codeberg ordering uses valid provider `created_at`, with canonical numeric release ID as deterministic tie-breaker. Missing or invalid ordering time prevents a complete latest-release decision. Every selected tag is resolved through the provider ref API to a full SHA-1 commit; a changed tag SHA is a new observation.

Release and tree listing follows all required pages within the contract bounds. An unread or truncated page is not treated as release／asset absence. Codeberg does not inherit GitHub ETag, 304 or `X-RateLimit-*` rules. A literal 304 is used only for the representation that produced it. Otherwise a normal response is parsed and hashed.

Codeberg `ratelimit-policy` items are parsed as a quoted policy name with positive decimal `q` quota and `w` window seconds; matching `ratelimit` items carry non-negative decimal `r` remaining and `t` reset seconds. Unknown parameters are ignored, but duplicate parameters, overflow, invalid quoting or invalid decimal values invalidate that item. When several valid policies are returned, every exhausted policy applies and cooldown uses the longest valid reset duration. A successful response with any applicable `r=0` suppresses the next request until that reset. A malformed header is not rate evidence by itself.

HTTP 429 is `PROVIDER_RATE_LIMITED`; valid `Retry-After` takes precedence and otherwise an exhausted valid Codeberg rate item or bounded provider backoff supplies the wait. HTTP 403 is rate-limited only with valid exhaustion evidence; otherwise it is access denied. `Retry-After` on 503 is honored as provider-unavailable retry timing but is not recorded as quota exhaustion. Cooldown is keyed by provider＋instance and persists across apps, manual checks and process restart.

## 5. Attachment candidate contract

Only provider-managed `type=attachment` entries are considered. `external` and unknown types are rejected. An APK candidate requires:

- canonical positive-decimal attachment ID;
- non-empty bounded filename ending in `.apk` case-insensitively;
- positive declared size no greater than 512 MiB;
- a Codeberg-controlled HTTPS release-download URL passing the exact policy below;
- valid optional provider timestamp and optional digest when supplied.

Provider MIME and digest may be absent. Absence is stored as null, never as an invented GitHub value. A malformed supplied digest is invalid metadata.

## 6. Selection contract

Candidate selection is provider-neutral:

1. zero candidates stops with no download;
2. one candidate may be selected;
3. multiple candidates may be selected automatically only when an explicitly saved condition matches exactly one;
4. otherwise the user must select one candidate explicitly before download.

A saved condition is a versioned JSON object with at least one of ABI, variant or exact filename. Conditions are combined with logical AND. It contains no provider asset ID. Default preferences are not treated as user-saved conditions. Manual selection changes only the current immutable release observation and does not save a future condition. A second asset cannot replace an already selected asset in the same observation.

Before download, UI may show filename, provider size, provider timestamp, optional digest and filename-derived ABI／variant hints with their source. Package, version, signer and actual ABI are unknown until the downloaded APK is inspected.

The initial Codeberg attachment URL must have host `codeberg.org`, no explicit port, user info, query or fragment, and decoded path segments exactly `{owner}/{repository}/releases/download/{tag}/{filename}`. Owner and repository must match the verified binding after ASCII lowercase normalization because Codeberg returns display casing in attachment URLs while repository identity uses case-insensitive `lower_name`; only those two segments are compared case-insensitively. The `releases` and `download` literals, tag and filename remain exact case-sensitive matches to the selected release and attachment metadata. Each segment is UTF-8 percent-decoded once, rejects encoded `/`, `\\`, `.`, `..`, control characters and invalid encoding, and is re-encoded canonically before equality comparison. The path may not contain extra or empty segments.

## 7. Download and APK verification

The Codeberg downloader permits only URLs satisfying section 5 and does not follow arbitrary external URLs. Phase 4.7 initially rejects every redirect. A future exact CDN host／path allowlist requires a contract revision and tests. HTTPS downgrade, user info, ports, fragments, queries and unknown hosts are rejected.

For Codeberg only, the response MIME allowlist is exactly `application/vnd.android.package-archive` and `application/octet-stream`. Comparison is ASCII case-insensitive after optional whitespace is trimmed and parameters beginning with `;` are removed. This does not widen the existing GitHub MIME policy. Missing or any other MIME is rejected. Declared provider size, response length when present and received byte count must agree. Download is bounded to 512 MiB, streamed to an app-owned temporary file, hashed with SHA-256 and atomically published only after validation. A supplied provider digest must match.

Android archive inspection must then establish a single installable APK, package name, version and signer. Split APKs and bundle containers are rejected. Generic MIME never bypasses these checks.

## 8. Room22 and immutable observations

Room21→22 is additive or table-rebuild-with-copy and has no destructive fallback. It preserves all IDs, foreign keys, local content paths, comparison／install references and legacy hashes.

Minimum Room22 semantics are:

| table | change |
|---|---|
| `release_assets` | provider content type nullable; provider-created timestamp nullable; actual download content type nullable |
| `release_snapshots` | published time nullable; metadata observation SHA-256 nullable; observation schema version defaults to legacy version 1 |
| `release_candidates` | published time nullable; provider-neutral asset JSON accepts nullable MIME and provider-created timestamp |
| `registered_apps` | nullable versioned saved-selection JSON |

Existing `observationSha256` values are not recomputed, copied into a differently defined hash or used as proof of downloaded-byte immutability. A new metadata row stores its JCS schema-version-2 metadata hash in both `metadataObservationSha256` and the existing unique `observationSha256` identity. The metadata payload covers provider／instance／repository ID／release ID, tag, resolved SHA, release metadata and every supported attachment candidate sorted by canonical asset ID. Fetch time, last-observed time, ETag, redirect query, selection reason, saved condition and installed-device state are excluded.

A downloaded-content fork stores a distinct JCS SHA-256 in its existing unique `observationSha256` field over `{schemaVersion: 2, kind: "DOWNLOADED_CONTENT", metadataObservationSha256, providerAssetId, computedRawSha256}` while retaining the same `metadataObservationSha256`. First successful download may complete the initial metadata snapshot and asset row. Once an asset has a computed raw hash, its provider observation, computed hash, parsed APK identity and referenced file meaning are immutable:

- identical refetch: reuse the observation; a missing file may be restored only after hash equality;
- different bytes: create a new snapshot, asset row and file after all validation succeeds;
- supplied provider digest mismatch: record failure, not a valid replacement;
- invalid APK: do not promote the new content to an available reference.

Every refetch uses an independent temporary file and attempt state. Digest mismatch, HTTP or APK validation failure, process death and stale commit detection leave the previous verified row, file and all comparison／install references unchanged. Current installed-package evaluation, resource availability and bounded attempt/error state may be updated independently. Removing an old byte file requires the existing explicit cleanup and retention authority; it never rewrites its historical hash or foreign-key meaning.

The lookup for a repeated metadata hash prefers the latest valid downloaded-content fork, so later metadata refresh cannot move current UI back to an older byte observation. Commit revalidates app binding, metadata hash and selected asset to stop stale selection／download races.

## 9. Scheduled metadata-only boundary

Both scheduled and user-triggered metadata-only checks may fetch repository, release, ref and attachment metadata and persist candidates, cooldown and notification intent. They must not issue APK GET／HEAD, reserve storage, call Runner, install a toolchain, clone, scan, request RCE review, build, compare, change trust or install.

The existing download-capable refresh method is not called from the Worker. Provider dispatch and pure parsing may be shared; a boolean side-effect flag on the download path is not an acceptable separation.

## 10. Runner contract

Runner keeps SQLite12 and the existing API v2 generic-build body. Its canonical repository validator accepts exactly `github.com` and `codeberg.org` under the same HTTPS and path constraints. Manifest, job persistence, clone and comparison use the same canonical URL.

Runner advertises `codeberg-source@1` only when that validator and source checkout path are implemented. Android requires `generic-build@1`, `apk-comparison@1` and `codeberg-source@1` before creating a Codeberg build. Capability absence or contract mismatch stops before a new Job. No API v1 fallback, HOST fallback, provider metadata lookup or APK download is added to Runner.

## 11. Acceptance ledger

Automated evidence, live provider evidence and product evidence are recorded independently. A fixture or compiled instrumentation test does not satisfy a product item.

| ID | Requirement |
|---|---|
| RC47-01 | closed GitHub／Codeberg provider registry and arbitrary-instance rejection |
| RC47-02 | strict Codeberg URL canonicalization and encoded-input negatives |
| RC47-03 | public repository identity and same numeric ID isolation across providers |
| RC47-04 | explicit SHA-1 acceptance and SHA-256／unknown rejection |
| RC47-05 | default branch, ref, commit and tree full-SHA verification |
| RC47-06 | complete tree pagination, truncation and resource bounds |
| RC47-07 | stable／prerelease／draft channel behavior and deterministic ordering |
| RC47-08 | release tag direct／annotated resolution and peel bound |
| RC47-09 | strict JSON, duplicate key, UTF-8, numeric and response bounds |
| RC47-10 | 403／404／429／503／Retry-After／Codeberg rate-header／offline failure classification |
| RC47-11 | provider＋instance cooldown isolation and restart persistence |
| RC47-12 | `attachment` acceptance and `external` rejection |
| RC47-13 | no, single and multiple APK candidate behavior |
| RC47-14 | saved condition unique／zero／ambiguous matching and explicit save |
| RC47-15 | release-local manual selection and stale replay rejection |
| RC47-16 | metadata MIME／digest absence preserved as null |
| RC47-17 | exact Codeberg URL／redirect allowlist and downgrade rejection |
| RC47-18 | size, MIME, streaming SHA-256 and provider digest download checks |
| RC47-19 | package／version／signer and standalone APK inspection |
| RC47-20 | split APK／APKS／XAPK／APKM／AAB rejection |
| RC47-21 | tag movement creates history without overwriting prior rows |
| RC47-22 | metadata asset replacement creates history |
| RC47-23 | byte replacement forks snapshot／asset／file and preserves comparison FK |
| RC47-24 | repeated metadata resolves to latest valid content fork |
| RC47-25 | scheduled checks have no APK／Runner／build／trust／install side effects |
| RC47-26 | Room21→22 success, cold-open, process-death and invalid-source rejection |
| RC47-27 | GitHub registration, release, selection, download and schedule regression |
| RC47-28 | Runner Codeberg URL acceptance and URL negatives |
| RC47-29 | Runner canonical URL remains stable through job／clone／manifest |
| RC47-30 | `codeberg-source@1` advertisement and Android fail-closed gate |
| RC47-31 | existing RCE, scan review, Docker and comparison gates remain effective |
| RC47-32 | live Codeberg API payload, pagination, rate, MIME and redirect observation |
| RC47-33 | live registration through APK selection and verified download |
| RC47-34 | paired Runner Codeberg Build A／B and raw three-axis comparison |
| RC47-35 | Android cold start／process death restores provider, selection and history |
| RC47-36 | three-repository docs, compatibility, diff, secret and clean-tree audit |

## 12. Live target and reporting

The primary registration／release target is `https://codeberg.org/UnifiedPush/android-example`; `https://codeberg.org/Sunup/android` is the registration fallback. Product acceptance used the primary target through explicit two-APK selection, signed APK inspection and cold-start restoration. Its source requires a Wrapper distribution outside the fixed generic recipe, so the generic Build A／B target used `https://codeberg.org/qwerty287/ftpclient`, whose 3.2.0 release provides a suitable single official APK and whose source passed the fixed Wrapper／toolchain boundaries. Immediately before execution, record public identity, object format, release and asset IDs, tag full SHA, asset list, package／version／signer and toolchain requirements. No target-specific production code was added.

Raw Official-vs-A, Official-vs-B and A-vs-B results are stored as observed. `MATCH` is not a prerequisite and no manifest, scan or auxiliary evidence promotes a raw difference to reproducible.

The accepted `qwerty287/ftpclient` run completed independent Build A／B Jobs and stored Official-vs-A `DIFFERENT`, Official-vs-B `DIFFERENT` and A-vs-B `MATCH`. Because all three axes did not match, the persisted outcome remains `reproducible=false`, `trustEligible=false` and `installEligible=false`. The Android UI restored the stored result after a cold start.

Earlier Phase 4.1／4.2／4.4／UI-R／4.5 gaps retain their independent status. Phase 4.7 reporting separates JVM／fixture／lint／assemble, instrumentation, live API and full product evidence as PASS, PARTIAL or NOT_RUN.
