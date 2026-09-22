# ReproDroid

ReproDroidは、公開GitHub／CodebergリポジトリからAndroidアプリのリリースを確認し、APKを取得・インストールするアプリです。PC上のRunnerを使うと、公開ソースから独立したBuild A／Bを作成し、公式APKと比較できます。

## クイックスタート

1. [GitHub Releases](https://github.com/Sanka1610/reprodroid/releases)で対象版の署名済みAPKを取得し、Android端末で開いてインストールします。
2. アプリ一覧の追加ボタンから、公開GitHubまたはCodebergのリポジトリURLを登録します。
3. アプリ詳細でリリースを確認し、対象APKを選んでダウンロードします。
4. APKのバージョン・署名・インストール条件を確認して、インストール操作へ進みます。

取得モードはAndroid単体で利用できます。ビルドと比較を行う場合は、[Runnerの導入](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/installation.md)と[検証手順](docs/user-guide.md#jobsとsource-scan)へ進んでください。

APKの入手方法と初期設定は[はじめに](docs/getting-started.md)、対応環境と配布版との差は[互換性](docs/compatibility.md)にまとめています。

## 目的別のガイド

| 目的 | 参照先 |
|---|---|
| 登録、更新確認、取得、インストール | [利用ガイド](docs/user-guide.md) |
| 比較結果を読む | [再現可能性の判定](docs/reproducibility.md) |
| 権限、認証情報、署名を確認する | [セキュリティ](docs/security.md) |
| 接続や操作の失敗を調べる | [トラブルシューティング](docs/troubleshooting.md) |
| debug APKを取得・ビルドする | [開発](docs/development.md) |
| 文書・変更履歴を探す | [ドキュメント一覧](docs/README.md) |

## ライセンス

ReproDroidは[Apache License 2.0](LICENSE)で提供しています。第三者ソフトウェアの帰属は[同梱の通知](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt)とアプリ内のライセンス画面で確認できます。
