# Phase 4.2: history、storage、manual cleanup、audit export 実装契約

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Phase 4.2 code complete and locally committed; bounded residual evidence remains on the ledger
- Date: 2026-09-05
- ADR: [ADR-0019](../adr/0019-phase-4-history-storage-and-audit.md)
- Foundation: [Phase 4.0 contract](phase-4-foundation-contract.md)、[Runner API v2](../api/runner-api-v2.md)
- Implemented baseline: Android `0fb39ffa978e4e70c00c26e83ce2dbe1fd8374ce` / Room16、Runner `e09a51dd86795b5c4c3e35f7869213655c8db9c6` / SQLite9 / default API v1 plus loopback development API v2
- Verification status: [Current status](../status/current.md)

## 1. 到達点と非目標

Androidのapp detailでrelease／comparison／install／cleanupの履歴とbytes availabilityを表示する。SettingsのStorage画面でAndroidと接続中Runnerの使用量、予約量、budget、警告、削除候補、保護理由を確認し、利用者が選択したbytesだけをcleanupする。Androidから対象appまたは全appの公開可能な監査履歴をlocal JSONへexportする。

4.2はautomatic cleanup、cloud upload、Issue投稿、共有送信、backup／restore、log export、toolchain install／削除、generic build、release定期確認を実装しない。Play Store／F-Droid、Google Play servicesを追加しない。APK本文、source、private Manifest、raw logを監査exportへ含めない。

4.1の実public GitHub製品経路`PARTIAL`は独立して保持する。4.2の成功で4.1をcloseしない。

## 2. immutable historyとavailability

### 2.1 Release observation

provider release IDは10進文字列としてdomain hashへ含める。Androidの既存Long列はmigration互換の保存形式として保持できるが、export／新wireではJSON numberにしない。

release observation schema1のhash payloadは次のfieldを含む。

- provider、instance、providerRepositoryId。
- providerReleaseId、tagName、resolvedCommitSha、targetCommitishRaw。
- releaseName、releaseUrl、draft／prerelease／immutable、createdAt／publishedAt。
- 選択assetのproviderAssetId、name、stable public URL、contentType、providerSizeBytes、provider digest、selectionReason。

`fetchedAt`、ETag、download状態、local path、installed app状態はobservation hashへ含めない。同じhashの再観測は同じsnapshotを再利用して`lastObservedAt`を更新できる。hashが異なる場合はprovider release IDが同じでも新しいreleaseSnapshotId／releaseAssetIdを作る。旧snapshot／assetをUPDATEしない。latestは`lastObservedAt`とlocal UUIDの安定tie-breakで選び、provider IDだけで任意rowを選ばない。

### 2.2 Availability

ownerは`ANDROID`または特定runnerId。resource kindは初期実装で次に限定する。

| owner | kind | resource identity | bytes root |
|---|---|---|---|
| Android | REFERENCE_APK | releaseAssetId | `files/reference-apks/{id}.apk` |
| Android | REFERENCE_ICON | releaseAssetId | `files/reference-icons/{id}.png` |
| Android | RUNNER_APK | artifactId | `files/apks/{jobId}/{id}.apk` |
| Runner | JOB_WORKSPACE | jobId | `workspaces/{jobId}` |
| Runner | JOB_ARTIFACT | artifactId | DBのconfined content path |
| Runner | JOB_MANIFEST | jobId | `manifests/{jobId}` |
| Runner | JOB_LOG | jobId | Runner log rootの当該Job bytes |
| Runner | SANDBOX_IMPORT | jobId | `sandbox-imports/{jobId}` |

stateは`PRESENT`、`DELETED`、`MISSING`、`CORRUPT`、`UNKNOWN`。`PRESENT`はregular file／directoryをowner root内で再確認した状態。symlink、owner root外、size／hash不一致は`CORRUPT`または安全違反としてcleanup対象にしない。`DELETED`はReproDroid cleanupが削除を確認した状態、`MISSING`は記録上必要だが外部で消失、`UNKNOWN`は未検査。`lastUsedAt`、`checkedAt`、bytes、known SHA-256、削除run／reasonを別fieldにする。

