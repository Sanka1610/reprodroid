# Operations and recovery

- Status: Current development guide
- Updated: 2026-09-14

This guide describes explicit user operations. ReproDroid does not turn source acquisition, build success, scan output, comparison, signer identity, update relation, trust, and installability into one safety verdict.

## Release tracking and official APK selection

Open an app's Release tracking section and use Check for updates for a manual metadata refresh. Global and per-app scheduled checks support a 1-to-24-hour interval or a specified daily time, with explicit network, battery, charging, notification, and per-app override policies.

A scheduled check may fetch bounded public release metadata, persist a candidate, and post a notification. It never starts APK download, toolchain installation, a Runner Job, source scan, build, comparison, trust change, or installation. Turning off Release-check notifications suppresses posting only; it does not erase candidates or history or stop metadata evaluation.

When one release has multiple APK assets, choose the exact asset explicitly. ReproDroid records provider identity and selection conditions, downloads with size and redirect bounds, calculates SHA-256, and inspects package, version, and signer data. A download or identity inspection is not a reproducibility result.

## Build A, Build B, and comparison

Verification mode uses these separate gates:

1. Save a validated build configuration. Saving does not run Gradle or create a Job.
2. Create the first Runner Job for a resolved full source commit and fixed recipe.
3. Review the exact Job, commit, sandbox identity, and arbitrary-code-execution warning before approving Build A.
4. If the pre-build source scan reports configured indicators, review and acknowledge that Build's digest-bound findings separately.
5. Repeat the process for Build B. It resolves and builds independently from the same release and recipe.
6. Compare Official-vs-A, Official-vs-B, and A-vs-B as three distinct raw axes.
7. Open explanatory DEX, native library, Manifest, resource, dependency, determinism, and sandbox evidence without allowing it to overwrite the raw result.

`Reproducible` requires all defined raw axes for the same release observation to match. `SUCCEEDED`, `Buildable`, no scan findings, or an explanatory semantic match is insufficient by itself. Generic Gradle builds execute untrusted code; the Docker profile is a containment measure, not third-party attestation or proof that source is safe.

## Installation handoff

ReproDroid passes only an eligible signed artifact to Android's standard `PackageInstaller`. The system owns the final confirmation and signer-lineage decision. ReproDroid does not silently install, uninstall another package, bypass signature checks, or manage a signing key for locally built apps.

Official and local artifacts can have different signers. A local build normally cannot update an installed official app unless Android accepts the signing lineage. Cancel, platform rejection, failure, and success remain distinct recorded outcomes.

## Recovery behavior

| Situation | Current behavior and operator action |
|---|---|
| Android process or device restarts | Room24 restores records, settings, histories, and durable operation state. The encoded route is restored only when its referenced identity remains valid. |
| Download was interrupted | Startup reconciliation recovers or marks the interrupted transfer without treating a partial file as a completed APK. Retry explicitly from the app. |
| Installer result was interrupted | Orphaned install attempts are reconciled against package state; an unknown callback is not converted to success. |
| Runner stopped during a Job | The Runner persists Jobs in SQLite12 and does not claim an interrupted build succeeded. Restart the Runner and use only the explicit retry／reconcile action offered for that state. |
| Runner identity, pin, or credential no longer matches | The connection fails closed. Verify the exact endpoint and identity, then use the explicit repair／replacement or re-pair flow. Do not downgrade to unauthenticated HTTP. |
| Pairing credential must be removed | Revoke the principal on the Runner when possible, then explicitly remove the local credential. Local deletion alone does not prove server-side revocation. |
| Provider rate or bounded-discovery limit is reached | Existing records remain. Wait for provider reset or reduce the requested scope, then retry explicitly; do not infer a complete result from a truncated response. |
| Storage limit blocks work | Review Android and Runner storage summaries, preview a bounded cleanup, verify protected items, and confirm manually. |
| Tracking should stop | Stop tracking independently of Android uninstall. Historical release, build, comparison, install, and audit records remain until a separate complete-deletion flow succeeds. |

Backup／restore and device-to-device migration are not available. Audit export and log export are diagnostic records, not restorable backups.

## Cleanup and exports

Android and Runner cleanup are preview-first and protect current, pending-review, retained, or otherwise referenced items. A preview can expire or become stale; execution revalidates it. Partial failure is reported as partial and is not silently promoted to success.

Android audit export contains bounded structured evidence. Android log export contains only ReproDroid operational events, not OS-wide logcat, other applications' logs, Runner build logs, or a database backup. Runner log export is a Runner-local operation documented in the [Runner README](https://github.com/Sanka1610/reprodroid-runner#local-log-export). Job output can contain paths or values printed by untrusted build scripts, so review it before any external sharing.

## Artifact and checksum verification

Do not install an APK based only on a filename. For a future published release:

1. obtain the release notes, APK, SBOM, provenance, and checksum manifest from the same release;
2. verify the checksum manifest with a trusted local SHA-256 tool;
3. verify the APK certificate with Android SDK `apksigner`;
4. compare the certificate SHA-256 fingerprint with the public [release signing policy](../../release/README.md);
5. confirm package name `com.sanka1610.reprodroid`, version name／code, requested permissions, and source commit;
6. reject missing, mismatched, substituted, or ambiguously associated files.

Example checksum commands, after reviewing the expected manifest filename:

```bash
sha256sum -c SHA256SUMS
sha256sum reprodroid.apk
```

The repository currently does not claim that a Phase 5 checksum manifest or signed candidate exists. Debug APK hashes and historical Phase 4 artifact hashes are not valid substitutes for the next release.

## Known limitations

The current limitations and verification gaps are maintained in [Current status](../status/current.md). In particular, private repositories, GitLab, arbitrary Forgejo／Gitea, split packages, silent install, backup／restore, hard generic-build disk／inode quota, fixed egress allowlisting, analytics, automatic crash upload, and public multi-user Runner service are outside the current product scope.
