# ADR-0018: Phase 4 operational foundation

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted; foundation implemented through the Phase 4.2 storage capability
- Date: 2026-09-01
- Scope: 4.0 common identity, authority, versioning and acceptance boundaries
- Requirements: [Architecture overview](../architecture/overview.md)
- Contract: [4.0 implementation foundation](../design/phase-4-foundation-contract.md)
- API: [Runner API v2 foundation](../api/runner-api-v2.md)
- Start audit and implementation evidence: 非公開の実装記録で管理
- Public verification status: [Current status](../status/current.md)

## Context

Phase 3Eの実装baselineはAPI v1、Runner SQLite8、Android Room14、private Manifest4／public3である。Phase 4の要件合意に基づき実装開始を指示されたため、工程4.0の設計を具体化する。環境cleanupと未commit文書の整理は別のentry gateであり、このADRのAcceptedをcode着手条件の充足、cleanup承認、検証完了とは扱わない。

現行AndroidはregisteredAppIdを持つがrepository URLにunique制約があり、登録がrelease選択・APK downloadと結び付く。同じprovider release IDを再取得するとsnapshotを更新する。Runnerには固定recipe／Job別同意／sandbox ownerはあるが、provider-neutral identity、client principal、保持hold、durableなrequest重複防止はない。新契約が必要な境界だけを分け、既存raw truthと旧evidenceを変更しない。

## Decision

1. 実行順を4.0→4.1→4.2→4.3→4.4a/b/c→4.5→4.6→4.7→4.8とする。4.1はAndroid側の登録・静的探索・設定保存であり、汎用Jobを作らない。
2. 管理単位はlocal UUIDで維持し、repository identityとpackage identityを分離する。provider object IDとimmutableな観測snapshotも分離し、tag移動・asset差替えを過去記録の上書きで表現しない。
3. 編集可能な設定、実行へ固定したsnapshot、実行許可、scan review、comparison結果、現在のbytes availabilityを別モデルにする。設定保存・通知・importは実行権限を生成しない。
4. API v2は比較protocol v2とは独立した実行契約とする。新旧混在では新Jobを止め、v1への実行fallbackをしない。local閲覧・登録・provider確認はRunner互換性から独立させる。
5. Runner identity、端末principal、Docker cleanup ownerを分離する。旧owner UUIDは秘密情報でも端末認証でもない。credentialはpublic evidence、Room、backup、build入力へ入れない。
6. 変更操作のrequest keyをprincipal・operation・要求hashへbindし、受付を永続化してから副作用へ進む。再送時に同じJobを再実行せず、現在の認証／失効と固定要求を再検証する。
7. 各工程でmigration、negative test、製品経路、非干渉を受け入れる。未来のDB番号や未実装capabilityを現行compatibilityへ先取りしない。

このADRは合意済み方針に対する実装上の具体化であり、ユーザーが個々のfield名を指定したという記録ではない。後続のschema／backend／library選定は各工程の契約で行う。

## Alternatives

| 案 | 見送る理由 |
|---|---|
| API v1へ汎用設定・retry権限を追加し続ける | 古いclientが新しい隔離・権限を理解せず実行できる曖昧さを避ける |
| URLまたはpackage名を管理対象のprimary keyにする | source-only、repository移転、同一repositoryの独立packageを表現できない |
| release／asset IDが同じならsnapshotを上書きする | 同じIDのtag／asset変更によって過去comparisonの対象が変わる |
| Docker ownerをclientの認証IDに転用する | 資源回収の継続性と端末の認証・失効は異なる責務である |
| memory内だけでrequestをdeduplicateする | 応答喪失やrestartでJob／予約が重複する |
| 移行とnegative testを4.8へ集約する | 各工程で履歴・権限の破壊を発見できず、後戻りが大きくなる |

## Consequences and acceptance

API v2と共通モデルは新文書で契約化するが、この変更はsource、DB、現行API v1、既存Manifest／scan digestを変更しない。新Runnerの認証が完成する4.6まではrelease／LAN運用へ進めない。固定3Eのbridgeとguardを汎用隔離・hard quotaへ読み替えず、4.4a未受入時にallowlistを外さない。

共通のnegative-test ledgerと工程別依存条件は4.0契約に置く。実装の成功証拠は各工程のreportへ記録する。承認前のcleanup、commit、merge、push、公開は実施しない。
