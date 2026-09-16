# Changelog

利用者に影響する変更を記録します。

## Unreleased

- 定期確認の有効化と方式を「更新の確認」に統合し、間隔／指定時刻／更新しないから選択
- intervalを1〜168時間の候補式へ変更し、指定時刻に24時間dialを追加
- 通知権限とbackground制限状態を「権限」に分離し、release-check通知設定は「通知」に維持

## 0.1.0-alpha05

- Apps、Add app、Settingsを横方向に移動できるroot navigationへ再編
- compact app row、group accordion、search/filter、group drag ordering
- app information、source edit、app settings、technical evidence、comparison evidenceを整理
- Settings accordion、Pure black、divider/selection outline、展開状態保存
- release-check notification policyとAndroid notification permissionを分離
- 1〜24時間interval、指定時刻、charging-only、per-app override
- English/Japanese resource、font scale 2.0、TalkBack、bottom operation feedbackを改善
- fresh databaseでReproDroid自身をoffline登録
- Room24、Android 16対応、Runner `0.1.0-alpha02`互換
- production-signed APK、CycloneDX SBOM、checksum、release manifestを公開

## 0.1.0-alpha03

- public repository登録、公式APK取得、Build A／B、raw三軸比較
- managed toolchain、Docker generic build、storage/history、secure Runner connectivity
- GitHub／Codeberg provider、metadata-only scheduled release check

alpha04は公開releaseとして配布していません。
