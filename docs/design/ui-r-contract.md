# UI-R: UI/UX reorganization implementation contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented locally; product acceptance pending
- Date: 2026-09-05
- Position: after Phase 4.4 and before Phase 4.5
- Decision: [ADR-0022](../adr/0022-ui-r-ui-ux-reorganization.md)
- Input baseline: Android Room18 at `8d61180ab3c1f7b37f4d08132f8cad3f8f165e35`; Runner SQLite11 at `388236ae96e8ebe1ba2b5a4f4a62168089c9dca7`
- Local implementation: Android `7ca23dfe4c091828f79cf7ab3233a03cf58407db`／Room19 on `agent/codex-ui-r`; Runner unchanged

## 1. Purpose and authority

UI-R creates the public-facing Android UI foundation before scheduled release discovery is added. It reorganizes existing registration, release, build, comparison, trust, install, storage, toolchain, and Runner Job functions; adds the minimum local metadata, grouping, tracking lifecycle, and deletion logic needed by the UI; and reserves honest connection points for later phases.

The developer-supplied `ReproDroid Phase 5 UI・UX公開前最終整備仕様.md` is the public-readiness source specification. It does not override existing security, trust, provider, retention, or phase ownership contracts. Where the source specification lists a function scheduled for 4.5–4.8, UI-R implements only the disabled surface and route described here.

## 2. Invariants

1. UI labels and layout do not change domain truth. Raw comparison, trust, update relation, install state, source scan, sandbox evidence, and cleanup ownership remain distinct.
2. A Gradle candidate is not displayed as a detected Android application, buildable project, or APK-producing module.
3. `Reproducible` remains derived from the current comparison protocol and current immutable release／asset／commit identity. It is not inherited by a newly discovered release or a different repository.
4. Repository owner and application author are distinct. Unknown values are not inferred from filenames, URLs, or unrelated metadata.
5. Registration does not automatically download an APK, install toolchains, create Runner Jobs, acknowledge RCE, continue source scans, build, compare, or install.
6. Destructive actions require a preview or explicit impact summary followed by final confirmation. System package uninstall confirmation is not bypassed.
7. A disabled future-phase control has no persistence or side effect and includes text and semantics identifying its owning phase.
8. Android local deletion and Runner storage cleanup remain separate operations and report partial or unavailable state honestly.
9. Existing app icons are read only from verified app-private icon data. Missing icons use a generic fallback and are not synthesized as provider truth.
10. Window insets, scrolling, font scaling, long identifiers, touch targets, and TalkBack order are acceptance requirements, not optional polish.

## 3. Scope

### 3.1 Functional UI-R scope

- Theme layer: System／Light／Dark plus persisted Dynamic Color preference, with custom fallback.
- English base and Japanese resources for all user-facing UI-R strings and accessibility descriptions.
- Stable route-based navigation and state restoration.
- Existing root Apps／Add／Settings destinations.
- App-local Information／Edit／Settings／Remove bottom action bar.
- Responsive, scrollable app list with search, group filters, icon, concise status, Empty State, and Add action.
- User-managed groups with one optional group per app and multiple apps per group.
- Decision-oriented app overview and bounded technical-detail routes.
- Editing display-name override, author-display override, note, same-identity source URL, and group.
- Existing app preference and build-configuration UI reorganized under App Settings.
- Tracking removal, optional system uninstall followed by tracking removal, tracking resume, inactive-history list, Android complete-deletion preview, and confirmed local deletion.
- Existing repository-add flow reorganized into input, analysis, choices, confirmation, registration, and post-registration status without inventing unavailable analysis.
- Existing Storage, Toolchain, audit-export, and Runner Job functions integrated into the new information architecture.
- Loading, Empty, Error, Success, in-progress, and unavailable states.
- Long-value wrapping, bounded preview, copy action, and technical detail.
- Tests and implementation documentation.

### 3.2 Disabled UI and route only

