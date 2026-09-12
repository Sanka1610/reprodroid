# ReproDroid Runner API v1

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

## Phase availability

Phase 2Bでは、`REAL_TRUSTED`が生成した単一APKをRunner専用artifact領域から配信する。実ビルドはGitHub HTTPS URL、repositoryとrevisionを組にしたexact allowlist、固定recipe、解決済みcommit SHAとRCEリスクのJob単位確認をすべて要求する。API requestからGradle task、引数、artifact path、Java runtimeは変更できない。

Phase 2Dは同じtag／fixed profileでBuild AとBuild Bの独立Jobを2件作成するが、API v1は変更しない。各Jobはrefを独立解決し、別のJob ID、commit確認、host RCE確認、clone、build home、artifact、Build Environment Manifestを持つ。Androidは1回目の確認を2回目へ継承せず、3軸raw比較、APK全entry inventory、DEX／Manifest／resource semantic補助証跡をRunner Job stateへ送らない。Build Environment ManifestをAndroidへ公開するendpointもPhase 2Dでは追加しない。

Phase 3A は既存 API v1 を破壊せず、Build Environment Manifest の redacted public endpoint を追加した。Phase 3B は `effectiveBuild.dependencyPinning`、Phase 3Cは`effectiveBuild.determinism`をadditiveに追加した。Phase 3Cの新規private Manifestはv3、public projectionはv2であり、historical private v2／public v1を未設定値に限って維持する。request surfaceとartifact endpointは変更していない。

Phase 3Dのstatic source scan APIは[ADR-0016](../adr/0016-pre-build-static-source-scan.md)に従い実装済みである。3D時点のRunnerはSQLite v7、compact Job summary、bounded detail endpoint、digest bind済みcontinue endpointを提供する。現在の検証状態は[Current status](../status/current.md)で管理する。

Phase 3Eは[ADR-0017](../adr/0017-docker-build-sandbox-feasibility.md)をAcceptedとし、2026-08-31にsandbox fieldとpublic Manifest v3を実装した。末尾の3E節のanchorは既存リンク維持のため残す。受入範囲と確定code SHAは非公開の実装記録で追跡し、公開時点の状態は[Current status](../status/current.md)で管理する。

## Transport

- Base URL: `http://127.0.0.1:8080/v1`
- 初期開発はADB reverse経由のloopback HTTP
- JSON: `application/json`
- artifact: `application/vnd.android.package-archive`
- LANへの無認証デフォルト公開は禁止。非loopback bindは明示的な危険受容と起動時警告を要求する

## Job states

```text
CREATED
RESOLVING_SOURCE
AWAITING_CONFIRMATION
QUEUED
CLONING
VERIFYING_WRAPPER
BUILDING
DISCOVERING_ARTIFACTS
SUCCEEDED
FAILED
CANCELLED
INTERRUPTED
```

Phase 3Dは`SCANNING_SOURCE`と`AWAITING_SCAN_REVIEW`をadditiveに追加した。

## Create job

`POST /jobs`

```json
{
  "executionMode": "SIMULATED",
  "repositoryUrl": "https://github.com/example/app.git",
  "revision": {
    "type": "COMMIT",
    "value": "0123456789abcdef0123456789abcdef01234567"
  },
  "simulationOutcome": "SUCCESS"
}
```

- `executionMode`: `SIMULATED`または`REAL_TRUSTED`
- `repositoryUrl`: HTTP(S) URL。埋め込み資格情報と制御文字は許可しない
- `revision.type`: `BRANCH`、`TAG`、`COMMIT`
- `simulationOutcome`: `SIMULATED`でのみ許可。`SUCCESS`または`FAILURE`
- Gradle task、引数、artifact pathはrequestに含めない
- `REAL_TRUSTED`は`https://github.com/{owner}/{repository}[.git]`形式だけを許可し、canonical URLとrefがRunner内の固定recipeに一致しなければ拒否する
- Runner起動時に`REPRODROID_ENABLE_REAL_BUILDS=true`がない場合、`REAL_TRUSTED`は`403 REAL_BUILD_DISABLED`

Response: `202 Accepted`

```json
{
  "jobId": "f14963aa-6027-4a8f-aab4-95b01f225342",
  "state": "CREATED"
}
```

## Get job