bytes削除後もrelease、asset、Job、comparison、Manifest／scan header、raw outcome、trust、install attemptを保持する。install／再比較はavailability `PRESENT`と既存identity gateを両方要求する。historical resultだけで許可しない。

## 3. Budget、reservation、使用量

| area | 既定budget | warning | recovery reserve |
|---|---:|---:|---:|
| ANDROID | 4 GiB | 80% | 16 MiB |
| RUNNER_JOB | 64 GiB | 80% | 64 MiB |
| RUNNER_TOOLCHAIN | 32 GiB | 80% | 64 MiB |

budgetは1 MiB〜4 TiBの範囲で変更可能な整数bytes。設定をused以下へ下げても削除せず、`OVER_BUDGET`として新しい容量消費操作を停止する。warningは0〜100の表示割合だが、初期UIは80固定とする。Docker image、SQLite/WAL、audit staging等のbudget外／overheadは`unclassifiedBytes`として表示し、0へ捏造しない。

`usedBytes`はowner root内のNOFOLLOW実測。`reservedBytes`はACTIVE reservationの和。新しい容量消費操作は、次をすべて満たす場合だけreservationを永続化する。

1. `used + active reservations + required <= budget`をoverflowなしで満たす。
2. 実usable spaceが`required + recovery reserve`以上。
3. operation／resourceの重複reservationがない。
4. DBへreservationをcommitできる。

reservation stateは`ACTIVE`、`CONSUMED`、`RELEASED`、`RECONCILIATION_REQUIRED`。purpose、resource identity、requested bytes、principal、operation ID、created／updatedを保存する。process restartでACTIVEを勝手にRELEASEDへせず、対応するpart file／operationを照合する。

Androidのreference APKとRunner artifact downloadはprovider size／Runner sizeにpart＋finalの上界と16 MiBを含めて予約する。既存verified fileの再利用時は二重予約しない。容量不足でdownload statusを成功へせず、他fileを自動削除しない。

## 4. 保護規則と候補化

保護理由は複数保存・表示する。少なくとも次を実行直前にも再評価する。

- `CURRENT_RELEASE`、`CURRENT_COMPARISON_REFERENCE`、`CURRENT_COMPARISON_ARTIFACT`。
- `DOWNLOAD_ACTIVE`、`INSTALL_ACTIVE`、`COMPARISON_ACTIVE`。
- Runner JobのQUEUED／CLONING／SCANNING_SOURCE／AWAITING_SCAN_REVIEW／AWAITING_CONFIRMATION／BUILDING／DISCOVERING_ARTIFACTS等の非terminal状態。
- sandbox cleanup `PENDING`または所有resource回収未確認。
- ACTIVE reservation。
- ACTIVE retention hold。
- export snapshotが参照しているAndroid resource。

Androidの非current APKは`lastUsedAt`から30日、Runner terminal workspace／cacheはterminal更新から7日、詳細log／大容量補助証跡は90日で候補化する。4.2にtoolchain bytesはないためtoolchain cleanup candidateを生成しない。監査headerは自動候補にしない。

Androidはcurrent comparisonが参照するRunner Job／artifactへACTIVE holdを作る。holdはrunnerId、development principal、resource kind／ID、理由、Android側registeredAppId／comparisonRunIdのrequest hashへbindする。Android切断、app cold start、Runner restartで解除しない。current参照が明示変更・削除された後にだけrelease operationを送る。別runnerIdへholdを移さない。

## 5. Cleanup previewと実行

clientはfilesystem pathを送らない。preview filterはarea、resource kind、eligibleBefore、任意のresource ID最大100件。server／Androidは候補と保護対象を合わせて最大1,000件返し、超過は`truncated=true`で実行不可とする。

各itemはopaqueなcanonical UUID `itemId`、kind、resourceId、observedBytes、observedToken、eligibleAt、protection reasonsを持つ。`observedToken`はkind／ID／NOFOLLOW file key・mtime・size／DB generation等のserver内部観測をSHA-256化した値で、pathを含めない。

preview自体をUUIDで30分間durableに固定する。期限切れpreview、runnerId変更、unknown item、preview外itemを拒否する。executeは選択item ID最大100件を受け、各itemを次の順に処理する。

