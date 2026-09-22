# Changelog

利用者に影響する変更を記録します。

## Unreleased

- アプリ一覧を起点に、追加と設定へ進む画面構成へ変更
- 登録をURL入力と内容確認の2段階に整理し、登録後はアプリ詳細へ移動
- GitHub／Codebergのリリース・Issue等のページURLからリポジトリを抽出
- APK候補が1件の場合は取得画面で自動選択。ダウンロードは明示操作で開始
- 取得・検証・技術情報を分離し、検査済みの同版・旧版候補から更新を促さない表示へ修正
- グループ管理を独立画面にし、一覧のグループ表示切替を追加
- 設定を目的別に分割し、データ使用量・削除・監査エクスポートを個別画面へ整理
- 更新確認の間隔・指定時刻・停止、通知権限、バックグラウンド制限の設定を整理
- Shizuku／Suiによるインストールと、任意のGoogle Play installer-of-record設定を追加

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
