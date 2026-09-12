# Phase 4.3 trusted toolchain contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: implementation contract
- Accepted: 2026-09-05
- ADR: [ADR-0020](../adr/0020-phase-4-trusted-toolchain-installation.md)
- Capability: `toolchain-install@1`
- Schema: Runner SQLite10 / Android Room17
- Verification status: [Current status](../status/current.md)

## Scope and authority

Android owns exact requirements, plan/license/progress/cancel UI and last-observed references. Runner owns catalog, resolution, reservation, bytes, verification, filesystem, inventory, restart reconciliation and removal. Androidのlocal row、共有`ANDROID_HOME`、`JAVA_HOME`、過去の`setup-env.sh --licenses`はinstalled／accepted truthにならない。

初期platformはWSL2を含むLinux x86_64だけ。storeは`REPRODROID_STATE_DIR/toolchains`、owner stagingは`REPRODROID_STATE_DIR/toolchain-staging/{installationId}`。次を対象外とする。

- WSL、Docker、Runner bootstrap JDK、Android Studio、emulator、system image、platform-tools
- Node、Flutter、任意repository URL／script、private credential
- Play Store／F-Droid、Google Play services、診断共有／自動送信
- automatic removal、generic build、comparison

## Catalog seed

bundled `reprodroid-toolchains-v1`はartifactごとにcomponent、exact version、platform、HTTPS URL、archive type、download bytes、expanded upper bound、SHA-256、install subdirectory、license ID、追加signature policyを持つ。2026-09-05に一次配布物を取得してSHA-256を計算し、Gradleは公式checksumと一致、Android archiveはGoogle repository XMLのsize／SHA-1とも独立照合、Temurinは公開SHA-256とOpenPGP signatureを照合した。

基礎seedはTemurin 21.0.12+1、Gradle 8.14.3、Android command-line tools 15859902、platform 36 r02、build-tools 36.0.0。4.4 targetからGradle 9.6.1／9.7.1、platform 37 r02、build-tools 37.0.0も追加する。具体的な製品受入targetと生の結果は非公開のtarget台帳で管理する。

## API resources

全mutationは`X-ReproDroid-Contract: toolchain-install@1`とUUID `Idempotency-Key`を要求する。JSONはunknown／duplicate field、非UTF-8、過大bodyをfail closedにする。

| Method | Path | Meaning |
|---|---|---|
| GET | `/v2/toolchains/catalog` | bundled snapshotとcatalog digest |
| GET | `/v2/toolchains/licenses/{licenseId}` | 同意対象本文とdigest |
| POST | `/v2/toolchains/plans:resolve` | exact requirementsのside-effect-free plan |
| GET | `/v2/toolchains/inventory` | Runner authoritative inventory |
| POST | `/v2/toolchains/installations` | digest-bound plan／同意からdurable install作成 |
| GET | `/v2/toolchains/installations/{id}` | state、item、bytes、public reason |
| POST | `/v2/toolchains/installations/{id}:cancel` | cancel request。即時成功を意味しない |
| POST | `/v2/toolchains/removals:preview` | exact inventory itemの期限付きpreview |
| POST | `/v2/toolchains/removals:execute` | preview再照合後のmanual removal |

plan SHAはcatalog SHA、platform、正規化requirements、解決artifact IDsからJCSで生成する。create時に再解決し、digestが違えば409 `TOOLCHAIN_PLAN_STALE`。現在の`(runnerId, principalId, licenseId, licenseTextSha256)`同意がないlicenseだけを要求し、required license集合とacceptance集合は完全一致させ、過不足・false・stale text digestを拒否する。

## States and restart

foundation operationは`RESERVED / APPLYING / COMPLETED / REJECTED / RECONCILIATION_REQUIRED`を維持する。detail stateは次の順である。

```text
PLANNED -> RESERVING -> DOWNLOADING -> VERIFYING_ARCHIVE -> EXTRACTING
        -> VERIFYING_CONTENT -> PUBLISHING -> INSTALLED
```