1. owner／principal、preview、resource identityを再検証する。
2. 現在state、hold、reservation、active operation、sandbox cleanup、current参照を再検査する。
3. observed token、NOFOLLOW path confinement、bytes identityを再検査する。
4. file／directoryを削除し、実在しない場合は`ALREADY_MISSING`とする。
5. availability、cleanup item、解放実測bytesを同じDB transactionで記録する。file副作用後にcommit不能ならoperationを`RECONCILIATION_REQUIRED`へ移す。

run stateは`PREVIEWED`、`APPLYING`、`COMPLETE`、`PARTIAL`、`REJECTED`、`RECONCILIATION_REQUIRED`。item resultは`DELETED`、`ALREADY_MISSING`、`SKIPPED_PROTECTED`、`FAILED`。全選択itemがDELETED／ALREADY_MISSINGの場合だけCOMPLETE。SKIPPEDまたはFAILEDが1件でもPARTIAL。previewは削除を承認した証拠であり、保護gateを迂回しない。

## 6. Runner API v2 `storage-retention@1`

4.2でRunner SQLite9とともにAPI v2 foundationを実装する。loopback bindかつdebug development modeだけ固定principal `local-development`を使う。非loopbackまたはrelease相当configではcredential未実装のためv2通常APIを起動せず、v1 mutationも新機能へfallbackしない。

capabilitiesは次の2件だけを返す。

~~~json
{
  "apiVersion": "v2",
  "foundationContractVersion": 1,
  "runnerId": "00000000-0000-4000-8000-000000000001",
  "runnerVersion": "0.1.0-alpha02",
  "capabilities": [
    {"id":"foundation","contractVersion":1},
    {"id":"storage-retention","contractVersion":1}
  ]
}
~~~

endpointは次に限定する。mutationは`X-ReproDroid-Contract: storage-retention@1`とcanonical UUIDの`Idempotency-Key`を要求する。

| Method / path | operation kind | 用途 |
|---|---|---|
| GET `/v2/capabilities` | read | runner／contract照合 |
| GET `/v2/operations/{operationId}` | read | durable operation結果 |
| GET `/v2/storage/summary` | read | area別used／reserved／budget／warning／filesystem state |
| POST `/v2/retention/holds` | `retention-hold-create` | JOB／ARTIFACT hold作成 |
| POST `/v2/retention/holds/{holdId}/release` | `retention-hold-release` | 明示解除 |
| POST `/v2/storage/reservations` | `storage-reservation-create` | 4.2の限定purpose予約 |
| POST `/v2/storage/reservations/{id}/release` | `storage-reservation-release` | 明示解除 |
| POST `/v2/cleanup/previews` | `cleanup-preview-create` | durable preview作成 |
| POST `/v2/cleanup/previews/{previewId}/execute` | `cleanup-execute` | 選択itemのcleanup |

request／responseはfoundationの通常上限を使う。unknown field、duplicate key、不正UTF-8、depth超過、bytes decimal overflow、unknown enumを受付前に拒否する。reason／messageにpath、environment、exception、Docker endpointを返さない。

### 6.1 Common response

mutationの新規受付はHTTP 202、同じkey／hashの再送はHTTP 200で次を返す。`result`はCOMPLETED時だけ存在し、それ以外でnullを成功として解釈しない。

~~~json
{
  "operationId": "00000000-0000-4000-8000-000000000001",
  "state": "COMPLETED",
  "kind": "retention-hold-create",
  "requestSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "result": {"type":"RETENTION_HOLD","resourceId":"00000000-0000-4000-8000-000000000002"},
  "reason": null,
  "createdAt": "2026-09-02T00:00:00Z",
  "updatedAt": "2026-09-02T00:00:00Z"
}
~~~

state／kind／requestSha256は永続rowから返す。`reason`はREJECTED／RECONCILIATION_REQUIRED時の`{code,message}`。result typeは`RETENTION_HOLD`、`STORAGE_RESERVATION`、`CLEANUP_PREVIEW`、`CLEANUP_RUN`。resourceIdは対応するcanonical UUID。unknown result typeではclientは依存操作を停止する。

### 6.2 Storage summary

`GET /v2/storage/summary`は次を返す。decimal bytes stringは0〜4 TiB、割合は整数0〜100。