| Surface | UI-R behavior | Functional phase |
|---|---|---|
| automatic update checks | Disabled setting with phase label; no stored schedule | 4.5 |
| check interval／daily time | Disabled controls; no WorkManager changes | 4.5 |
| release channel／prerelease | Disabled control distinct from APK filename variant | 4.5 |
| network／battery policy | Disabled controls; no constraints scheduled | 4.5 |
| update notification／mute | Disabled controls; no notification permission request | 4.5 |
| GitHub Stars import | Entry, route, explanation, Empty State; no API call | 4.5 follow-up |
| Runner pairing／authentication | Route and unavailable state; no credential | 4.6 |
| Codeberg | Disabled provider option; GitHub remains the only active provider | 4.7 |
| backup／restore | Route and unavailable state; audit export is not relabelled | 4.8 |
| log export | Disabled action | 4.8 |

### 3.3 Out of scope

- A Runner schema／API change.
- Background release discovery or notification posting.
- GitHub or Codeberg credentials.
- Codeberg network implementation.
- Encrypted backup／restore or log export.
- Automatic build／comparison／installation after registration.
- Multiple group membership.
- Rebinding one registered app to a different provider repository ID.
- Silent package uninstall, root／Shizuku, or signature bypass.
- Atomic combined Android／Runner deletion.
- Split APK, APKS, XAPK, APKM, or AAB support.

## 4. Information architecture and routes

Routes use stable IDs; display names and URLs are never route identity.

| Route | Purpose | Bottom surface |
|---|---|---|
| `apps` | active app list and group filters | root navigation |
| `apps/inactive` | tracking-removed history | root context |
| `add/source` | provider and URL input | root navigation |
| `add/analysis` | repository identity and bounded static discovery | add flow |
| `add/options` | management and installation choices | add flow |
| `add/confirm` | registration summary | add flow |
| `apps/{registeredAppId}/information` | app overview | app action bar |
| `apps/{registeredAppId}/edit` | local metadata and tracking source | app action bar |
| `apps/{registeredAppId}/settings` | app behavior and build configuration | app action bar |
| `apps/{registeredAppId}/technical` | low-level evidence index | app action bar or no bar |
| `comparisons/{comparisonRunId}` | bounded comparison evidence | back navigation |
| `settings` | global settings index | root navigation |
| `settings/data` | Android data management and audit export | back navigation |
| `settings/data/inactive` | deletion selection and preview | back navigation |
| `settings/runner` | Runner state／future pairing | back navigation |
| `settings/runner/storage` | Runner cleanup preview／execute | back navigation |
| `settings/toolchains` | managed toolchains | back navigation |
| `settings/jobs` | Runner Jobs | back navigation |
| `settings/updates` | disabled until 4.5 | back navigation |
| `settings/authentication` | disabled until 4.6 | back navigation |
| `settings/backup` | disabled until 4.8 | back navigation |
| `import/github-stars` | disabled until 4.5 follow-up | add flow |

The implementation may use a typed route library or an internal typed navigator, but it must support cold-start intent mapping to `apps/{registeredAppId}/information`, back-stack restoration, invalid／inactive app handling, and tests without depending on composable-local booleans.

## 5. App information selection

The overview contains only information required for the next user decision.

1. Identity: verified icon when available, resolved display name, optional author display, repository owner, package, installed and latest known version.
2. Current state: tracking state, source／release check state, update relation, current build／comparison progress, last relevant check time.
3. Official vs Local: trust headline and protocol-appropriate Official-vs-A, Official-vs-B, and A-vs-B summary when available.
4. Build summary: selected configuration revision, current／latest Job state, artifact identity, and bounded sandbox／toolchain summary.
5. Source: provider, owner／repository, canonical URL, branch, full commit, and discovery confidence.
6. Explicit actions and detail links.

