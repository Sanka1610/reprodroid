# ADR-0026: Phase 4 release and operational hardening

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-09-09
- Contract: [Phase 4.8 release hardening](../design/phase-4-release-hardening-contract.md)

## Context

Phase 4.7までの実装に続き、daily-use alphaの配布準備とPhase 4全体の未完了受入を閉じる。旧計画には暗号化論理backupとrestoreが含まれていたが、利用者は2026-09-09にそれらをPhase 4から除外し、旧debugのuninstall時にデータを失うことを明示する方針を確定した。これは通常の同一package／同一署名upgradeのmigrationを除外する判断ではない。

## Decision

1. `.rdbak`、暗号化論理backup／restore、別端末restore、旧debugデータ移行をPhase 4対象外とする。暗号library、import経路、移行専用旧debug APKを追加しない。既存監査exportは読取用証跡であり復元手段ではない。
2. releaseは`com.sanka1610.reprodroid`、debugは`.debug` suffixとする。旧同一ID debugとreleaseの署名が異なる場合は上書き更新できない。利用者が旧debugをuninstallすると登録・設定・履歴・pairingを失い、releaseで再登録・再pairingが必要になることを明示する。自動uninstallは行わない。
3. Android／Runner自身のbounded UTF-8 log exportを追加する。任意build出力を匿名化済みとせず、欠損と打切りを表示する。自動送信・診断packageは作らない。
4. Phase 3 baselineからの全migration chain、整合したprivate事前snapshot、失敗時の元データ保持とfail-closed起動を受け入れる。snapshotはローカル障害復旧専用でportable backupではなく、権限・失効状態を巻き戻さない。
5. unsigned CI、隔離環境での手動署名、最終署名済み成果物検証を分離する。署名鍵生成は専用環境で運用者が行い、CI／RCE Runner／repositoryへ秘密を持ち込まない。公開・push・main統合は別承認である。
6. 実versionに基づくlicense／NOTICE同梱、offline license画面、permission／privacy／Google Play services非依存監査、CycloneDX JSON SBOM、checksum、signer fingerprint、署名前後digestと来歴を配布準備の必須条件とする。
7. 4.8完了はPhase 4全体のretained gapsを含む。fixtureやcompile成功で製品経路の`PARTIAL`／`NOT_RUN`を昇格させず、契約の台帳で証拠を対応付ける。

## Alternatives

旧backup計画を実装する案は利用者の確定scope変更に反する。監査exportをrestoreへ転用する案は現在trust／実行権限の混同を招く。release signingをCIやRCE Runnerへ統合する案は署名authorityを任意repository buildへ近づけるため採らない。

## Consequences and verification

旧debugデータのportable移行は提供されない。通常upgradeのデータ互換性は保持し、署名不一致を迂回しない。Phase 4.8契約のRC48台帳、roadmap最終受入、既存Phaseの台帳をすべて照合する。Acceptedは実装・試験・公開済みを意味しない。旧計画14.1／14.2と旧debug移行条件は本ADRによりsuperseded、過去reportは履歴として保持する。