`GET /jobs/{jobId}`

```json
{
  "jobId": "f14963aa-6027-4a8f-aab4-95b01f225342",
  "executionMode": "REAL_TRUSTED",
  "repositoryUrl": "https://github.com/MorpheApp/MicroG-RE.git",
  "requestedRevision": { "type": "BRANCH", "value": "main" },
  "resolvedCommitSha": "0123456789abcdef0123456789abcdef01234567",
  "state": "AWAITING_CONFIRMATION",
  "progressPercent": 10,
  "requiresConfirmation": true,
  "effectiveBuild": {
    "recipeId": "morpheapp-microg-re-6.1.4-default-release",
    "variantName": "defaultRelease",
    "buildRoot": ".",
    "javaMajor": 18,
    "tasks": ["clean", ":play-services-core:assembleDefaultRelease"],
    "dependencyPinning": "NONE",
    "determinism": {
      "sourceDateEpoch": null,
      "noBuildCache": false,
      "fixedLocale": null
    }
  },
  "latestLogSequence": 12,
  "artifacts": [],
  "error": null,
  "createdAt": "2026-08-20T00:00:00Z",
  "updatedAt": "2026-08-20T00:00:02Z"
}
```

`effectiveBuild`はRunnerが選んだ固定recipeの監査値であり、client requestから指定しない。`recipeId`、`variantName`、`javaMajor`はPhase 2Bで追加したnullable fieldで、旧SQLite rowや旧Runnerとの互換性を保つ。Phase 2B comparisonはこれらが期待値と完全一致しない場合にAndroid側で`INCOMPARABLE`とする。`dependencyPinning`と`determinism`はtarget identityではなく、raw comparison、trust、update、install policyを変更しない。

RunnerサービスはJDK 21で動作する。MicroG-RE `6.1.4` release recipeは`REPRODROID_JDK_18_HOME`のJDK 18を外部buildに使用し、path、regular file、実行権限、`release` metadataのJava majorを実行前に検査する。未設定はJob error `BUILD_JAVA_HOME_MISSING`、path／metadata／major不正は`BUILD_JAVA_HOME_INVALID`となる。

## Confirm real build

`POST /jobs/{jobId}/confirm`

```json
{
  "resolvedCommitSha": "0123456789abcdef0123456789abcdef01234567",
  "riskAcknowledged": true
}
```

Runnerが現在保持するcommit SHAと一致しない場合は`409 Conflict`。`SIMULATED`では使用しない。

`riskAcknowledged`が`true`でなければ`403 RISK_ACKNOWLEDGEMENT_REQUIRED`。成功時は`204 No Content`を返し、Jobを単一worker queueへ投入する。確認前にclone、checkout、Wrapper、Gradleは実行しない。retryした`REAL_TRUSTED`はrefを再解決し、以前の確認を継承しない。

## Cancel job

`POST /jobs/{jobId}/cancel`

完了済みJobは`409 Conflict`。実行中の子processも終了対象とする。

## Retry job

`POST /jobs/{jobId}/retry`

元Jobを変更せず、新しいJob IDを発行する。`REAL_TRUSTED`は再度commitとリスク確認を要求する。

## Get logs

`GET /jobs/{jobId}/logs?afterSequence=12&limit=200`

```json
{
  "entries": [
    {
      "sequence": 13,
      "timestamp": "2026-08-20T00:00:03Z",
      "level": "INFO",
      "message": "Resolved branch main to commit 0123456789abcdef"
    }
  ],
  "nextAfterSequence": 13,
  "hasMore": false
}
```

## List artifacts

`GET /jobs/{jobId}/artifacts`

```json
{
  "artifacts": [
    {
      "artifactId": "44e636f8-b180-4351-a942-19249196c560",
      "fileName": "microg-debug-6.1.4.apk",
      "sizeBytes": 12345678,
      "sha256": "lowercase-hex",
      "packageName": "",
      "versionName": "",
      "versionCode": 0
    }
  ]
}
```

実APKでは`fileName`、`sizeBytes`、`sha256`をRunner側で確定する。`packageName`、`versionName`、`versionCode`はAndroidがdownload後のAPKそのものから解析するため、Runner APIではそれぞれ空文字・空文字・`0`とする。`SIMULATED`は従来どおり模擬値を返す。

