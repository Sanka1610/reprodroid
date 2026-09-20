# UI architecture and navigation

## Production root

Production has one UI call chain:

```text
MainActivity
  -> ReproDroidApp in ui/app/AppHost.kt
       -> typed route and back policy
       -> single list entry and top-level scaffold
       -> activity-result coordinator
       -> feature UiState collection and screen wiring
```

`MainActivity` owns the initial notification route and `onNewIntent` input. `AppHost` owns orchestration but does not own full feature-screen bodies. The former legacy roots have been removed.

## Screen ownership

| Package | Current owner |
|---|---|
| `ui/app` | production host and uninstall／notification／document activity-result coordination |
| `ui/navigation` | route parse／encode, back destinations, missing-record policy, notification route |
| `ui/apps` | registered-app list, toolbar search, group management and display preference, tracking history |
| `ui/add` | repository URL, bounded analysis, options, confirmation |
| `ui/appdetail` | overview, acquisition, verification, installation content, source edit, app settings and read-only technical evidence |
| `ui/comparison` | raw comparison evidence |
| `ui/settings` | category navigation and separate appearance, update checks, notifications, acquisition, provider, data and about screens |
| `ui/runner` | Runner connection／authentication, confirmations, Runner storage, toolchains |
| `ui/jobs` | Runner Job creation, RCE confirmation, scan review, artifact actions |
| `ui/shared` | reusable components, technical rows, labels, and formatters |
| `ui/state` | immutable feature states and owner-scoped message／result contracts |
| `ui/delegate` | feature state producers and mutation gates behind the ViewModel facade |

The screen map describes responsibility; repository、Room、API、authorization、comparison、signer、install-policy validation remains below the UI.

## Root information architecture

The registered-app list is the root. Its FAB opens registration; the toolbar opens Settings. There is one route state, without a root pager, drawer or duplicate navigation capsule.

```text
Registered apps
  +-- Manage groups: grouped or flat list, creation, renaming and ordering
  +-- Add: URL -> app review and registration -> app information
  +-- App information
  |     +-- APK acquisition and installation
  |     +-- Verification: preparation, approval, progress and results
  |     +-- App settings / source edit
  |     +-- Technical evidence and history
  +-- Settings
        +-- Appearance and hints
        +-- Update checks
        +-- Notifications and permissions
        +-- Acquisition defaults and installer
        +-- Provider credentials
        +-- Verification environment / Runner
        +-- Data and storage: usage, cleanup, inactive apps, history export and diagnostic logs
        +-- About and licenses
```

Registration options and analysis details expand within the review page. Legacy option and confirmation routes render the same review page. Provider secrets stay local to the credential editor. Category navigation does not rewrite stored expansion preferences.

## Encoded route contract

`ReproDroidRoute` is the single parse／encode boundary for saved navigation state and notification intents.

| Route family | Destination and fallback |
|---|---|
| `apps` | registered apps root |
| `apps/groups` | independent group management; Back returns to Apps |
| `apps/inactive` | tracking history; Back returns to Settings |
| `add/source` -> `add/analysis` | two-step registration; legacy `/options` and `/confirm` open the same review content; missing preview offers return to source |
| `settings` | category list |
| `settings/appearance`, `settings/acquisition`, `settings/providers`, `settings/about` | category detail screens |
| `settings/data`, `settings/data/storage`, `settings/data/cleanup`, `settings/data/audit`, `settings/data/inactive`, `settings/data/runner-storage` | data management hierarchy |
| `settings/runner`, `settings/runner/storage`, `settings/toolchains`, `settings/jobs` | Runner hierarchy |
| `settings/authentication` | Runner authentication; Back returns to Runner settings |
| `settings/log-export` | Log export; Back returns to Data management |
| `settings/licenses`, `settings/third-party-notices` | License screens; Back returns to About |
| `settings/updates` | update schedule and release scope |
| `settings/notifications` | release notifications and OS permissions |
| `settings/backup` | old compatibility alias parsed as `settings/log-export` |
| `apps/{registeredAppId}/information` | app overview; missing or non-canonical identity fails closed to Apps |
| `apps/{registeredAppId}/registration-complete` | compatibility input, redirected to app information |
| `apps/{registeredAppId}/install` | compatibility input; opens acquisition or verification according to the saved mode |
| `apps/{registeredAppId}/acquisition`, `/verification` | independent acquisition and verification workflows |
| `apps/{registeredAppId}/edit`, `/settings`, `/technical` | app detail tabs; inactive records cannot enter mutation screens |
| `comparisons/{comparisonRunId}` | raw comparison; Back uses the owning app when known, otherwise Apps |
| unknown or malformed input | Apps |

The parser also retains an unsupported GitHub Stars import route as a compatibility input. It is not exposed in release navigation and does not claim that import exists.

Release notifications carry `apps/{registeredAppId}/information` through `MainActivity.EXTRA_ROUTE`. Cold start and `onNewIntent` therefore enter the same parser. Route segments that identify records require canonical UUIDs.

## Back behavior

Back handling is ordered and deterministic:

1. return a verification prerequisite screen to the recorded verification owner;
2. return a nested route to its parent;
3. return Add source or Settings to the app list;
4. close the list search state;
5. let Android handle normal exit without an application confirmation.

App detail routes return to the app overview. Inactive app overview retains its recorded origin. Comparison returns to its owning app when that owner can be proven. A stale app route is normalized only after the catalog has loaded and proven the record missing.

## Presentation state ownership

`ManagedAppsViewModel` remains a lifecycle facade. It exposes nine immutable owner states produced by delegates; `JobViewModel` separately owns Jobs.

| Owner | State and actions | Concurrency boundary |
|---|---|---|
| Apps | catalog, active／inactive records, groups, global settings | app-ID action gate; group operations remain explicit |
| Registration | analysis preview, source-edit preview, registration result | cancellable generation and registration Jobs |
| App detail | build manifests, Runner Jobs, source／scan／sandbox warnings, availability | authoritative repository validation remains below UI |
| Release | release-check settings, per-app overrides, schedules, release observations | app-ID action gate; no automatic download／build／install |
| Storage | Android／Runner summaries, cleanup previews, audit／log export | single busy gate for storage mutations |
| Runner | connection status and saved Runner identities | repository-owned pairing and authorization state machine |
| Toolchain | catalog, install plan, progress, inventory, removal | coordinator-owned cancellation and identity checks |
| Deletion／export | complete-deletion preview／result and export result | app-ID gate and preview identity |
| Jobs | Job list, polling, RCE／scan acknowledgements, artifact actions | Job／artifact identity and visible polling lifecycle |

Messages and one-shot results carry an owner and monotonically increasing local event ID. Navigation results are consumed only when the current encoded route and relevant app／release／removal identity still match. Leaving a screen therefore prevents a late callback from mutating navigation for another record.

## Saved and transient state

- Encoded route, form selections, removal target, search visibility and the verification return owner use saveable UI state. Install and source-scan acknowledgements are transient and tied to their exact current target.
- Room25 owns persistent settings, expansion preferences, release-check policy, self-registration bootstrap state, installer preferences and attempts, records, and history.
- Pairing invitation secrets, replacement payloads, active confirmation dialogs, and temporary license-load results are intentionally not placed in saved state.
- Missing, malformed, stale, unknown, or mismatched identity never defaults to success, trust, reproducibility, or install eligibility.

## Preserved safety boundaries

- A successful Runner Job means build completion, not reproducibility or safety.
- Source-scan and build-environment evidence are explanatory and cannot promote a raw mismatch.
- Build execution, source-scan continuation, comparison, trust, and installation remain separate explicit decisions.
- Installation uses the standard installer, or the existing eligible Shizuku/Sui path; signer and reproducibility gates remain below UI.
- Scheduled checks stop at metadata and notification.
- Release HTTPS requires manual pairing, root pinning, device credential authorization, and fail-closed transport.

system-wide componentとtrust boundaryは[Architecture overview](overview.md)、version/API対応は[Compatibility](../compatibility.md)、比較判定は[Reproducibility](../reproducibility.md)を参照してください。

## Refactored operation owners

`ManagedAppRepository` remains the public compatibility facade for existing callers. `AppComparisonCoordinator` owns comparison execution, its identity checks and transactional evidence updates. `AppInstallationCoordinator` owns installed-state refresh, install selection, callbacks and interrupted-attempt recovery. Their existing validation and transaction bodies were moved together.

Feature delegates now live in separate files. `ManagedAppsDelegates` constructs them with one shared `AppActionDelegate` and event store; the split does not create independent competing app-ID gates. The common ViewModel facade and some host-level subscriptions remain. Gradle modules, Room25 schema and Runner APIs are unchanged.

`AppActionPolicy` derives the primary action from candidate preparation, inspected version relation and active comparison state. Uninspected candidates lead to acquisition; verification remains reachable for same-version APKs. Metadata refresh no longer downloads a previously selected APK. Selection and explicit acquisition retain the existing download identity checks.

## Compact UI revision (2026-09-20)

The list search replaces the toolbar title and does not expose group controls. Display mode is a presentation-only Boolean in the private `app_list_display` preferences (`grouped`, default `true`); existing Room records and group membership are not migrated. Group management has its own route.

App information uses short label/value rows and trailing action buttons. A tracking installation action calls the existing installation coordinator only when the selected bytes are present, the version is installable, installer permission is available and no risk acknowledgement is outstanding. Signed local builds additionally require the matching comparison artifact. Other cases open acquisition or candidate preparation. The repository revalidates current state at execution. Opening technical details or refreshing release metadata does not initiate acquisition.

Data usage, cleanup selection and audit export are separate screens. Changing export scope clears the prior preview. Cleanup execution requires a selection and an explicit confirmation, then the existing manager revalidates protections, identity and expiration. Runner storage has a data-management route as well as its Runner-settings route so Back returns to the correct parent.

The outer Scaffold consumes its applied insets before child app bars; app-bar content height is 56 dp. Information dialogs hold supplementary explanations. Technical sections reveal long evidence values on demand and retain copy actions without truncating identifiers.
