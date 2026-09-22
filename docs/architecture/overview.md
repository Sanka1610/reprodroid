# アーキテクチャ概要

Androidはリリース情報・APK・比較結果・利用者の操作を管理し、Runnerはソースの取得とビルドを実行します。

## コンポーネントの責務

| 担当 | 責務と実装 |
|---|---|
| Androidの登録・取得 | [ManagedAppRepository.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/ManagedAppRepository.kt)の`ManagedAppRepository`が登録・追跡・リリース取得を提供 |
| Androidの比較 | [AppComparisonCoordinator.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/AppComparisonCoordinator.kt)の`AppComparisonCoordinator`が対象を照合し、結果・証拠を保存 |
| Androidのインストール | [AppInstallationCoordinator.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/AppInstallationCoordinator.kt)の`AppInstallationCoordinator`が導入状態・実行条件・結果を管理 |
| Runner | ソースのコミット解決、スキャン、ビルド、Job・成果物の保存と[API](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/api/README.md)による配信 |
| 配布元 | 公開リポジトリ、リリース、公式APKを提供。[対応範囲](../compatibility.md#対応する配布元とapk形式)を参照 |

## 処理の流れ

```text
リポジトリ登録 → リリース確認 → 公式APKの選択・取得・検査
                                  ├→ 取得モードのインストール
                                  └→ RunnerのBuild A／B → 比較 → インストール条件の確認
```

画面の遷移と状態の責務は[UIアーキテクチャ](ui.md)、比較範囲と表示条件は[再現可能性の判定](../reproducibility.md)にまとめています。

## 保存先と接続

Androidの登録・設定・履歴・比較結果はRoomへ、RunnerのJob・成果物・所有権は専用の保存先へ記録します。スキーマと移行は[互換性](../compatibility.md#保存データの互換性)、認証情報の保存・通信・公開署名は[セキュリティ](../security.md)を参照してください。

## 文書の配置

Android本体と製品横断の利用文書はこのリポジトリ、Runner本体と設定・運用・API文書は`reprodroid-runner`が管理します。各文書の入口は[ドキュメント一覧](../README.md)です。