## Download artifact

`GET /jobs/{jobId}/artifacts/{artifactId}/content`

- 完了した`SUCCEEDED` Jobの登録済みartifactだけを返し、path traversalとsymbolic linkによるartifact directory外参照を許可しない
- APKはbuild workspaceからRunner専用artifact領域へコピーし、SQLiteにはstate directoryからの相対pathを保存する
- 配信直前にsizeとSHA-256を再検査し、不一致なら`409 ARTIFACT_CONTENT_INVALID`を返す
- `Content-Type: application/vnd.android.package-archive`、`Content-Length`、SHA-256を引用符で囲んだ`ETag`を返す
- AndroidはMIME type、size、`ETag`、受信byte数を検査し、受信後にSHA-256を再計算する
- Phase 1C以前のcontent pathを持たないartifactと`SIMULATED` artifactは`409 ARTIFACT_CONTENT_UNAVAILABLE`

## Health

`GET /health`

Runner version、API version、real build enabled、database readinessを返す。allowlistの詳細やローカルpathは返さない。

`realBuildEnabled`は起動時の`REPRODROID_ENABLE_REAL_BUILDS`を反映する。これはグローバルゲートの状態だけであり、Job単位のcommit/RCE確認を省略しない。

## Errors

```json
{
  "code": "REPOSITORY_NOT_ALLOWLISTED",
  "message": "Real build is not permitted for this repository.",
  "details": {}
}
```

主なHTTP status:

- `400`: request不正
- `403`: real build無効、allowlist外、確認不足
- `404`: Job/artifactなし
- `409`: 状態競合、commit不一致
- `500`: Runner内部エラー

ref解決、clone、Wrapper検査、Gradle実行、APK検出は非同期Job処理である。これらの失敗はHTTP `422`ではなく、Jobを`FAILED`へ遷移させて`error.code`と監査ログへ保存する。

artifact content固有の`409` code:

- `JOB_NOT_SUCCEEDED`: Jobが配信可能な完了状態ではない
- `ARTIFACT_CONTENT_UNAVAILABLE`: 模擬artifactまたはPhase 1C以前のcontent pathがないartifact
- `ARTIFACT_CONTENT_INVALID`: 保存済みAPKのpath、file type、size、SHA-256のいずれかが登録値と一致しない

## Phase 3A: Build Environment Manifest

> Status: **Implemented.** [ADR-0013](../adr/0013-build-environment-manifest-public-api.md) に従う additive API v1 endpoint である。

`GET /jobs/{jobId}/build-environment-manifest`

上記は Base URL `http://127.0.0.1:8080/v1` に対する相対 path であり、完全な request path は `GET /v1/jobs/{jobId}/build-environment-manifest` である。

Runner は private `reprodroid-build.json` を直接配信せず、integrity 検査と redaction 後の JSON だけを返す。API v1 の additive endpoint であり、既存 endpoint を変更しない。

Response: `200 OK`

```json
{
  "schemaVersion": 2,
  "commit": "0123456789abcdef0123456789abcdef01234567",
  "java": {
    "version": "18.0.2.1+1",
    "vendor": "Eclipse Adoptium"
  },
  "gradle": "8.14.3",
  "androidSdk": 36,
  "buildTools": "36.0.0",
  "dependencies": [
    {
      "fileName": "example-1.2.3.jar",
      "sha256": "lowercase-hex"
    }
  ],
  "apkHash": "lowercase-hex",
  "determinism": {
    "sourceDateEpoch": 1777393787,
    "noBuildCache": true,
    "fixedLocale": "C.UTF-8"
  }
}
```

次を response に含めない。

- SDK root、workspace、state directory、Gradle cache、artifact の path
- HOME、Gradle user home、環境変数値、token 様文字列
- OS detail、build root、Gradle task、Wrapper URL、dependency cache relative path

`dependencies` は file name と SHA-256 の multiset である。同名 record の対応が一意でない場合、Android は `changed` と推測せず、`added` / `missing` として説明する。

Phase 3Cの新規private Manifestはinternal schema v3、上記responseは独立したpublic schema v2である。historical private v2は、SQLite effective determinismが未設定でManifestにv3 fieldがない場合だけpublic v1として返す。private v3はdeterminism objectを必須とし、SQLite effective値との完全一致を要求する。`androidSdk` / `buildTools` は recipe に固定し、Runner が Gradle 開始前に対応する SDK package を検証した値を返す。AGP の内部選択を推測した値ではない。dependency は private path 順を引き継がず、`fileName`、`sha256` の順で安定 sort する。

