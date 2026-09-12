# ADR-0025: Phase 4.7 Codeberg provider and APK selection

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted, implemented and locally verified on 2026-09-09
- Date: 2026-09-08
- Contract: [Phase 4.7 Codeberg provider expansion](../design/phase-4-codeberg-provider-contract.md)

## Context

Phase 4.1 and 4.5 implemented public GitHub repository registration, release discovery and metadata-only scheduled checks. Phase 4.4 implemented explicit selection when a GitHub release exposes multiple standalone APK candidates. The persisted binding, cooldown and observation models carry provider identity, but Android repository orchestration, wire DTOs, URL policy and download code remain directly coupled to GitHub. Runner generic build likewise accepts only canonical `github.com` source URLs.

Phase 4.7 adds the second planned provider without weakening the existing GitHub, immutable-history, scheduled side-effect, RCE, scan-review, Docker or raw-comparison boundaries. Codeberg's Forgejo API does not expose all GitHub attachment fields: provider MIME and digest may be absent, attachments can be `external`, repositories may advertise SHA-1 or SHA-256 object formats, and conditional-request or rate-limit behavior differs by instance and response.

## Decision

1. Support only public `codeberg.org` as the second provider. Do not add private credentials, GitLab, arbitrary Forgejo／Gitea instances, HTML scraping or automatic external-asset following.
2. Keep Android authoritative for provider metadata, release and asset discovery, download, APK inspection, selection, history and display. Runner owns only validated source checkout, scan, build, artifact and comparison work.
3. Introduce a closed Android provider registry for `GITHUB/github.com` and `CODEBERG/codeberg.org`. Wire DTOs, endpoints, pagination, tag resolution, rate-limit handling and URL／redirect policies remain provider-specific; repository, release, asset and failure results use provider-neutral domain types.
4. Accept only Codeberg repositories that explicitly advertise SHA-1 object format. SHA-256 and unknown formats stop as unsupported; they are not inferred from a returned commit length.
5. Add Runner capability `codeberg-source@1`. Android requires it before creating a Codeberg generic build. Existing GitHub builds continue to require the existing generic-build and comparison capabilities only. API v2 request shape and Runner SQLite12 remain unchanged.
6. Move Android to Room22. Preserve all Room21 rows, IDs, foreign keys, files, comparison references and legacy observation hashes. Store provider MIME and digest as nullable metadata, provider asset creation time separately from response MIME, nullable release publication time, versioned selection conditions and versioned metadata observations.
7. Do not overwrite the provider observation, recorded raw SHA-256, parsed APK identity or referenced file meaning after a computed raw SHA-256 is recorded. A byte-identical refetch may reuse it. A different byte stream forks to a new snapshot, asset row and file after bounded APK validation; prior comparison and installation references remain attached to the old content. Current installed-state evaluation, availability and attempt/error state may still change, and explicit cleanup follows the existing retention contract.
8. Treat Codeberg `type=attachment` as eligible and reject `external`. Candidate discovery requires a bounded positive size, provider-controlled URL and `.apk` name. Download accepts only a closed MIME set, verifies size and streaming SHA-256, checks a provider digest when present, and then requires a signed standalone APK with parsed package／version／signer identity. Missing HTTP MIME, unrelated MIME, split packages and bundle containers fail closed.
9. Select automatically only when exactly one candidate exists or an explicitly saved ABI／variant／exact-filename condition matches exactly one candidate. Default preferences are not saved conditions. Manual selection is release-observation-local and never stores or reuses an asset ID as a future condition.
10. Extend scheduled checks to Codeberg metadata, candidate persistence and notification only. They must not issue APK GET／HEAD requests or start Runner, toolchain, clone, scan, build, comparison, trust or install work. Codeberg response equality is not reported as HTTP 304 when the server supplied no 304.
11. Keep immutable provider observations. Tag movement, metadata replacement, asset replacement and downloaded-byte replacement create new history rather than mutating old truth.
12. Accept Phase 4.7 with at least one live public Codeberg Android project through registration, source discovery, release and APK selection, download and inspection, configuration, paired Runner Build A/B and raw three-axis comparison. A `MATCH` result is not required; truthful raw results are.

## Alternatives rejected

- Reusing GitHub DTOs with fabricated `uploaded`, MIME or digest values: records provider claims that Codeberg did not make.
- Enabling every Forgejo-compatible host: expands network and credential authority beyond the approved public instance.
- Silent expansion of `generic-build@1`: old Android clients cannot distinguish a Runner that validates Codeberg sources from one that rejects or mishandles them.
- Treating created time as published time: changes provider evidence and ordering semantics.
- Overwriting a downloaded asset after content changes: rewrites the meaning of existing comparison and install foreign keys.
- Reusing an asset ID across releases: provider IDs do not establish byte or release immutability.
- Accepting arbitrary generic or missing MIME without APK inspection: permits unrelated bounded files to enter the reference path.
- Supporting SHA-256 repositories by truncation or inference: breaks the current Android／Runner／API／manifest SHA-1 contract.

## Consequences

Android gains provider abstraction and a Room21→22 migration. GitHub behavior remains a regression target. Provider metadata and actual HTTP／APK observations are displayed separately. Codeberg source builds require an updated Runner advertising `codeberg-source@1`; repository metadata and APK retrieval never move into Runner.

The first live acceptance target is `UnifiedPush/android-example`, revalidated immediately before execution. `Sunup/android` is a fallback when the primary target is unavailable or incompatible. External repository state is evidence captured at execution time, not a permanent fixture guarantee.

Earlier Phase 4.1／4.2／4.4／UI-R／4.5 product gaps remain independent and are not promoted by Phase 4.7 automation.

Post-implementation evidence is tracked in private verification records and the product target ledger. Live Android evidence satisfies registration, source discovery, release／APK selection, download／inspection and cold start, while API 36 instrumentation satisfies Room21→22 migration. Independent `ftpclient` Build A／B Jobs each completed after their own RCE acknowledgement and digest-bound scan review, and Android persisted the raw axes as Official-vs-A `DIFFERENT`, Official-vs-B `DIFFERENT`, A-vs-B `MATCH`. Decision 12 and Phase 4.7 product acceptance are complete without claiming reproducibility or install eligibility. The public verification state is recorded in [Current status](../status/current.md).
