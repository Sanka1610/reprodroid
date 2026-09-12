# ReproDroid documentation

このディレクトリは、ReproDroidの公開仕様・設計判断・利用者向け技術情報の正本です。非公開の開発レポートやローカル実行証跡がなくても、製品の役割、セキュリティ境界、互換性、制限を確認できる構成を維持します。

## Start here

- [Current status](status/current.md): 現在のrelease候補、schema、API、未完了境界
- [Architecture overview](architecture/overview.md): Android、Runner、provider、保存・比較の責務
- [Architecture Decision Records](adr/README.md): 公開する設計判断
- [Runner API](api/README.md): API v1／v2の公開契約
- [Feature contracts](design/README.md): 現在の機能別実装契約
- [Release signing policy](../release/README.md): 公開証明書と手動署名境界

## Authority

| 情報 | 正本 |
|---|---|
| 実際の動作 | production sourceとtest |
| 公開アーキテクチャ・セキュリティ境界 | `docs/architecture/` |
| 設計判断 | `docs/adr/` |
| Runner API | `docs/api/` |
| 機能別公開契約 | `docs/design/` |
| 現行release候補・公開検証状態 | `docs/status/` |
| 公開署名identity・署名手順 | `release/` |
| Runner固有の実行方法 | `reprodroid-runner` |
| 生の実行証跡、handoff、内部計画 | 非公開`reprodroid-project` |

公開文書は非公開`reprodroid-project`へリンクしません。内部資料は公開文書やsource commitを参照できますが、公開文書の正本にはなりません。

## Migration status

2026-09-12に、既存ADR、Runner API v1／v2、現在参照される機能契約、公開release metadataの移管を完了しました。開発計画、target台帳、生の検証証跡、handoff、prompt、workspace互換性は非公開領域に残し、公開文書からは参照しません。