Status / error contract:

| HTTP | code | 条件 |
|---:|---|---|
| 404 | `JOB_NOT_FOUND` | Job が存在しない |
| 409 | `BUILD_MANIFEST_NOT_READY` | Job が未完了、または manifest audit record がない |
| 409 | `BUILD_MANIFEST_PUBLICATION_LIMIT_EXCEEDED` | private Manifest 32 MiB、redacted projection 20,000 dependency record、1 dependency file name 255 UTF-8 byte、または UTF-8 response 8 MiB の上限を超える |
| 410 | `BUILD_MANIFEST_REDACTED_EMPTY` | integrity が成立した Manifest から redaction 後に public field を導出できない |
| 410 | `BUILD_MANIFEST_REDACTION_REQUIRED` | dependency file name に path separator、control / Bidi、credential marker、既知 token prefix があり、安全で完全な dependency projection を返せない |
| 409 | `BUILD_MANIFEST_INVALID` | path confinement、non-symlink regular-file、SHA-256、strict JSON decode、schema / field integrity のいずれかが成立しない（3Eで409へ統一。3D実装は500） |

Android は 404 / 409 / 410 / 500 を Manifest warning として扱う。これらは comparison outcome、protocol version、trust、install policy を変更しない。

error response と Runner log は private path、問題のあった file name、environment value を含めない。Android は `REAL_TRUSTED` かつ `SUCCEEDED` の Job だけで endpoint を呼び、response を同じ上限と field integrity 条件で再検査してから Room へ保存する。

## Phase 3B: dependency pinning

> Status: **Implemented.** 契約は[ADR-0014](../adr/0014-dependency-pinning-recipe-contract.md)、現在の検証状態は[Current status](../status/current.md)を正本とする。

`GET /jobs/{jobId}`の`effectiveBuild`は次のadditive fieldを返す。

```json
{
  "effectiveBuild": {
    "recipeId": "morpheapp-microg-re-6.1.4-default-release",
    "variantName": "defaultRelease",
    "buildRoot": ".",
    "javaMajor": 18,
    "tasks": ["clean", ":play-services-core:assembleDefaultRelease"],
    "dependencyPinning": "NONE"
  }
}
```

許可値:

| API value | Fixed recipe meaning |
|---|---|
| `NONE` | Runnerの3B lockfile integrity policyを要求しない |
| `LOCKFILE` | `buildRoot/gradle.lockfile`のpre / post integrityを検査する |
| `LOCKFILE_OFFLINE` | lockfile integrityに加えてGradle task前へ`--offline`を追加する |

Runnerは`effectiveBuild`が存在する場合、`NONE`を含めて`dependencyPinning`を常に明示する。serialization defaultによる欠落を許容しない。Phase 3B以前のRunner responseにはfieldがないため、Android clientはfield欠落だけをlegacy `NONE`として受理する。未知値を`NONE`へ丸めない。

`dependencyPinning`はRunner内のfixed recipeから導出する監査値である。Job create / confirm / retry requestへ対応fieldを追加せず、Androidから値や`--offline`を指定させない。pre / post lockfile SHA-256、lockfile path / content、Gradle cache pathはpublic APIへ返さない。

`LOCKFILE_OFFLINE`はGradle dependency resolution optionであり、Git clone、ref resolution、Gradle distribution / Wrapper checksum取得、build script / plugin、Runner host全体のnetwork isolationを意味しない。offline解決を含むGradle non-zeroは、出力文字列から原因を推測せず既存の`GRADLE_BUILD_FAILED`を使用する。

Phase 3B固有のterminal Job error code:

| code | 条件 |
|---|---|
| `DEPENDENCY_LOCKFILE_MISSING` | lock modeでfixed lockfileが存在しない |
| `DEPENDENCY_LOCKFILE_INVALID` | checkout confinement、non-symlink regular-file、read条件が成立しない |
| `DEPENDENCY_LOCKFILE_TOO_LARGE` | 8 MiB上限を超える |
| `DEPENDENCY_LOCKFILE_CHANGED` | post-buildの欠落、不正化、size超過、またはpre / post SHA-256不一致 |
| `DEPENDENCY_LOCK_AUDIT_PERSISTENCE_FAILED` | effective levelまたはprivate audit hashをRunner SQLiteへ保存できない |

