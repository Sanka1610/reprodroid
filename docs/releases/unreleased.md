# Unreleased Phase 5 changes

- Status: Draft; candidate source identity fixed, no rebuilt or accepted release candidate exists
- Updated: 2026-09-14
- Android candidate source identity: `0.1.0-alpha04`／`versionCode 4`
- Runner: production source remains `0.1.0-alpha02` unless later release work changes its distribution

These notes summarize locally integrated source changes after the frozen Phase 4 `0.1.0-alpha03` artifact. They are not a release announcement and do not provide an APK, checksum, signature, provenance, or publication authorization.

## User-facing changes

- Reorganized Apps, Add app, app detail, comparison, Settings, Runner, and Jobs around primary actions and progressive technical detail.
- Added horizontally swipeable Registered apps／Add app／Settings root pages with synchronized navigation controls.
- Added compact app rows, group accordions, drag ordering, inline search／filters, and explicit app tracking history.
- Added Settings accordions, Pure black, optional child dividers and selection outlines, and persistent expansion state.
- Separated Android notification permission from release-check notification policy.
- Added interval sliders from 1 through 24 hours, specified-time scheduling, charging-only policy, and per-app update overrides.
- Added offline ReproDroid and third-party license screens and fixed public project／author links.
- Added an offline self-registration on a genuinely fresh database without starting network or execution work.
- Refined English／Japanese copy, font-scale behavior, selection layout, exit confirmation, bottom operation messages, and self-app uninstall targeting.

## Internal structure and persistence

- Replaced the legacy dual-root UI with one `MainActivity` -> `ReproDroidApp` production chain.
- Split full screens into feature packages and centralized route／back／missing-record／notification policy.
- Kept `ManagedAppsViewModel` as a facade while extracting eight delegates, immutable feature states, action gates, and owner-scoped one-shot results.
- Moved activity-result coordination out of full screen bodies.
- Advanced Android storage from Room22 to Room24 through additive migrations for UI preferences, release notification／charging policy, and fresh-database bootstrap state. Runner remains SQLite12.

## Preserved boundaries

- API v1／v2 and all advertised capability identifiers remain unchanged.
- Scheduled checks remain metadata-only and cannot automatically download, install toolchains, build, compare, change trust, or install.
- Build execution, scan review, raw comparison, signer relation, trust, and installer eligibility remain distinct.
- Paired HTTPS, root pinning, per-device credentials, authorization, revocation, RCE confirmation, scan review, and Android system installer boundaries remain in force.
- No analytics, telemetry, automatic crash upload, private repository token, or automatic external sharing was added.

## Current verification

- Local final unit／build／lint for the last Phase 5.5+ source: `PASS`; lint reported 0 errors and 33 warnings.
- Targeted root navigation instrumentation: `PASS` for 2 tests.
- Android 16 final product UI audit for the bounded Phase 5.5+ follow-up: `PASS`.
- Full connected suite on the final follow-up source: `NOT_RUN`.
- Final release version, full regression, production-signed product journeys, artifact／SBOM／checksum／provenance binding, reachable-history audit, `main` integration, push, tag, and GitHub Release: `NOT_RUN`.

## Before release

Release-candidate work must rebuild every artifact from the final integrated source, run the required full regression and Android 16 journeys, bind source／artifact／SBOM／checksum／signer／compatibility identities, and perform a separate public-tree plus reachable-history audit. Any fix changes source identity and requires the affected artifacts and checks to be regenerated.

See [Current status](../status/current.md) for known limitations, [Getting started](../guides/getting-started.md) for availability and pairing, and [Operations and recovery](../guides/operations.md) for safety and checksum handling.