~~~json
{
  "schemaVersion": 1,
  "runnerId": "00000000-0000-4000-8000-000000000001",
  "areas": [{
    "area": "RUNNER_JOB",
    "budgetBytes": "68719476736",
    "usedBytes": "0",
    "reservedBytes": "0",
    "unclassifiedBytes": "0",
    "usableBytes": "0",
    "warningPercent": 80,
    "state": "OK",
    "measurementState": "COMPLETE",
    "measuredAt": "2026-09-02T00:00:00Z"
  }]
}
~~~

areaは`RUNNER_JOB`と`RUNNER_TOOLCHAIN`を各1件。stateは`OK`、`WARNING`、`OVER_BUDGET`、`STORAGE_UNAVAILABLE`。measurementStateは`COMPLETE`、`INCOMPLETE`、`FAILED`。走査上限やIO失敗を0 bytesにせず、INCOMPLETE／FAILEDでは新しい容量消費reservationを拒否する。

### 6.3 Retention hold

create request:

~~~json
{
  "resource": {"kind":"ARTIFACT","id":"00000000-0000-4000-8000-000000000001"},
  "reason": "CURRENT_COMPARISON",
  "clientReference": {"type":"COMPARISON","id":"00000000-0000-4000-8000-000000000002"}
}
~~~

resource kindは`JOB`または`ARTIFACT`。reasonは`CURRENT_COMPARISON`、`CURRENT_RELEASE`、`EXPORT_SNAPSHOT`。clientReference typeは`REGISTERED_APP`または`COMPARISON`で、idはcanonical UUID。resourceが不存在でも他principal所有でも404。既に同じprincipal／resource／reason／client referenceのACTIVE holdがある場合は同じholdIdへ収束する。

release request bodyは`{"reason":"REFERENCE_RELEASED"}`だけ。reasonは`REFERENCE_RELEASED`、`APP_REMOVED`、`COMPARISON_REPLACED`。ACTIVEからRELEASEDだけを許可し、再送は同じ結果。別principal holdを404として扱う。

### 6.4 Storage reservation

create request:

~~~json
{
  "area": "RUNNER_JOB",
  "purpose": "CLIENT_OPERATION",
  "resource": {"kind":"JOB","id":"00000000-0000-4000-8000-000000000001"},
  "requestedBytes": "1048576"
}
~~~

areaは`RUNNER_JOB`または`RUNNER_TOOLCHAIN`、purposeは4.2で`CLIENT_OPERATION`だけ。requestedBytesは1〜16 GiBでarea budgetも超えられない。resource kindはJOB／ARTIFACTでRUNNER_JOBだけを許可し、RUNNER_TOOLCHAIN reservationは4.3 capability実装前は409 CAPABILITY_UNAVAILABLE。ACTIVE reservationは同principal／area／resource／purposeへ1件で、別keyでもdomain gateにより重複を拒否する。

release bodyは`{"reason":"OPERATION_CANCELLED"}`。reasonは`OPERATION_CANCELLED`、`OPERATION_COMPLETED`、`RESERVATION_NOT_NEEDED`。ACTIVEからRELEASEDへの明示遷移。対応resourceに未確定part bytesがある場合はRECONCILIATION_REQUIREDとし、releaseだけでbytesを消さない。

### 6.5 Cleanup preview

create request:

~~~json
{
  "area": "RUNNER_JOB",
  "resourceKinds": ["JOB_WORKSPACE","JOB_ARTIFACT","JOB_MANIFEST","JOB_LOG","SANDBOX_IMPORT"],
  "eligibleBefore": "2026-08-26T00:00:00Z",
  "resourceIds": []
}
~~~

resourceKindsは1〜5件で重複不可。resourceIdsは0〜100 canonical UUID、空はpolicy上の全候補。eligibleBeforeは現在より未来を許可しない。response/resultのpreview resourceは次のschema。

~~~json
{
  "schemaVersion": 1,
  "previewId": "00000000-0000-4000-8000-000000000001",
  "runnerId": "00000000-0000-4000-8000-000000000002",
  "state": "PREVIEWED",
  "expiresAt": "2026-09-02T00:30:00Z",
  "truncated": false,
  "items": [{
    "itemId": "00000000-0000-4000-8000-000000000003",
    "resourceKind": "JOB_WORKSPACE",
    "resourceId": "00000000-0000-4000-8000-000000000004",
    "observedBytes": "1048576",
    "observedToken": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "eligibleAt": "2026-08-01T00:00:00Z",
    "protectionReasons": []
  }]
}
~~~

