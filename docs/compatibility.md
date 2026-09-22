# 互換性

## 配布版とチェックアウトを識別する

配布APK／ZIPの版、ソースコミット、データベースの版、対応製品は、対象Releaseの`release-manifest.json`で確認します。Android側の`compatibleRunner`とRunner側の`compatibleAndroid`を照合してください。版ごとの機能差は[変更履歴](../CHANGELOG.md)に記録しています。

ソースの状態は各リポジトリで次を確認します。

```bash
git rev-parse HEAD
git describe --tags --always
```

ローカルビルドの`versionName`が公開版と同じでも、ソースコミットやスキーマが異なることがあります。配布版の値を作業中コードへ流用せず、次の定義を参照します。

| 項目 | 定義・確認先 |
|---|---|
| Androidのapplication ID、versionName／versionCode、minSdk、targetSdk、compileSdk、Build Tools、JDK | [app/build.gradle.kts](../app/build.gradle.kts)の`android`、`defaultConfig`、`buildTypes`、`jvmToolchain` |
| Androidデータベース | [ReproDroidDatabase.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/local/ReproDroidDatabase.kt)の`ReproDroidDatabase`、`@Database.version`と各`MIGRATION_*` |
| Runnerの動作環境 | [Runner導入手順](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/installation.md#必要要件) |
| Runnerの設定既定値 | [Runner設定](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/configuration.md) |
| Runnerデータベース | [SQLiteJobStore.kt](https://github.com/Sanka1610/reprodroid-runner/blob/main/src/main/kotlin/com/sanka1610/reprodroid/runner/SQLiteJobStore.kt)の`SCHEMA_VERSION` |

## APIと機能の確認

実行中Runnerの`GET /v1/health`で版と稼働状態、`GET /v2/capabilities`で利用可能な機能と契約バージョンを確認します。認証と応答フィールドは[Runner共通API](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/api/common.md)に定義しています。

AndroidはRunner ID、接続方式、capability応答を検証してから機能を使用します。API、capability、データベースのバージョンはそれぞれ独立しています。

<a id="provider"></a>

## 対応する配布元とAPK形式

公開`github.com`／`codeberg.org`のリポジトリと、単一の`.apk`を扱います。非公開リポジトリ、GitLab、任意のForgejo／Gitea、split APK、APKS、XAPK、APKM、AABには対応していません。リリースに複数のAPKがある場合は対象を選択します。

登録URLの受理条件は[GitHubRepositoryParser.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/network/GitHubRepositoryParser.kt)の`GitHubRepositoryParser`と[CodebergRepositoryParser.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/network/CodebergRepositoryParser.kt)の`CodebergRepositoryParser`を参照してください。

## 保存データの互換性

Android・Runnerとも、更新時に対応するデータベース移行を実行します。移行後のデータを旧版で開く前に、対象版のスキーマ対応を確認してください。Androidにはバックアップ復元・端末間移行機能がありません。Runnerのデータ保全は[運用手順](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/operations.md#stateの保全)を参照してください。