Repository IDs, observation hashes, configuration hashes, raw APK hashes, signer fingerprints, dependency records, scan findings, complete release history, per-entry APK evidence, semantic differences, and sandbox internals belong to bounded technical routes or expandable details. Hashes and URLs offer copy actions; horizontal scrolling is not the default presentation.

## 6. Local model contract

Exact Kotlin names may change during implementation, but persisted semantics may not.

### 6.1 Registered app additions

| Field | Type | Meaning |
|---|---|---|
| `displayNameOverride` | nullable string | User-visible name override; null follows the provider/default name |
| `authorDisplayOverride` | nullable string | User-entered display author; not provider owner evidence |
| `note` | non-null string | Local free-form note, excluded from build／comparison decisions |
| `groupId` | nullable UUID | At most one user group |
| `trackingState` | enum string | `ACTIVE` or `INACTIVE` |
| `trackingStoppedAt` | nullable instant | Time tracking was removed |
| `dynamic source revision` | revision／timestamp as needed | Optimistic concurrency for edit confirmation |

The existing `displayName` remains the provider/default value or is renamed through a migration-safe equivalent. UI resolves `displayNameOverride ?: providerDisplayName`. “Reset to default” stores null.

### 6.2 Groups

`AppGroupEntity` uses a canonical local UUID, bounded normalized display name, unique comparison policy, integer or long sort order, and timestamps. `All` and `Ungrouped` are virtual and cannot collide with persisted group IDs. Group deletion and app reassignment occur in one Room transaction.

### 6.3 Theme

Global settings add a non-null `dynamicColorEnabled`, default true. Theme mode stays `SYSTEM`／`LIGHT`／`DARK`. Android 12+ uses dynamic light／dark only when enabled; every other case uses the custom scheme.

### 6.4 Tracking lifecycle

Inactive records remain queryable for history and audit export but are excluded explicitly from active-list and future scheduled-check queries. Existing repository uniqueness does not silently create a second primary registration. Registration detects an inactive same-identity record and offers resume. Resume resets the tracking state and timestamp but does not claim a new check or trust result.

## 7. Source edit contract

Source URL changes use parse → remote preview → immutable identity comparison → explicit confirmation → transactional update.

- No save occurs while input is unvalidated.
- Provider and instance must be supported and unchanged.
- Provider repository ID must equal the stored verified ID.
- The preview displays canonical URL, owner／repository, default branch, and whether the identity matches.
- A match may update canonical locators and add a new source discovery observation without rewriting old release or comparison evidence.
- A mismatch offers separate registration and cancel; it cannot overwrite the current binding.
- Concurrent binding or edit changes fail stale and require reload.
- Provider failure, rate limiting, authentication need, unsupported provider, and malformed URL remain distinct errors where the backend exposes that distinction.

## 8. Registration flow

The active UI-R GitHub flow is:

1. Source input.
2. Repository identity resolution and bounded static tree discovery.
3. Management／installation choices and any supported static build-root choice.
4. Confirmation of facts actually known.
5. Registration transaction.
6. Navigate to app information and show pending next actions.

The analysis may display provider, repository owner/name, default branch, full commit, discovery state, Gradle candidates, exclusion counts, and limits. It must not display package, icon, author, license, Android module, variant, artifact, build support, or comparison support unless established by existing authoritative evidence.

An indeterminate indicator is used while no meaningful numeric progress exists. Existing Job progress may use determinate progress. Registration completion does not start a Runner operation.

## 9. Removal, uninstall, and deletion

### 9.1 Tracking removal

The app-local Remove action first offers tracking removal, uninstall then tracking removal when a package is known, or cancel. A second confirmation states the package, installed version, other records referring to the package, and preserved history. Tracking is changed only after the final confirmation. Uninstall mode launches the standard Android confirmation and rechecks PackageManager on return; cancellation／failure preserves active tracking.

### 9.2 Complete Android deletion

