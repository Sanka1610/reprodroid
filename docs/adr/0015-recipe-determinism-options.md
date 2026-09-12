# ADR-0015: recipe determinism options

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted; implemented and verified
- Date: 2026-08-28
- Scope: Phase 3C

## Context

Phase 3A exposes a redacted Build Environment Manifest and Phase 3B fixes and audits dependency-pinning policy. Neither phase controls timestamp input, Gradle Build Cache use, or the process locale. The current Runner inherits host `LANG` / `LC_ALL`, does not pass `SOURCE_DATE_EPOCH`, and only fixes `--no-daemon`, plain console output, and the Phase 3B offline option.

These controls are execution conditions and audit evidence. They do not prove that every build tool consumes `SOURCE_DATE_EPOCH`, that every cache is disabled, or that output bytes will match. Protocol v2 raw Official-vs-A, Official-vs-B, and A-vs-B outcomes remain authoritative.

## Decision

### 1. Add an optional typed determinism block to fixed recipes

Runner `BuildRecipe` gains an optional `determinism` block with:

- `source_date_epoch`: nullable non-negative Unix seconds literal represented as a signed 64-bit integer.
- `no_build_cache`: boolean, default `false`.
- `fixed_locale`: nullable enum. Phase 3C permits only canonical `C.UTF-8`.

The block is owned by the allowlisted Runner recipe. Job create, confirm, retry, and Android request models cannot select or override it. Omission preserves Phase 3B behavior. Existing MicroG-RE recipes remain explicitly unconfigured in committed production code.

Runner validates every recipe when the registry is constructed. A negative epoch, unsupported locale, or a fixed task list containing `--build-cache` / `--no-build-cache` is a configuration defect and prevents the recipe registry from becoming usable. A host-provided `LANG` / `LC_ALL` value is not a collision.

### 2. Apply controls only to Gradle and its children

Git clone, checkout, ref verification, SDK verification, and Wrapper verification retain the existing restricted environment. Immediately before `GradleWrapperMain`, Runner derives a Gradle environment:

- configured epoch becomes the canonical decimal `SOURCE_DATE_EPOCH` value;
- configured locale replaces both `LANG` and `LC_ALL`;
- `no_build_cache = true` adds one `--no-build-cache` option before fixed tasks.

Runner does not infer epoch from its clock, the Job timestamp, Git metadata, filesystem mtimes, or Gradle output. It does not inherit `SOURCE_DATE_EPOCH` from the host.

Before Gradle starts, configured `C.UTF-8` is checked as an available UTF-8 locale. Failure is terminal `DETERMINISM_LOCALE_UNAVAILABLE`.

Phase 3C does not inject `TZ`, use `faketime`, rewrite source mtimes or ZIP timestamps, disable configuration/dependency caches, rewrite build scripts, or introduce scanner/container behavior.

### 3. Persist effective policy before execution

Runner SQLite moves from v5 to v6 and stores nullable epoch, non-null build-cache boolean, and nullable locale on each real Job. Existing rows are backfilled as unconfigured. Values are written with the other effective recipe fields before source resolution and are restored from SQLite after restart; they are not re-derived from the current registry.

Failure to persist effective values is fail closed and does not start the real-build workflow.

### 4. Extend additive API v1 effective-build evidence

`GET /v1/jobs/{jobId}` keeps API v1 and adds `effectiveBuild.determinism`:

```json
{
  "sourceDateEpoch": 1777393787,
  "noBuildCache": true,
  "fixedLocale": "C.UTF-8"
}
```

New Runner responses explicitly include the object whenever `effectiveBuild` exists. Legacy responses without it mean unconfigured. Unknown locale values or malformed fields are not rounded to unconfigured. Request surfaces remain unchanged.

### 5. Version private and public Manifest schemas without invalidating history

New successful builds write private Build Environment Manifest schema v3 with the effective determinism object. The public projection becomes schema v2 and exposes only epoch, boolean build-cache policy, and canonical locale in addition to existing public v1 fields.

Runner continues to read historical private schema v2 and project it as public schema v1. A private v2 record containing v3-only fields, a private v3 record missing determinism, or any mismatch between SQLite effective values and the private Manifest is `BUILD_MANIFEST_INVALID`.

Manifest write or audit persistence failure remains terminal. No general environment values, paths, host locale list, or task details become public.

### 6. Persist Android Job and Manifest evidence in Room v12

Android Room moves from v11 to v12. It adds effective determinism columns to `jobs` and observed determinism columns to `build_environment_manifests`. Existing rows are backfilled as unconfigured while all prior Job, Manifest, dependency, pinning, comparison, raw outcome, trust, and install evidence is preserved.

Android accepts legacy public Manifest v1 without determinism and public v2 with a strictly validated determinism object. Successful v2 retrieval replaces header and dependency rows in one transaction. Decode, version, integrity, or consistency failure retains any prior valid record and is a session warning.

Job evidence is used before RCE confirmation and in Job detail. Manifest evidence is used after successful publication. Build A / B detail may display both values. Determinism does not become a target-identity gate or comparison input.

### 7. Bound UI claims

UI may state the exact epoch, `Gradle build cache: Disabled by Runner`, and `Process locale: C.UTF-8`. It must not state that all timestamps or caches are fixed, the host environment is normalized, or reproducibility is guaranteed.

Build A / B mismatch is explanatory only. It does not change raw outcomes, comparison status, trust, update relation, signer relation, install source, or PackageInstaller policy.

### 8. Verify fixtures and the existing product path separately

Positive and negative option behavior is covered by Runner fixtures. The product path remains MicroG-RE `6.1.4`; no new allowlist profile is added. During E2E only, its release recipe is temporarily configured with:

- `source_date_epoch = 1777393787` (`2026-04-28T16:29:47Z`)
- `no_build_cache = true`
- `fixed_locale = C.UTF-8`

The committed recipe is restored to unconfigured after E2E. If injected controls change raw bytes, the raw result remains `Different` and Phase 3C product acceptance is not reported green.

## Alternatives considered

### Expose only Job API fields and leave Manifest schemas unchanged

This cannot satisfy the requirement to record the actual successful-build audit evidence and makes Job policy indistinguishable from Manifest publication. Rejected.

### Keep Room v11 by encoding values in existing text columns

This changes the meaning of existing columns and prevents strict migration and query validation. Rejected.

### Permanently enable controls for MicroG-RE

This may change the already validated comparison profile and artifact bytes. The recipe remains unchanged outside the temporary E2E run.

### Allow arbitrary locale strings

Host availability and semantics would vary. Phase 3C starts with the verified canonical `C.UTF-8` value and an availability preflight.

### Apply locale and epoch to every subprocess

This would alter Git and Wrapper behavior beyond Gradle build determinism. Rejected for Phase 3C.

## Consequences

- Runner SQLite becomes v6; private Manifest becomes v3 for new Jobs; public Manifest becomes v2 for new Jobs.
- Historical private v2/public v1 Manifest access remains supported.
- Android Room becomes v12 and preserves all v11 evidence.
- API v1 remains additive and request models remain unchanged.
- Existing committed MicroG-RE recipe behavior remains unchanged.
- Protocol v2 and every raw comparison/trust/install boundary remain unchanged.

## Verification

The implementation test names, product-path acceptance criteria, raw automated results, migration observations, E2E, cold-start, and cleanup evidence are retained in private implementation records. The public verification state is recorded in [Current status](../status/current.md).
