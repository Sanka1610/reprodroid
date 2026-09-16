# ReproDroid

ReproDroidは、公開されているAndroidアプリの公式APKと、その公開ソースからPC上のRunnerがそれぞれ生成した2つのAPKを比較するAndroidアプリです。利用者は、APK間の差異、ビルド条件、署名、来歴を確認できます。

## 主な機能

- 公開GitHub／Codebergリポジトリの登録
- リリース情報の手動確認と定期確認
- 複数のAPK成果物からの明示的な選択、ダウンロード、パッケージ名／バージョン／署名者の検査
- 同一ソースのリビジョンから、独立したBuild A／Bを生成
- Official vs A、Official vs B、A vs Bの3軸によるバイト単位の比較
- DEX、ネイティブライブラリ、Manifest、リソース、依存関係、ビルド環境に関する補助証拠の表示
- Runnerとの手動ペアリング、ルート公開鍵を固定したHTTPS、端末ごとの認証情報と失効管理
- 履歴、ストレージ使用状況、削除前のプレビュー、監査記録／ログのエクスポート
- Android標準の`PackageInstaller`によるインストール確認

## 対応環境

| 項目 | 現在の値 |
|---|---|
| Androidアプリ | `0.1.0-alpha05` / `versionCode 5` |
| Android OS | Android 8.0（API 26）以上 |
| targetSdk / compileSdk | 36 / 36 |
| Androidデータベース | Room24 |
| Runner | `0.1.0-alpha02` |
| Runnerデータベース | SQLite12 |
| Runner API | v1、v2、pairing v1 |
| 対応配布元 | 公開GitHub、公開Codeberg |
| パッケージ形式 | 単一APK |

詳しくは[互換性](docs/compatibility.md)を参照してください。

## Androidアプリを入手する

用途に応じて、次のいずれかを選びます。

- 通常利用：[GitHub Release](https://github.com/Sanka1610/reprodroid/releases/tag/v0.1.0-alpha05)から、正式に署名された`reprodroid-0.1.0-alpha05.apk`を取得します。
- 動作確認・開発：[GitHub ActionsのAndroid CI](https://github.com/Sanka1610/reprodroid/actions/workflows/ci.yml)で、対象コミットに対応する成功済みの実行から`reprodroid-unsigned-<commit SHA>`をダウンロードし、その中の`app-debug.apk`を使用します。成果物の保持期間は14日間です。

debug APKは、製品版とはapplication ID、署名、保存データが異なります。GitHub Actionsの成果物に同梱される`app-release-unsigned.apk`は未署名であり、インストール用ではありません。

取得したAPKをAndroid端末で開き、標準インストーラーに表示されるアプリ情報を確認してからインストールします。

### 公開物を追加で確認する（任意）

GitHub Releaseには、APKのほかに次の検証用ファイルがあります。

- `reprodroid-0.1.0-alpha05.cyclonedx.json`
- `SHA256SUMS`
- `release-manifest.json`

チェックサムを確認する場合は、APKと`SHA256SUMS`を同じGitHub Releaseから取得して実行します。

```bash
sha256sum -c SHA256SUMS
```

署名証明書を確認する場合は、Android SDKの`apksigner`を使用します。

```bash
apksigner verify --verbose --print-certs reprodroid-0.1.0-alpha05.apk
```

表示された署名証明書のSHA-256が[公開署名の識別情報](docs/security.md#公開releaseの確認)と一致することを確認します。ファイル名だけでAPKを判断せず、検証に使用するAPK、`SHA256SUMS`、`release-manifest.json`は同じGitHub Releaseから取得してください。

## クイックスタート

1. 対応する[ReproDroid Runner v0.1.0-alpha02](https://github.com/Sanka1610/reprodroid-runner/releases/tag/v0.1.0-alpha02)を導入します。
2. Runnerでpaired HTTPSを初期化し、`pairing-open`を実行します。
3. ReproDroidの`Settings` → `Runner` → `Runner settings and authentication`で手動ペアリングします。
4. `Add app`で公開GitHubまたはCodebergのリポジトリURLを登録します。
5. リリースを確認し、必要な場合は公式APKの成果物を明示的に選択します。
6. ビルド構成とツールチェーンを確認し、Build AとBuild Bを個別に承認します。
7. `Comparison evidence`で3軸の比較結果と補助証拠を確認します。

詳しい導入手順は[はじめに](docs/getting-started.md)、画面操作は[利用ガイド](docs/user-guide.md)を参照してください。

## 比較結果の読み方

| 表示 | 条件 |
|---|---|
| `Reproducible` | 同じリリース観測に属するOfficial vs A、Official vs B、A vs Bがすべて`MATCH`し、必要な識別情報、信頼状態、インストール条件も成立している |
| `Different` | 定義された比較軸の少なくとも1つが`DIFFERENT`である |
| `Incomparable` | 成果物、識別情報、構成、証拠の不足または不整合により、比較条件が成立しない |

ビルドの成功、ソーススキャンでの指摘なし、Build AとBuild Bの一致、意味的な比較の一致のうち、いずれか1つだけでは`Reproducible`になりません。詳しくは[再現可能性の判定](docs/reproducibility.md)を参照してください。

## セキュリティと既知の制限

- 汎用ビルドでは、RunnerのDockerコンテナ内で任意のコードを実行します。現在のプロファイルには、固定の送信先許可リストと、Job単位のハードディスク容量／inode数の上限がありません。
- リリース用の接続では、手動ペアリングとルート公開鍵を固定したHTTPSを使用します。
- APKのインストール／更新時にはAndroid標準の`PackageInstaller`が開き、利用者の確認とAndroidによる署名者の継承関係（signer lineage）の判定が必要です。
- 定期的なリリース確認では、リリース情報を取得して通知します。ビルドは`Jobs`画面から、インストールはAndroid標準のインストーラーから開始します。
- 非公開リポジトリ、GitLab、任意のForgejo／Gitea、split APK／APKS／AAB、サイレントインストール、バックアップ／復元には対応していません。
- 利用状況の解析、広告、トラッキング、クラッシュ情報の自動送信は行いません。

詳しくは[セキュリティ](docs/security.md)を参照してください。

## ドキュメント

- [ドキュメント一覧](docs/README.md)
- [はじめに](docs/getting-started.md)
- [利用ガイド](docs/user-guide.md)
- [再現可能性の判定](docs/reproducibility.md)
- [セキュリティ](docs/security.md)
- [互換性](docs/compatibility.md)
- [トラブルシューティング](docs/troubleshooting.md)
- [開発](docs/development.md)
- [リリース手順](docs/releasing.md)
- [アーキテクチャ概要](docs/architecture/overview.md)
- [UIアーキテクチャ](docs/architecture/ui.md)
- [変更履歴](CHANGELOG.md)

Runnerの設定、CLI、API、Docker、状態管理については、[Runnerのドキュメント](https://github.com/Sanka1610/reprodroid-runner/tree/main/docs)を参照してください。

## ソースからビルドする

debug APKのビルドとテストについては、[開発](docs/development.md)を参照してください。debug APKは、製品版APKとはapplication ID、署名、保存データが異なります。

## ライセンス

ReproDroidは[Apache License 2.0](LICENSE)で提供しています。Androidアプリ内で、第三者ソフトウェアのライセンス情報を確認できます。
