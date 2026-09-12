# ADR-0019: Phase 4.2 history、storage、manual cleanup、audit export

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted and implemented; bounded acceptance is recorded separately
- Date: 2026-09-02
- Scope: Phase 4.2 / 4A
- Requirements: [Architecture overview](../architecture/overview.md)
- Foundation: [ADR-0018](0018-phase-4-operational-foundation.md)、[Runner API v2 foundation](../api/runner-api-v2.md)
- Contract: [Phase 4.2 implementation contract](../design/phase-4-storage-contract.md)
- Verification status: [Current status](../status/current.md)

## Context

Phase 4.1の確定baselineはAndroid Room15、Runner SQLite8、Runner API v1である。Androidはrelease、asset、Job、comparison、install、source discovery、build設定を保持するが、同じprovider release IDの再観測を同じrowへ上書きする。APK bytesの有無と過去の比較結果も独立したavailabilityとして扱っていない。RunnerはJob workspace、artifact、Manifest、log、sandbox importを保持するが、Android current参照を保護するhold、永続reservation、cleanup preview／partial failure、durable operationがない。

期限だけでbytesを消すと、current comparison、review待ちJob、install中APK、sandbox cleanup未確認資源を破壊し得る。逆に一度検証したbytesを削除できない設計では、daily-use alphaのstorage budgetを維持できない。履歴の事実と現在bytesが利用可能かを分け、利用者がpreviewを確認した後にだけcleanupする必要がある。

監査exportはbackupやlog exportではない。Androidが保持する公開可能な履歴をlocal JSONへ出力するが、APK、source、private Manifest、raw log、credential、absolute pathを含めず、importによるtrust復元も行わない。

## Decision

1. Android Room16とRunner SQLite9を4.2へ割り当てる。旧row、raw comparison、trust、signer、install attempt、source scan digestを保持し、destructive migrationを使わない。
2. provider release／assetの観測内容をJCS＋SHA-256で識別する。同じprovider IDでもtag SHA、asset metadata、bytes identityが変われば新しいimmutable observationを作り、過去comparisonが参照するrowを上書きしない。
3. Android／Runnerの保持bytesは`PRESENT`、`DELETED`、`MISSING`、`CORRUPT`、`UNKNOWN`を持つavailabilityとして監査headerと分離する。削除は過去raw outcomeやtrustを変更しないが、install／再比較に必要なbytes gateを閉じる。
4. budget、reservation、実filesystem空き容量を別々に検査する。Android 4 GiB、Runner Job 64 GiB、Runner toolchain 32 GiB、警告80%を既定とし、budget低下で自動削除しない。
5. cleanupは自動実行しない。server／Androidがpathを公開せず候補を列挙し、利用者が選んだresource tokenを実行直前に再検証する。current／active／review／install／download／hold／sandbox cleanup `PENDING`を保護する。
6. cleanup結果はitemごとに`DELETED`、`ALREADY_MISSING`、`SKIPPED_PROTECTED`、`FAILED`を保存する。部分成功をCOMPLETEへ丸めず、削除済みitemを無条件再実行しない。
7. RunnerはAPI v2 foundationをSQLite9と同時に実装し、`foundation@1`と`storage-retention@1`だけを広告する。generic build、toolchain install、pairingは広告しない。4.6前はloopback debug限定の固定local principalであり、認証済み端末とは表示しない。
8. Runnerのmutationはdurable operationへbindする。同じprincipal／operation kind／idempotency key／request hashは同じoperationへ収束し、異なるhashは拒否する。DB受付前にfile削除や外部副作用を開始しない。
9. audit export schema1はAndroidがallowlistから作る。recordとbundle payloadをRFC 8785 JCSでcanonicalizeしSHA-256を付ける。生成時刻はrecord hashから除外しbundle metadataへ置く。exportは署名・真正性証明ではない。
10. 4.2は履歴、availability、budget、reservation、hold、manual cleanup、audit exportだけを実装する。automatic retention、backup／restore、log export、toolchain削除、generic build、通知、共有送信を追加しない。

## Alternatives

| 案 | 見送る理由 |
|---|---|
| release IDをunique keyのまま上書きする | tag移動・asset差替えで過去comparisonの対象が変わる |
| DB rowとbytesを同時に削除する | 過去のraw outcome、cleanup監査、再取得不能の区別を失う |
| AndroidだけでRunnerの保護対象を記憶する | Android切断・再install時にRunner cleanupがcurrent artifactを削除できる |
| 任意pathをcleanup APIへ送る | path traversal、別Job／別ownerの削除、client/server layout結合を招く |
| preview後のresourceを無検査で削除する | active化、hold追加、bytes差替えとの競合を破壊する |
| 期限到達時にWorkerで自動削除する | 初期運用方針の利用者確認とpartial failure監査を満たさない |
| exportへDB dumpやprivate Manifestを入れる | credential、absolute path、private evidenceを公開境界外へ出す |
| audit exportをbackupとしてimport可能にする | imported historyからtrust・install権限を生成する危険がある |

## Consequences and acceptance

storage集計はsymlinkを追跡せず、owner rootから外れるpathを対象にしない。file sizeはpreview時と実行時に再取得し、観測tokenが変われば削除せず競合として記録する。削除後にavailabilityとcleanup itemの永続化が失敗した場合は、bytesの実在を再照合して`RECONCILIATION_REQUIRED`へ移す。

Android download、Runner artifact取得、migration、将来toolchain導入は同じbudget/reservation原則へ接続する。4.2では既存APK download経路をAndroid reservationへ接続するが、Runnerの新Job受付はAPI v2 generic build未実装として停止したままにする。

完了にはRoom15→16／全chain、SQLite8→9、実Phase 3E履歴保持、budget exact境界、同時reservation、hold切断保持、preview後競合、partial failure、process restart、strict v2 wire、export hash／redaction／書込み失敗、Android 16製品経路が必要である。fixture testだけを製品経路に読み替えない。