Only inactive records appear in the default complete-deletion selector. Settings creates a bounded local preview containing record counts, owned file bytes, protected resources, and audit-export guidance. Execution revalidates the preview and current ownership before a Room transaction and confined file deletion. Partial or reconciliation-required outcomes remain visible and are not reported as complete.

### 9.3 Runner deletion

Runner Job／artifact deletion remains under Runner Storage and uses capability, owner, retention hold, preview expiry, selection, and result checks. Android complete deletion does not claim Runner cleanup. A UI may guide the user through both independent steps but cannot combine their result into an atomic success.

## 10. Global and app settings

Implemented settings remain enabled. App-level inherited fields explicitly show “Use global setting” versus “Override for this app” and the current effective value. Removing an override stores inheritance, not a copied global value.

Future categories are visible as disabled sections or routes with their owning phase. Multiple global accordion sections can be expanded simultaneously. Expansion is UI state and is not treated as a functional setting. Audit export, storage cleanup, toolchain management, and Runner Jobs retain their actual names and are not relabelled as backup or authentication.

## 11. Localization and accessibility

- `values/strings.xml` is English fallback; `values-ja/strings.xml` is Japanese.
- All user-facing strings, state descriptions, content descriptions, and placeholder phase labels use resources.
- Package names, URLs, hashes, IDs, ABI, artifact names, branch／tag, and stable error codes stay verbatim.
- Known error codes receive localized explanations; unknown raw messages are bounded and placed in technical details where possible.
- Icon-only actions have a content description, Material components meet minimum touch targets, state is not color-only, traversal order follows visual order, and disabled future items announce both unavailability and phase.
- Font scale acceptance includes the Android accessibility presets used by the test environment; no essential action is clipped or reachable only by horizontal scrolling.

## 12. Architecture and testability

The legacy root in `ReproDroidApp.kt` is replaced by a UI-R root, while existing technical／settings／storage components are reused where their domain behavior remains authoritative. Domain models and repositories do not depend on composables. Theme and route parsing are separate packages. Screen responsibilities are separate top-level composables with explicit inputs and callbacks; UI-R does not require a behavior-neutral physical file split that would enlarge this migration without acceptance value. Screen state is represented explicitly rather than inferred from empty lists and nullable values alone. UI mapping functions that affect domain presentation remain independent from persistence and backend authority.

The implementation keeps these boundaries even where related screen composables are colocated:

- theme and design tokens;
- navigation and route parsing;
- shared components and status presentation;
- apps list／groups;
- app information／technical details;
- app edit and registration flow;
- app／global settings and data management;
- localization-compatible error presentation.

Physical screen-file extraction may follow after product-path acceptance if file size impairs maintenance. It must not be coupled to Phase 4.5 behavior or used to postpone UI-R functional fixes.

## 13. Acceptance ledger

`NOT_RUN` means no evidence exists yet. A disabled placeholder can pass only a `ROUTE_ONLY` acceptance item and cannot satisfy the future functional phase.