itemsはresourceKind、resourceId、itemId順で安定sort。protectionReasonsは`ACTIVE_JOB`、`AWAITING_REVIEW`、`SANDBOX_CLEANUP_PENDING`、`RETENTION_HOLD`、`ACTIVE_RESERVATION`、`RESOURCE_CHANGED`。protected itemもpreviewへ表示するがexecuteでDELETEDにしない。truncated=trueのpreviewはexecuteを409 PREVIEW_INCOMPLETEで拒否する。

execute requestは`{"itemIds":["..."]}`で1〜100件、重複不可。result resource:

~~~json
{
  "schemaVersion": 1,
  "cleanupRunId": "00000000-0000-4000-8000-000000000001",
  "previewId": "00000000-0000-4000-8000-000000000002",
  "state": "PARTIAL",
  "releasedBytes": "1048576",
  "items": [{
    "itemId": "00000000-0000-4000-8000-000000000003",
    "result": "SKIPPED_PROTECTED",
    "releasedBytes": "0",
    "reason": {"code":"RETENTION_HOLD","message":"The resource is protected by an active retention hold."}
  }],
  "startedAt": "2026-09-02T00:00:00Z",
  "finishedAt": "2026-09-02T00:00:01Z"
}
~~~

reasonは固定公開code／messageだけ。FAILEDの内部exceptionやpathを返さない。operation GETから同じresultを再取得でき、detail cleanup row削除後もoperation tombstoneは別resourceを再削除しない。

SQLite9は次を追加する。

| table | 主なkey／制約 |
|---|---|
| `runner_identity` | singleton、canonical runner UUID。migrationごとに再生成しない |
| `operations` | operationId、principal、kind、key、request hash、state、result ref。namespace unique |
| `retention_holds` | holdId、principal、kind／resource、reason、ACTIVE／RELEASED。ACTIVE重複を収束 |
| `storage_reservations` | reservationId、area／purpose／resource／bytes／state／operation |
| `resource_availability` | kind／resource key、state／bytes／hash／lastUsed／checked／deletion ref |
| `cleanup_runs` | preview／execution state、principal、filter hash、期限、summary |
| `cleanup_items` | run／item、resource、observed token／bytes、protection、result／reason |

v1 readは既存履歴移行のため維持する。v2 modeのRunnerは`POST /v1/jobs`、confirm、scan continue、retryを426 `API_UPGRADE_REQUIRED`で拒否し、内部v2 Jobへ変換しない。v1のartifact／Manifest／scan／log readは4.4まで維持できるが、v2 holdが保護するbytesを削除するmutationを設けない。

## 7. Android Room16

Room16 migrationは既存全table／row／FKを保持し、次を行う。

1. `global_settings`へ`androidStorageBudgetBytes`（4 GiB）と`storageWarningPercent`（80）を追加する。
2. `release_snapshots`へ`observationSha256`、`lastObservedAt`を追加し、旧rowは保存fieldからschema1 hashをoffline計算する。旧rowに選択assetが存在しない場合は`selectedAsset: null`として履歴を保持し、network補完や架空のasset生成を行わない。provider ID unique indexを削除し、`(registeredAppId, observationSha256)` uniqueへ変える。
3. `release_assets`へobservation hashに必要なimmutable metadataの整合列を追加する場合、旧値を保持しnetwork補完しない。
4. `resource_availability`、`storage_reservations`、`retention_holds`、`cleanup_runs`、`cleanup_items`、`audit_exports`を追加する。
5. 既存verified APK／artifactはconfined regular file、size、hashが一致する場合だけPRESENT。未検査またはpath不正をPRESENTへdefaultしない。migration内で大容量file全hashを無制限実行せず、起動後bounded reconciliationへ`UNKNOWN`で渡せる。

Room15→16と全1→16 chainはmigration前snapshot gateを通す。実Phase 3E Room14とPhase 4.1 Room15 fixtureの旧全table値、raw comparison、trust、settings、source registrationを比較する。未知schema、容量不足、busy WAL、snapshot破損は4.1 gateのfail-closedを維持する。

