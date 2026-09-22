# はじめに

このチェックアウトの操作を説明します。配布版ごとの機能は[変更履歴](../CHANGELOG.md)、必要環境は[互換性](compatibility.md)で確認してください。

## Androidアプリを入手する

[GitHub Releases](https://github.com/Sanka1610/reprodroid/releases)で対象版の署名済みAPKを取得し、Android端末で開きます。標準インストーラーに表示されるアプリ情報を確認してインストールしてください。

チェックサムや署名を追加で確認する場合は、同じReleaseの検証ファイルを使う[公開releaseの確認](security.md#公開releaseの確認)を参照してください。開発中の機能を試す場合は[GitHub Actionsのdebug APK](development.md#github-actionsから取得する)を使用します。

## アプリを登録して取得する

1. アプリ一覧の追加ボタンを押し、公開GitHub／CodebergのリポジトリURLを入力します。
2. 解析結果のアプリ名と配布元を確認し、取得モードを選んで登録します。
3. アプリ詳細から取得画面を開き、対象APKを確認して「公式APKをダウンロード」を押します。
4. APKの検査結果を確認し、インストール操作へ進みます。

APK候補が1件なら画面で選択済みになります。複数ある場合は対象を選びます。登録時・定期実行時のリリース確認はメタデータを取得するための処理で、APK取得はボタン操作で開始します。

グループ、更新通知、インストール方法、削除・エクスポートは[利用ガイド](user-guide.md)で設定できます。

## ソースからビルドして比較する

検証モードではPC上のRunnerを使用します。

1. [Runnerの導入](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/installation.md)を完了します。
2. 設定 → 検証環境からRunner認証画面を開き、[手動ペアリング](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/networking-and-pairing.md#manual-pairing)を行います。
3. [ビルド設定](user-guide.md#build-configurationを保存する)と[ツールチェーン](user-guide.md#toolchainを導入する)を準備します。
4. アプリ詳細 → 検証から[Build A／Bを実行](user-guide.md#jobsとsource-scan)します。
5. [比較結果](reproducibility.md)を確認します。