| ID | Condition | Required evidence | Current evidence (2026-09-06) |
|---|---|---|---|
| UIR-01 | clean Room18 database upgrades | Room19 migration retains all prior fields, trust, history, settings, and relationships | NOT_RUN |
| UIR-02 | failed／interrupted migration | migration gate preserves recoverable prior data; no destructive fallback | NOT_RUN |
| UIR-03 | System／Light／Dark × dynamic on／off | correct scheme selection; Android <12 fallback; persistence after restart | PARTIAL: implementation／assemble only; device matrix NOT_RUN |
| UIR-04 | Japanese／English／unsupported locale | Japanese for `ja`, English for base and all unsupported locales; no hard-coded user strings in UI-R surfaces | PARTIAL: English／Japanese 416-key parity and lint PASS; device locale paths NOT_RUN |
| UIR-05 | root and app-local navigation | root destinations preserved; detail action bar selection／remove semantics; back and restoration correct | PARTIAL: typed-route JVM tests PASS; Compose／restoration product path NOT_RUN |
| UIR-06 | cold-start app-detail intent | valid active／inactive／missing IDs handled without side effects | PARTIAL: route parser tests PASS; intent product path NOT_RUN |
| UIR-07 | empty apps／empty search／empty group | informative Empty State and appropriate action; no blank screen | NOT_RUN |
| UIR-08 | multiple apps in one group | ordering, filtering, persistence, restart | NOT_RUN |
| UIR-09 | group rename／reorder／delete | transactional update; delete moves members to Ungrouped | NOT_RUN |
| UIR-10 | reset display name／author／note | override semantics, bounds, persistence, build／comparison unaffected | NOT_RUN |
| UIR-11 | same repository source URL edit | preview and immutable ID match required; no old evidence rewrite | NOT_RUN |
| UIR-12 | different repository source URL | in-place save rejected; separate registration route offered | NOT_RUN |
| UIR-13 | app overview for all domain states | unknown／active／success／Different／Incomparable／error remain distinct and not color-only | NOT_RUN |
| UIR-14 | long URL／package／asset／hash／error | no horizontal layout failure; copy and bounded detail work | NOT_RUN |
| UIR-15 | registration success／failure／cancel | staged route state, stale preview rejection, no automatic backend side effect | NOT_RUN |
| UIR-16 | tracking removal | history retained, active work stopped, inactive list and audit visibility retained | NOT_RUN |
| UIR-17 | uninstall cancel／failure／success | system confirmation used; tracking changes only after confirmed absence | NOT_RUN |
| UIR-18 | re-register inactive same identity | resume offered; duplicate primary record not silently created | NOT_RUN |
| UIR-19 | complete Android deletion preview／execute | inactive selection, counts／bytes, revalidation, confined files, partial failure | NOT_RUN |
| UIR-20 | Runner offline／hold／partial cleanup | Android and Runner results remain separate and truthful | NOT_RUN |
| UIR-21 | future phase surfaces | disabled, labelled with phase, semantic unavailable, zero persistence／network／Worker side effects | ROUTE_ONLY: disabled controls／phase labels compile; future functions remain unimplemented |
| UIR-22 | icon available／missing／invalid | verified icon or neutral fallback; list and detail remain stable | NOT_RUN |
| UIR-23 | large font／TalkBack／Insets | actions reachable, traversal correct, touch targets valid, fixed and scroll areas do not overlap | NOT_RUN |
| UIR-24 | light／dark screenshots on representative phone sizes | no fixed-width dependency or contrast／clipping regression | NOT_RUN |
| UIR-25 | existing JVM／lint／assemble regression | debug and release suites pass without weakening tests | PASS: debug 127／release 127 tests, lint, debug／release assemble; 0 failures／errors |
| UIR-26 | Android 16 product-path smoke | add → detail → edit → group → settings → remove／resume and local deletion preview observed | NOT_RUN |

## 14. Implementation sequence

1. Record contract, ADR, roadmap insertion, and baseline.
2. Split theme, resources, routes, and shared components without changing domain behavior.
3. Add Room19 local metadata／group／tracking lifecycle schema and migration tests.
4. Implement app list, group management, and Empty States.
5. Implement information hierarchy and technical-detail routes.
6. Implement edit and same-identity source preview／save.
7. Reorganize app settings and inherited-value presentation.
8. Implement tracking removal, uninstall result handling, inactive history, and Android deletion preview／execute.
9. Rebuild the add flow and future-phase placeholders.
10. Integrate existing Storage／Toolchain／Runner Job surfaces.
11. Complete accessibility, locale, font-scale, inset, and long-value verification.
12. Run full automated regression and Android 16 product smoke, record evidence, and update every ledger row.

UI-R is complete only when every in-scope ledger row is supported by authoritative evidence. Future-phase functions remain explicitly incomplete even when their `ROUTE_ONLY` surface passes.
