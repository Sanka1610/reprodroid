# ADR-0023: Phase 4.5 scheduled release discovery and notifications

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Android implementation integrated into local `develop` at `259c91e`; automated Android verification PASS, instrumentation `COMPILE_ONLY`, Android 16 product acceptance `NOT_RUN`
- Date: 2026-09-06
- Scope: Android scheduled release metadata checks, candidate persistence, provider cooldown, notifications, Room20
- Contract: [Phase 4.5 release check contract](../design/phase-4-release-check-contract.md)
- Preparation and implementation evidence: 非公開の実装記録で管理
- Public verification status: [Current status](../status/current.md)

## Context

Phase 4.4 implemented generic Docker build and comparison code, and UI-R added stable Android routes and disabled 4.5 connection points. At decision time, scheduled release discovery was unimplemented. The existing manual `ManagedAppRepository.refresh()` refreshes source metadata and may download a selected APK, so it cannot be invoked from a background Worker without violating the accepted Phase 4 boundary. The pre-4.5 GitHub client was stable-latest only, treated every 403 as rate limited, and did not preserve provider cooldown headers.

The accepted roadmap requires Android-only metadata discovery, durable candidate／outbox state, release channel selection, network／battery constraints, provider-wide serial scheduling, backoff, notification deduplication, and restart recovery. It also forbids automatic download, Runner work, build, comparison, trust mutation, and installation.

## Decision

1. Implement a separate `ReleaseCheckWorker` and metadata-only repository. Do not add a background flag to the existing download-capable `refresh()`.
2. Keep `JobSyncWorker` unchanged. Store schedule truth in Room and use unique one-time WorkManager requests as reconstructable dispatch references.
3. Add Room20 tables for global／app settings, schedule state, check runs, candidates, provider cooldown, notification outbox, and deduplication headers.
4. Convert release／asset provider IDs from `Long`／SQLite INTEGER to canonical decimal `String`／TEXT in Room20. Preserve existing references and observation hashes byte-for-byte without destructive fallback.
5. Limit 4.5 to unauthenticated public GitHub. Codeberg remains 4.7; private repositories and tokens remain out of scope.
6. Use a provider-wide durable serial queue. Respect `Retry-After` and rate reset, classify access denial separately, and preserve cooldown across manual checks and restart.
7. Treat 304 as representation-level information. Re-resolve the stored tag to a full SHA and never infer unchanged APK bytes, signer, update relation, or trust.
8. Persist the candidate and outbox intent before posting a notification. A notification only opens the stable app Information route.
9. Limit 4.5 notification types to release candidate, verification-required, asset-selection-required, and already-verified exact-candidate states. Build／comparison／transient error notifications are outside 4.5.
10. Request `POST_NOTIFICATIONS` only after an explicit Updates-screen action. Permission denial or app mute does not delete candidates and does not trigger bulk replay when later enabled.
11. Retain detailed check and terminal outbox payloads for 90 days, minimal dedup headers for 365 days, and protect current／unseen／pending／referenced records.
12. Keep GitHub Stars import as a separate follow-up after the 4.5 provider queue and cooldown have product acceptance.

## Alternatives rejected

| alternative | reason |
|---|---|
| Call existing `refresh()` from WorkManager | It can refresh source discovery and download APK bytes, violating the background authority boundary |
| Add `background=true` branches throughout `refresh()` | Makes forbidden side effects dependent on dispersed conditionals and weakens reviewability |
| Reuse `JobSyncWorker` | Mixes Runner Job synchronization with independent provider metadata ownership and failure policy |
| Store only WorkManager state | Loses authoritative due time, cooldown, and retry evidence across replacement and reconciliation |
| Treat `BatteryNotLow` as 20% | Android's low-battery policy is not the contract's explicit 20%／21% boundary |
| Treat all 403 as rate limit | Hides access denial and can create repeated unauthorized polling |
| Post directly after network response | Notification may exist without a durable candidate or may duplicate after process death |
| Reuse previous release trust for a new tag／asset | Violates immutable release identity and raw comparison truth |
| Include GitHub Stars in core 4.5 | Expands rate, queue, selection, and UX scope before the shared provider foundation is accepted |
| Add Firebase／Google Play services／exact alarms | Outside agreed dependencies and unnecessary for deferrable metadata checks |

## Consequences

Android receives a Room19→20 migration and a new background subsystem, but Runner API／SQLite remain unchanged. Existing manual download and comparison flows continue as explicit user actions. Scheduled discovery may be delayed by OS or provider constraints and does not claim exact execution time. Notification permission denial reduces delivery only; the candidate remains available in-app.

The provider-ID migration is intentionally part of 4.5 because candidate and cooldown state establish the provider-neutral basis used by Codeberg in 4.7. Migration failure must preserve the Room19 database and stop startup through the existing migration gate; it must not reset user data.

Implementation follows this decision in Android Room20. Android feature commit `6e4ca30` is integrated into local `develop` at `259c91e`; Runner's 4.5 boundary-only documentation commit `4a3f1cb` is integrated into local `develop` at `7206dca`, while Runner production code／SQLite11／API remain unchanged; Project documentation commit `3f94329` is integrated into local `develop` at `50ce8d4`. No Phase 4.5 branch has been pushed or integrated into local `main`. Debug／release JVM tests each pass 147 tests with no skips or failures; lint passes with 0 errors, 32 warnings, and 1 hint; debug／release assemble and AndroidTest APK compilation pass. Room migration／DAO／repository／Worker instrumentation sources are compile-only because no device was connected. Acceptance remains separate from implementation: the Android 16 scheduled, live GitHub, background, notification, migration, and no-side-effect product-path items remain `NOT_RUN`.
