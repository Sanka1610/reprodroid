# ADR-0022: UI-R UI/UX reorganization boundary

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted; implemented locally, product acceptance pending
- Date: 2026-09-05
- Scope: Android UI architecture, navigation, presentation models, app metadata, grouping, tracking removal, local deletion, localization, accessibility, and future-phase connection points
- Contract: [UI-R implementation contract](../design/ui-r-contract.md)
- Source specification: `ReproDroid Phase 5 UI・UX公開前最終整備仕様.md` supplied by the developer on 2026-09-05

## Context

Phase 4.4 completed the local Android and Runner code for generic build and comparison, while Phase 4.5 scheduled release discovery and notifications has not started. The Android UI is concentrated in one large `ReproDroidApp.kt`, uses manual enum／nullable-ID／boolean navigation, exposes low-level evidence at the same level as user decisions, and cannot yet receive a stable notification-to-app-detail route. Existing screens provide useful functions, but their information hierarchy and lifecycle are not suitable as the public-facing foundation for 4.5–4.8.

The supplied UI／UX specification was titled Phase 5, but the developer selected a special non-numbered phase between 4.4 and 4.5. This avoids executing Phase 5 before Phase 4.5 and avoids silently moving the established 4.5–4.8 backend responsibilities.

## Decision

The special phase is named **UI-R — UI/UX Reorganization**. It runs after Phase 4.4 and before Phase 4.5. UI-R does not renumber the Phase 4 roadmap and does not make the supplied public-readiness specification itself an implementation-complete artifact.

UI-R adopts the following boundaries.

1. Keep the root Apps／Add／Settings destinations. On app detail routes, replace the root navigation bar with an app-local Information／Edit／Settings／Remove bottom action bar. Remove is an action and is never selected.
2. Replace manual screen flags with stable routes. App detail must be addressable by registered app ID so Phase 4.5 notification taps can open it without starting download, build, comparison, or installation.
3. Separate a concise decision-oriented app overview from bounded technical evidence screens. Existing raw comparison and trust semantics remain authoritative; presentation never converts `DIFFERENT` to `INCOMPARABLE`, carries trust to another release identity, or calls Gradle-file detection an Android-app or build-success result.
4. Store optional user-facing metadata separately from provider identity. Repository owner is not treated as application author. Unknown author, package, license, signer, or build support stays unknown.
5. Add user-managed groups with a one-to-many relationship: one app belongs to at most one group; one group can contain multiple apps. All and Ungrouped are virtual filters. Removing a group moves its apps to Ungrouped.
6. Editing a source URL in place is allowed only when provider and immutable provider repository ID still match. A different repository identity is registered separately and never inherits the old release／comparison／trust history.
7. App-detail removal means tracking removal, not physical deletion. Tracking removal preserves history and can optionally follow a successful system-confirmed uninstall. Complete Android deletion is a separate Settings data-management operation. Runner Job／artifact cleanup remains a separate Runner storage operation; UI-R does not pretend these independent owners form one atomic deletion.
8. Base string resources are English and `values-ja` is Japanese. Android locale selection chooses Japanese for Japanese locales and English for every currently unsupported locale. UI-R does not add an in-app language override.
9. Android 12 and later can use dynamic light／dark color. The persisted dynamic-color preference defaults to enabled; unsupported devices and disabled dynamic color use the custom light／dark scheme.
10. Phase-owned future functions remain in their existing phases. UI-R may add disabled, clearly labelled UI and stable routes, but no fake persistence, network call, Worker, result, or capability. A placeholder is not evidence that the future function is implemented.

## Future-phase mapping

| UI surface prepared by UI-R | Functional owner |
|---|---|
| scheduled checks, release channel, network／battery constraints, notification mute | Phase 4.5 |
| GitHub Stars import execution, using the provider queue／cooldown foundation | Phase 4.5 follow-up |
| Runner pairing, authentication, revocation, secure endpoint management | Phase 4.6 |
| Codeberg provider | Phase 4.7 |
| encrypted backup／restore and log export | Phase 4.8 |

## Deletion ownership

Tracking removal persists an inactive state and timestamp. It stops normal-list visibility, scheduled checks, notifications, and new ordinary work, but retains registration identity, source discovery, build configuration, release observations, comparison evidence, install attempts, notes, and audit-export eligibility. Re-registering the same primary repository must offer resuming the inactive record before creating another record.

Complete Android deletion is previewed and selected from Settings. It deletes the selected inactive local registration, cascading Room history only after explicit confirmation and deleting only app-private files that are still the expected owned resources and are not protected. Runner cleanup uses the existing preview／hold／execute boundary. Runner unavailability cannot be reported as successful Runner deletion, and it does not force Android deletion to masquerade as a distributed transaction.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Call this work Phase 5 and then return to 4.5 | Makes execution order and completion claims ambiguous |
| Pull 4.5–4.8 backend functions into UI-R | Breaks the accepted roadmap and mixes UI restructuring with scheduled work, authentication, provider, and backup contracts |
| Hide every future category | Loses the requested information architecture and forces later navigation redesign |
| Enable non-functional controls or save placeholder values | Misrepresents capability and creates migration debt |
| Multiple group memberships initially | Requires a cross-reference model and duplicate/filter semantics without an established use case |
| Replace a registered repository ID in place | Makes old evidence appear to belong to a different source |
| App-detail hard delete | Makes an ordinary navigation action destroy audit history |
| Combined Android-and-Runner delete | Cannot be atomic across offline and independently owned storage and would obscure partial failure |
| Use repository owner as app author | Provider ownership does not establish authorship |
| Japanese base resources | Unsupported locales would incorrectly fall back to Japanese instead of English |

## Consequences

UI-R requires an Android Room migration, UI state and navigation restructuring, localized resources, and broader unit／migration／Compose accessibility testing. It does not require a Runner schema or API change. The Android database version remains independent from the phase label; the expected next version is Room19 if no intervening migration lands.

The supplied public-readiness checklist remains a cross-phase target. UI-R completion is determined only by the UI-R contract and evidence ledger. Disabled future-phase surfaces prove route and layout readiness only.