必要同意がない要求はresourceを作らず`LICENSE_ACCEPTANCE_REQUIRED`。処理中cancelは`CANCEL_REQUESTED`を観測し、I/O停止とowner staging／reservation処理後に`CANCELLED`。検証、network、容量エラーは`FAILED`。final publishの存在、atomic move不能、manifest／inventory不一致は`RECONCILIATION_REQUIRED`で止める。

restartは非terminal installationを同じIDで再queueし、owner stagingを削除して最初の未installed itemから再取得する。Range resumeは初版で行わない。既に`VERIFIED`のartifactは再取得しない。final directoryが存在するのにinventoryがない場合は上書き・削除しない。

## Archive and publish limits

- HTTPS only。初期allow hostは`services.gradle.org`、`downloads.gradle.org`、`github.com`、`release-assets.githubusercontent.com`、`dl.google.com`。
- redirectごとにscheme、userinfo、port、hostを検査する。
- Content-Lengthがある場合はcatalog sizeと一致させ、streamも同じ上限で数える。
- ZIP／tar.gzはJava側で展開し、absolute／control character／`..`、symlink parent traversal、hardlink／deviceを拒否する。安全なrelative symlinkだけroot内targetへ許可する。
- expanded bytesとentry countはartifact別上限。metadata markerを実行せず検査する。
- content manifestはrelative path、type、size、file SHA-256またはlink targetをcanonical orderで保存する。
- final parentとstagingは同一state filesystem。`ATOMIC_MOVE`非対応時はcopy fallbackせず停止する。

OpenPGP検証はhost bootstrapの`gpgv`を使う。これはdownloadしたprogramではないが、Runner配布前提に追加される。keyringはbundled bytes、fingerprintはcatalog固定。`gpgv`欠落やfingerprint不一致はfail closed。

## Android persistence

Room17 `toolchain_installation_references`は次だけを保存する。

- `runnerId`, `installationId`, `operationId`
- optional `registeredAppId`, `buildSettingsRevision`
- `planSha256`, `catalogSha256`, last observed state/time

path、accepted license truth、archive bytes、inventory installed truthは保存しない。restart後はactive referenceを同じRunnerへ問い合わせ、別runnerIdの結果を混ぜない。

## Negative-test ledger

| ID | Expected result |
|---|---|
| T43-01 | unknown component/version/platformはplanを作らない |
| T43-02 | unknown/duplicate JSON fieldはDB副作用前に400 |
| T43-03 | stale catalog/plan digestは409 |
| T43-04 | license欠落、false、本文digest変更、余分な同意はresource作成前に拒否 |
| T43-05 | download host／redirect逸脱、userinfo／非HTTPSを拒否 |
| T43-06 | length、SHA-256、Temurin signature/fingerprint不一致をpublish前に拒否 |
| T43-07 | ZIP/TAR path escape、unsafe link/device、expanded limit超過を拒否 |
| T43-08 | budget／usable bytes不足をstaging download前に拒否 |
| T43-09 | network断はFAILED、part bytesはinventoryにならない |
| T43-10 | cancel finalはI/O停止、staging削除、reservation release後 |
| T43-11 | restartは同じIDで再開し、二重publishしない |
| T43-12 | final衝突、manifest欠落／改ざんはRECONCILIATION_REQUIRED |
| T43-13 | removalは期限付きpreviewと再照合を要求する |
| T43-14 | removal対象外path、共有JDK／SDK、symlinkは変更しない |
| T43-15 | license同意からJob/RCE/scan/buildを自動開始しない |

## Acceptance boundary

4.3 PASSは空の専用storeからAndroid操作でplan、三license同意、全seed導入、verified inventory、Runner／Android restart復元、manual removal、空store復帰、共有JDK／SDK digest不変を現物確認した場合だけ付ける。fixture unit testとarchive authoring検査を製品E2Eへ読み替えない。

4.4 targetのgeneric Docker build、read-only mount再検証、A／B、公式APK比較は4.3では`PARTIAL / deferred to 4.4`である。raw comparisonの`MATCH / DIFFERENT / INCOMPARABLE`、trust、signer、install policyをtoolchain成功で変更しない。