これらは非同期Jobを`FAILED`へ遷移させるerrorであり、新しいHTTP endpointやHTTP `422`を追加しない。private pathやlockfile contentをpublic messageへ含めない。

## Phase 3C: determinism options

> Status: **Implemented.** 契約は[ADR-0015](../adr/0015-recipe-determinism-options.md)、現在の検証状態は[Current status](../status/current.md)を正本とする。

`GET /jobs/{jobId}`の`effectiveBuild`は次のadditive objectを返す。

```json
{
  "effectiveBuild": {
    "determinism": {
      "sourceDateEpoch": 1777393787,
      "noBuildCache": true,
      "fixedLocale": "C.UTF-8"
    }
  }
}
```

- `sourceDateEpoch`: nullableな非負64-bit Unix seconds。recipe literalであり、Runner clock、Job timestamp、Git timestamp、mtimeから導出しない。
- `noBuildCache`: non-null boolean。`true`の場合だけGradle task前へ`--no-build-cache`を1回追加する。
- `fixedLocale`: nullable enum。Phase 3Cの許可値は`C.UTF-8`だけで、Gradle開始前に`locale charmap`がUTF-8であることを検査する。

新Runnerは`effectiveBuild`が存在する場合、未設定でも`determinism` objectと`noBuildCache: false`を明示する。Phase 3C以前のfield欠落だけはlegacy未設定として受理する。未知locale、負epoch、required boolean欠落は未設定へ丸めない。

値はRunner内のfixed recipeが所有する。Job create、confirm、retry requestへ指定fieldを追加せず、Androidからepoch、cache option、localeを選択・上書きさせない。Runner SQLite v6はJob実行前にeffective値を保存し、再起動後は現在のrecipeから再導出せず保存値を返す。

注入範囲は`GradleWrapperMain`とその子processだけである。Git、ref verification、SDK検査、Wrapper検査は既存のrestricted environmentを維持する。`SOURCE_DATE_EPOCH`は全build toolが値を消費する保証ではなく、`--no-build-cache`はGradle Build Cache以外のconfiguration／dependency／OS cacheを無効化しない。`C.UTF-8`もhost全体の正規化や再現性を保証しない。

Phase 3C固有のterminal Job error code:

| code | 条件 |
|---|---|
| `DETERMINISM_LOCALE_UNAVAILABLE` | fixed localeの起動またはUTF-8 charmap検査に失敗した |

不正recipeの負epochとreserved build-cache task optionはregistry construction時に拒否する。private Manifest v3欠落／SQLite値不一致、設定済みJobに対するprivate v2、監査値やManifestの永続化不成立は既存のfail-closed経路を使用する。これらのcontrolsとBuild A／B差異は補助証跡であり、protocol v2 raw outcome、trust、update、signer、install policyを変更しない。

## Phase 3D: pre-build static source scan

> Status: **Implemented.** [ADR-0016](../adr/0016-pre-build-static-source-scan.md)に従うadditive API v1 contractである。現在の検証状態は[Current status](../status/current.md)で管理する。

### Job summary

`GET /jobs/{jobId}`はscan開始済みJobへnullableな`sourceScan`を返す。

```json
{
  "sourceScan": {
    "status": "COMPLETED",
    "scannerVersion": "reprodroid-static-v1",
    "resultSha256": "lowercase-hex",
    "scannedFiles": 1234,
    "scannedBytes": 12345678,
    "findingCount": 3,
    "requiresReview": true,
    "reviewed": false
  }
}
```

scan中とoperational failureではminimal summaryを返す。

```json
{"sourceScan":{"status":"SCANNING","scannerVersion":"reprodroid-static-v1"}}
```

```json
{"sourceScan":{"status":"FAILED","scannerVersion":"reprodroid-static-v1"}}
```