## 8. Android storage serviceとUI

Android serviceは`files/reference-apks`、`files/reference-icons`、`files/apks`と`files/audit-exports`だけをaccounted bytes rootにする。前三者と90日経過した完了済みaudit stagingだけをcleanup候補にし、ACTIVE exportは保護する。DB、shared preferences、no-backup migration snapshotを通常cleanupへ出さない。directory walkはNOFOLLOW、最大100,000 entry、depth16、全体5秒。上限到達は使用量を`INCOMPLETE`とし、余裕ありと判定しない。

SettingsへStorage画面を追加する。

- Android／Runnerごとのused、reserved、budget、warning／over-budget、実空き容量、最終検査時刻。
- budget変更。現在使用量未満へ下げても自動削除しない旨。
- candidate／protected item、kind、概算bytes、eligible date、保護理由。
- item選択→preview再確認→manual cleanup。partial結果をitem単位表示。
- Runner停止、runnerId変更、capability欠落はAndroid local管理を維持し、Runner項目を未取得と表示する。

app detailのHistoryはrelease observationを新しい順に表示し、comparison／install／cleanup headerとavailabilityを結び付ける。同じprovider IDの変更を1件へ折り畳まない。`DELETED`／`MISSING` bytesを「検証済みで現在install可能」と表示しない。

## 9. Audit export schema1

MIME typeは`application/vnd.reprodroid.audit+json`、推奨拡張子`.rdaudit.json`。rootは次の形とする。

~~~json
{
  "schemaVersion": 1,
  "generatedAt": "2026-09-02T00:00:00Z",
  "scope": {"type":"APP","registeredAppIds":["00000000-0000-4000-8000-000000000001"]},
  "records": [{"type":"managed-app","schemaVersion":1,"payload":{},"sha256":"..."}],
  "payloadSha256": "..."
}
~~~

scope typeは`ALL`または`APP`。APPは1〜100 canonical UUID。期間filterはUTC `fromInclusive`／`toExclusive`、releaseSnapshotIds／comparisonRunIdsは各0〜100。filterなしは選択appの全履歴。records最大20,000、canonical payload最大32 MiB。超過時はtruncateせず`EXPORT_LIMIT_EXCEEDED`。

record typeは初期実装で次を許可する。

- `managed-app`、`repository-binding`、`source-discovery`、`build-configuration`。
- `release-observation`、`asset-observation`、`comparison-attempt`、`raw-comparison-axis`。
- `public-build-manifest`、`public-source-scan-summary`、`public-sandbox-summary`。
- `install-attempt`、`availability`、`cleanup-run`、`cleanup-item`。

record payloadは公開allowlistだけで再構築し、DB rowを汎用serializeしない。大きいID／bytes／version codeは10進文字列。record sortは`type`、stable resource ID、revision／attempt、record SHAの順。record SHAは`{type,schemaVersion,payload}`のJCS bytes。bundle `payloadSha256`は`{schemaVersion,scope,records}`のJCS bytesで、generatedAtとhash自身を除く。

含めないfieldはAPK／icon bytes、source本文／snippet、private Manifest、raw log／error、local absolute／relative storage path、provider signed URL、token／credential、environment、Docker endpoint／ID、Room internal key、PackageInstaller raw intentである。保存済みpublic Manifest／scan／sandboxは既存schema validatorを通過したものだけexportする。未知schema、hash不一致、破損evidenceは正常recordへ入れずexport全体をfail closedにする。

exportはapp-private stagingへwrite、fsync、size／SHA再検証後に`STAGED`となる。利用者がStorage Access Frameworkで選んだlocal destinationへcopyし、全bytes書込み成功後だけ`COMPLETE`。失敗時は`FAILED`とし、部分destinationを完成exportとして記録しない。自動share／uploadを呼ばない。stagingはACTIVE reservation／export中に保護し、明示cleanup候補は90日後とする。

## 10. Acceptance ledger

`PASS`は実装と直接試験が揃った項目だけに付ける。2026-09-05のcode完了checkpointはPASS 25、PARTIAL 3、NOT_RUN 0である。PARTIALは成功へ数えず、追加のarchive／scale／実データ受入証跡として残す。

