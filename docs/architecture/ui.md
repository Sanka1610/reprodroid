# UI architecture and navigation

- Status: Current public architecture
- Updated: 2026-09-14
- Source baseline: Phase 5.5+ locally integrated source

## Production root

Production has one UI call chain:

```text
MainActivity
  -> ReproDroidApp in ui/app/AppHost.kt
       -> typed route and back policy
       -> root pager, drawer, and top-level scaffold
       -> activity-result coordinator
       -> feature UiState collection and screen wiring
```

`MainActivity` owns the initial notification route and `onNewIntent` input. `AppHost` owns orchestration but does not own full feature-screen bodies. The former legacy roots have been removed.

## Screen ownership

| Package | Current owner |
|---|---|
| `ui/app` | production host and uninstall／notification／document activity-result coordination |
| `ui/navigation` | route parse／encode, back destinations, missing-record policy, notification route |
| `ui/apps` | registered-app list, search／filter, groups, tracking history |
| `ui/add` | repository URL, bounded analysis, options, confirmation |
| `ui/appdetail` | overview, source edit, app settings, technical evidence, removal confirmation |
| `ui/comparison` | raw comparison evidence |
| `ui/settings` | Settings accordion, update policy, data management, Android storage, logs, licenses |
| `ui/runner` | Runner connection／authentication, confirmations, Runner storage, toolchains |
| `ui/jobs` | Runner Job creation, RCE confirmation, scan review, artifact actions |
| `ui/shared` | reusable components, technical rows, labels, and formatters |
| `ui/state` | immutable feature states and owner-scoped message／result contracts |
| `ui/delegate` | feature state producers and mutation gates behind the ViewModel facade |

The screen map describes responsibility, not a security-authority transfer. Repository, Room, API, authorization, comparison, signer, and install-policy validation remains below the UI.

## Root information architecture

The three root pages are horizontally pageable and use the same typed route state as the navigation controls.

```text
Registered apps <-> Add app <-> Settings
       |
       +-- App information
       |     +-- Edit identity and source
       |     +-- App settings
       |     +-- Technical evidence
       |             +-- Comparison evidence
       +-- Tracking history

Settings
  +-- Appearance
  +-- App defaults
  +-- Update checks
  +-- Notifications
  +-- Service authentication
  +-- External tool integrations
  +-- Runner
  |     +-- Runner settings and authentication
  |     +-- Jobs
  |     +-- Managed build toolchains
  |     +-- Runner storage
  +-- Backup (currently unavailable)
  +-- Warnings and guidance
  +-- Data management
  |     +-- Android storage and cleanup
  |     +-- Tracking history and complete deletion
  |     +-- Audit export
  +-- Operational log export
  +-- About ReproDroid
        +-- ReproDroid license
        +-- Third-party notices
```

The root navigation capsule and pager are synchronized immediately. Add analysis／options／confirmation remain nested routes and cannot be paged away as root pages. The drawer provides secondary navigation without becoming a second route model.

## Encoded route contract

`ReproDroidRoute` is the single parse／encode boundary for saved navigation state and notification intents.

| Route family | Destination and fallback |
|---|---|
| `apps` | registered apps root |
| `apps/inactive` | tracking history; Back returns to Settings |
| `add/source` -> `add/analysis` -> `add/options` -> `add/confirm` | validated registration flow; missing preview returns to Add source |
| `settings` | Settings root |
| `settings/data`, `settings/data/storage`, `settings/data/inactive` | data management hierarchy |
| `settings/runner`, `settings/runner/storage`, `settings/toolchains`, `settings/jobs` | Runner hierarchy |
| `settings/authentication`, `settings/log-export`, `settings/licenses`, `settings/third-party-notices` | Settings detail routes |
| `settings/updates` | accepted compatibility input and immediately normalized to inline Settings |
| `settings/backup` | old compatibility alias parsed as `settings/log-export` |
| `apps/{registeredAppId}/information` | app overview; missing or non-canonical identity fails closed to Apps |
| `apps/{registeredAppId}/edit`, `/settings`, `/technical` | app detail tabs; inactive records cannot enter mutation screens |
| `comparisons/{comparisonRunId}` | raw comparison; Back uses the owning app when known, otherwise Apps |
| unknown or malformed input | Apps |

The parser also retains an unsupported GitHub Stars import route as a compatibility input. It is not exposed in release navigation and does not claim that import exists.

Release notifications carry `apps/{registeredAppId}/information` through `MainActivity.EXTRA_ROUTE`. Cold start and `onNewIntent` therefore enter the same parser. Route segments that identify records require canonical UUIDs.

## Back behavior

Back handling is ordered and deterministic:

1. close an open drawer;
2. return a nested route to its recorded parent;
3. return Add app or Settings root to Registered apps;
4. close Apps search／filter state;
5. show the application close confirmation.

App detail routes return to the app overview. Inactive app overview retains its recorded origin. Comparison returns to its owning app when that owner can be proven. A stale app route is normalized only after the catalog has loaded and proven the record missing.

## Presentation state ownership

`ManagedAppsViewModel` remains a lifecycle facade. It exposes eight immutable owner states produced by delegates; `JobViewModel` separately owns Jobs.

| Owner | State and actions | Concurrency boundary |
|---|---|---|
| Apps | catalog, active／inactive records, groups, global settings | app-ID action gate; group operations remain explicit |
| Registration | analysis preview, source-edit preview, registration result | cancellable generation and registration Jobs |
| App detail | build manifests, Runner Jobs, source／scan／sandbox warnings, availability | authoritative repository validation remains below UI |
| Release | release-check settings, per-app overrides, schedules, candidates | app-ID action gate; no automatic download／build／install |
| Storage | Android／Runner summaries, cleanup previews, audit／log export | single busy gate for storage mutations |
| Runner | connection status and saved Runner identities | repository-owned pairing and authorization state machine |
| Toolchain | catalog, install plan, progress, inventory, removal | coordinator-owned cancellation and identity checks |
| Deletion／export | complete-deletion preview／result and export result | app-ID gate and preview identity |
| Jobs | Job list, polling, RCE／scan acknowledgements, artifact actions | Job／artifact identity and visible polling lifecycle |

Messages and one-shot results carry an owner and monotonically increasing local event ID. Navigation results are consumed only when the current encoded route and relevant app／candidate／removal identity still match. Leaving a screen therefore prevents a late callback from mutating navigation for another record.

## Saved and transient state

- Encoded route, root form selections, removal target, search visibility, and identity-bound acknowledgements use saveable UI state where process recreation must preserve them.
- Room24 owns persistent settings, expansion preferences, release-check policy, self-registration bootstrap state, records, and history.
- Pairing invitation secrets, replacement payloads, active confirmation dialogs, and temporary license-load results are intentionally not placed in saved state.
- Missing, malformed, stale, unknown, or mismatched identity never defaults to success, trust, reproducibility, or install eligibility.

## Preserved safety boundaries

- A successful Runner Job means build completion, not reproducibility or safety.
- Source-scan and build-environment evidence are explanatory and cannot promote a raw mismatch.
- Build execution, source-scan continuation, comparison, trust, and installation remain separate explicit decisions.
- Installation uses Android's standard installer and its signer-lineage decision.
- Scheduled checks stop at metadata and notification.
- Release HTTPS requires manual pairing, root pinning, device credential authorization, and fail-closed transport.

The system-wide component and trust boundaries are in the [Architecture overview](overview.md). Current verification and release status are in [Current status](../status/current.md).