- summaryはJob pollingをboundedに保ち、finding detailやsource本文を含めない。
- field欠落だけをlegacy Runnerとする。未知status、負count、malformed digestを未実施へ丸めない。
- `SCANNING`／`FAILED`ではscanner version以外のresult／count／review fieldを禁止する。`COMPLETED`では例示した全fieldを必須とする。scan未到達／legacy Jobだけが`sourceScan`自体を省略する。
- `resultSha256`はresolved commit、scanner version、summary、detector counts、stable-sorted findingsを含むcanonical result byte列のSHA-256である。
- finding 0件はreview不要、1件以上は`AWAITING_SCAN_REVIEW`で停止する。

### Detail endpoint

`GET /jobs/{jobId}/source-scan`

完全なrequest pathは`GET /v1/jobs/{jobId}/source-scan`である。

```json
{
  "schemaVersion": 1,
  "jobId": "f14963aa-6027-4a8f-aab4-95b01f225342",
  "resolvedCommitSha": "0123456789abcdef0123456789abcdef01234567",
  "scannerVersion": "reprodroid-static-v1",
  "resultSha256": "lowercase-hex",
  "summary": {
    "scannedFiles": 1234,
    "scannedBytes": 12345678,
    "skippedBinaryFiles": 42,
    "skippedSymlinks": 2,
    "findingCount": 1
  },
  "detectorCounts": [
    {
      "detectorId": "UNICODE_BIDI_CONTROL",
      "count": 1
    }
  ],
  "findings": [
    {
      "detectorId": "UNICODE_BIDI_CONTROL",
      "displayPath": "module/src/File.kt",
      "line": 42,
      "column": 18
    }
  ]
}
```

public responseはUTF-8 4 MiBを上限とする。source本文、snippet、match文字列、absolute path、workspace、HOME、Runner state、environment、credential候補を返さない。control／Bidiを含むpathはraw characterではなく`U+XXXX`表記へescapeする。

`line`と`column`はcontent findingで両方を1-based integerとして必須にする。path／file単位findingでは両方を省略する。片方だけ、0、負値はinvalid responseである。schema v1で未知detector IDを返さない。Androidは未知IDをgeneric／safeへ丸めずresponse integrity failureとする。

detector ID、encoding、file allowlist、50,000 entry、256 MiB aggregate、4 MiB per-file、5,000 finding、60秒timeoutはADR-0016に固定する。上限超過をtruncateしてbuildへ進めない。

### Review endpoint

`POST /jobs/{jobId}/source-scan/continue`

完全なrequest pathは`POST /v1/jobs/{jobId}/source-scan/continue`である。

```json
{
  "scanResultSha256": "lowercase-hex",
  "riskAcknowledged": true
}
```

成功時は`204 No Content`。保存済みJob ID、`AWAITING_SCAN_REVIEW` state、result digest、acknowledgementをすべて要求する。Build AのreviewをBuild B、retry、新Jobへ継承しない。Continue前にWrapper、dependency lock pre-hash、locale preflight、Gradleを開始しない。

### Errors

| HTTP／Job | code | 条件 |
|---|---|---|
| 409 | `SOURCE_SCAN_NOT_AVAILABLE` | 未開始、legacy、`SIMULATED`、scan前state |
| Job `FAILED` | `SOURCE_SCAN_INVALID` | path／file／canonical result integrity不成立 |
| Job `FAILED` | `SOURCE_SCAN_RESOURCE_LIMIT_EXCEEDED` | entry／bytes／file／finding／response上限超過 |
| Job `FAILED` | `SOURCE_SCAN_TIMEOUT` | 60秒timeout |
| Job `FAILED` | `SOURCE_SCAN_PERSISTENCE_FAILED` | atomic result／review persistence不成立 |
| 409 | `SOURCE_SCAN_REVIEW_NOT_REQUIRED` | finding 0件、review済み、対象stateではない |
| 409 | `SOURCE_SCAN_REVIEW_DIGEST_MISMATCH` | request digest不一致 |
| 403 | `SOURCE_SCAN_RISK_ACKNOWLEDGEMENT_REQUIRED` | acknowledgement false |

content findingはこれらのterminal errorではない。利用者がContinueしたJobは既存build flowへ進む。scan evidence、review choice、Build A／B差異はprotocol v2 raw outcome、trust、update、signer、install policyを変更しない。

## Phase 3E: planned opt-in build sandbox