| ID | 試験 | 必須結果 | 状態 |
|---|---|---|---|
| A4.2-01 | 同provider release／asset IDでtag SHA／metadata変更 | 新observation、旧comparison参照不変 | PASS |
| A4.2-02 | 完全同一release再観測 | 同observationへ収束、履歴重複なし | PASS |
| A4.2-03 | Room15→16／1→16／実Room14・15 | 旧全値、raw、trust、registration保持 | PARTIAL |
| A4.2-04 | SQLite8→9／未知9超過 | 旧Job／artifact／scan／sandbox保持、未知停止 | PASS |
| A4.2-05 | used+reserved+requiredがbudget exact／+1 | exact受理、+1拒否、自動削除なし | PASS |
| A4.2-06 | usable space不足／overflow／budget低下 | 新規消費停止、cleanup／監査余地保持 | PASS |
| A4.2-07 | 同時reservation／restart／part file | 二重予約なし、勝手なreleaseなし | PASS |
| A4.2-08 | current release／comparison／active download・install | cleanup candidate外、理由表示 | PASS |
| A4.2-09 | active／review待ちJob、sandbox cleanup PENDING | Runner bytes削除なし | PASS |
| A4.2-10 | Android切断／cold start／Runner restart中hold | hold維持、明示releaseまで保護 | PASS |
| A4.2-11 | preview後にhold／state／bytes token変更 | 再検証でskip、別bytes削除なし | PASS |
| A4.2-12 | 2 item中1件delete失敗 | PARTIAL、item別結果、成功分を再実行しない | PASS |
| A4.2-13 | symlink／path escape／directory差替え | cleanup拒否、owner外非干渉 | PASS |
| A4.2-14 | file削除後DB commit failure | RECONCILIATION_REQUIRED、再照合まで新規実行なし | PASS |
| A4.2-15 | v2 strict JSON／256 KiB／depth32／unknown field | 受付前拒否、operation／副作用なし | PASS |
| A4.2-16 | capability／contract欠落、別runnerId | fallbackなし、local履歴維持 | PASS |
| A4.2-17 | 同key同要求の同時再送／restart | 同operation／result、重複hold／cleanupなし | PASS |
| A4.2-18 | 同key異hash／kind／principal | conflict／namespace分離、他結果漏洩なし | PASS |
| A4.2-19 | v2 modeへのv1 mutation | 426、内部変換・新Jobなし | PASS |
| A4.2-20 | audit record／bundle JCS known vector | 安定SHA、生成時刻でrecord SHA不変 | PASS |
| A4.2-21 | export redaction canary | token、path、private Manifest、log、APK bytes 0件 | PASS |
| A4.2-22 | unknown／破損public evidence | 正常recordにせずexport失敗 | PASS |
| A4.2-23 | 20,000／20,001 record、32 MiB境界 | exact成功、超過truncateなし | PARTIAL |
| A4.2-24 | staging／destination書込み失敗・cleanup競合 | COMPLETEなし、snapshot／reservation整合 | PASS |
| A4.2-25 | bytes削除後のraw／trust／signer／install | 過去事実不変、現在install／再比較不可 | PASS |
| A4.2-26 | Runner停止でAndroid history／export | local分成功、Runner未取得を取得済みにしない | PASS |
| A4.2-27 | Android 16製品UI cold start | summary→preview→選択cleanup→履歴→export復元 | PARTIAL |
| A4.2-28 | 4.1／Phase 3回帰 | source登録、raw3軸、INCOMPARABLE、signer policy不変 | PASS |

## 11. 実装順序

1. ADR／契約／API v2 storage wireを確定し、Room16／SQLite9のtable・state・migrationを追加する。
2. Runner strict v2 foundation、capability、operation、summary、hold／reservationを実装する。
3. Runner cleanup inventory／preview／execute／reconciliationを実装する。
4. Android immutable observation、availability、reservation、local inventory／cleanupを実装する。
5. Android Runner v2 client、hold同期、Storage／History UIを接続する。
6. audit export builder、staging、SAF destination、redaction／hash検証を実装する。
7. JVM／migration／Android16／Runner restart／製品経路と回帰を受け入れ、report、README、確定full HEAD、compatibilityを同期する。