Status: Accepted contract / implemented。Runner SQLite v8、private Manifest v4／public v3、Android Room v14。製品E2Eとnegative acceptanceの範囲は[Current status](../status/current.md)を参照。見出しのplannedは旧anchor互換のため残す。

### Job selection and cleanup

`GET /v1/jobs/{jobId}`へtop-level `sandbox`を追加する。新RunnerのREAL_TRUSTEDではCREATEDから必須、SIMULATEDでは禁止する。executionModeは従来の2値のまま。effectiveBuild、create／confirm／source-scan continueのrequest shapeは変更しない。clientはmode／profile／image／mount／Docker引数を送れない。

新規HOST、移行済み旧HOST、DOCKERのshapeはそれぞれ次のとおり。

```json
{"sandbox":{"mode":"HOST","origin":"NEW_JOB"}}
```

```json
{"sandbox":{"mode":"HOST","origin":"LEGACY_HOST"}}
```

```json
{"sandbox":{"mode":"DOCKER","origin":"NEW_JOB","profileId":"docker-microg-v1","cleanupStatus":"NOT_CREATED"}}
```

| Field | Rule |
|---|---|
| mode | HOST / DOCKERのstrict enum |
| origin | NEW_JOB / LEGACY_HOST。後者は旧SQLiteから移行したHOSTのみ |
| profileId | DOCKER必須、初期値は`docker-microg-v1`。HOSTは禁止 |
| cleanupStatus | DOCKER必須、NOT_CREATED / PENDING / COMPLETE。HOSTは禁止 |

NOT_CREATEDはcreate intentなし、PENDINGは未回収または回収未確認のintentあり、COMPLETEは全intentを照合してcontainer除去／不存在確認済みを表す。COMPLETEはbuild成功を意味しない。DOCKER SUCCEEDEDにはCOMPLETEと整合した成功監査が必要。cancelの204は取消要求の受理であり、PENDINGならprocess停止完了とは表示しない。

選択はJob作成transactionで固定し、Runner再起動／設定変更で置換しない。retry／Build Bは新規snapshotと個別RCE／scan同意を要求する。初期profile・固定resource値・期限・所有権回収規則は本節と[ADR-0017](../adr/0017-docker-build-sandbox-feasibility.md)に従う。

Androidはfield欠落だけを旧APIの証跡未提供として扱う。明示null、未知enum、余分なHOST field、必須field欠落、DOCKER+LEGACY_HOST、state矛盾をdefaultへ丸めない。sandbox内の未知fieldも本契約では拒否する。一度保存したmode／origin／profileの変更・消失は不正refresh。ただし旧API欠落からHOST+LEGACY_HOSTへの一度の補完は許可する。cleanup状態の変化は別扱い。

### Manifest public schema v3

既存`GET /v1/jobs/{jobId}/build-environment-manifest`を使い、endpointは追加しない。新規成功JobはHOST／DOCKERともschemaVersion 3、determinismとsandboxを必須とする。commit／java／gradle／androidSdk／buildTools／dependencies／apkHashの意味とbounded validationは維持する。

ManifestのHOST sandboxは`{"mode":"HOST"}`だけ。Job側のoriginはManifest objectへコピーしない。DOCKER sandboxは次の全fieldを必須にする。

```json
{
  "mode":"DOCKER",
  "profileId":"docker-microg-v1",
  "imageDigest":"sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316",
  "platform":"linux/amd64",
  "engineVersion":"29.6.2",
  "networkMode":"BRIDGE",
  "limits":{"cpuCount":8,"cpuset":"0-7","memoryBytes":8589934592,"memorySwapBytes":8589934592,"pids":1024,"tmpfsBytes":1073741824},
  "isolation":{"uid":1000,"gid":1000,"readOnlyRoot":true,"capDropAll":true,"noNewPrivileges":true,"seccomp":"DEFAULT","sdkReadOnly":true,"jdkReadOnly":true,"dockerSocketMounted":false,"jobDiskQuotaEnforced":false}
}
```

engineVersionは実測したsafe version string（128 UTF-8 bytes以下）であり、上記の29.6.2へ固定する意味ではない。他の値は初期immutable profileと照合する。imageDigestはplatform manifest digestであり、image config IDと同値だと仮定しない。private監査で実imageとの対応を検証する。top-level java等はcontainer実行環境の観測と整合させ、hostの値を無条件に転記しない。

sandbox objectは8 KiB以下。既存private Manifest 32 MiB／public response 8 MiB／20,000 dependencies上限も維持する。container ID/name、owner/attempt ID、socket/endpoint、host path、environment、credential、raw Docker inspectは公開しない。設定値を実測済みと偽装せず、actual container policy・Job snapshot・cleanup・artifactとの不一致は`BUILD_MANIFEST_INVALID`とする。

private v4は保存済みeffective recipe ID／buildRoot／tasks／Java major、artifact SHA-256／sizeと照合する。DOCKERのGradle／Wrapper origin／SDK API／Build Toolsは固定profileとも一致させる。private `androidSdk`はcontrollerで検証したSDK mount元のpathであり、read-only mount監査と照合する。public `androidSdk`はSDK API levelであり、このprivate pathやcontainer内pathを返すfieldではない。

| Job evidence | Allowed public schema | Sandbox interpretation |
|---|---|---|
| 旧APIでsandbox欠落 | v1 / v2 | sandbox証跡未提供、旧determinism規則で検証 |
| HOST + LEGACY_HOST | v1 / v2、またはv3 HOST | 旧成功Manifestは維持。移行後に未完了Jobをbuildした場合は新v3 HOST |
| HOST + NEW_JOB | v3 | sandbox mode HOST必須、Docker field禁止 |
| DOCKER + NEW_JOB | v3 | 全DOCKER field・profile・COMPLETE cleanup整合が必須 |

Runnerはprivate v2→public v1、private v3→public v2をLEGACY_HOSTかつLEGACY_ALLOWEDなrowだけに許可する。移行時に未完了だった旧HOST Jobも、移行後のbuild開始前にmanifestFormatをREQUIRED_V4へ固定する。originはLEGACY_HOSTのままでよい。NEW_JOBは作成時からREQUIRED_V4とする。新buildのprivate v4／public v3を古い形式へdowngradeしない。Androidも保存済みv3からv1/v2へのrefreshを拒否する。既存determinism条件を緩めず、未知schema／modeや不整合をlegacyに丸めない。

### Failure and client behavior

Docker障害時もHOST fallbackは行わない。設定不正は起動失敗、それ以外のengine/image/mount/toolchain/start/resource/import/audit failureは原則非同期Job FAILED。予定codeと具体的な条件・priorityは本節を正本とする。

`SANDBOX_CONFIG_INVALID`, `SANDBOX_SNAPSHOT_INVALID`, `SANDBOX_PROFILE_UNSUPPORTED`, `SANDBOX_ENGINE_UNAVAILABLE`, `SANDBOX_IMAGE_INVALID`, `SANDBOX_MOUNT_INVALID`, `SANDBOX_TOOLCHAIN_MISMATCH`, `SANDBOX_START_FAILED`, `SANDBOX_RESOURCE_LIMIT_EXCEEDED`, `SANDBOX_OUTPUT_INVALID`, `SANDBOX_OUTPUT_LIMIT_EXCEEDED`, `SANDBOX_AUDIT_PERSISTENCE_FAILED`, `SANDBOX_CLEANUP_FAILED`。

build timeoutは既存`PROCESS_TIMEOUT`、確定した通常の非zero exitは`GRADLE_BUILD_FAILED`を維持する。OOMはexit 137だけから推定しない。すでにCANCELLED／INTERRUPTEDならcleanup失敗でterminal stateを上書きせず、PENDINGとwarningを保持する。

所有containerの回収が未確認ならREAL_TRUSTEDの新規／queued実行を止める。`POST /v1/jobs`とretryのREAL_TRUSTED requestはHTTP 503 `SANDBOX_CLEANUP_PENDING`で拒否し、そのrequestから新Jobを作らない。既存read endpointとSIMULATEDは利用可能。healthのrealBuildEnabledは設定値であり、cleanup完了やDocker稼働の証明ではない。

不正Job refreshでは前回の正常証跡を保持し、現在responseに対する新たな確認actionを無効にする。Manifest取得／整合warningはraw結果・trust・update・installを変更しない。UIは「選択した方式」「実際の実行監査」「回収未完了」を区別する。Build A/Bでmodeが違っても、それだけでraw比較をINCOMPARABLEにしない。
