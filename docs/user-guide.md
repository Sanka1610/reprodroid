# 利用ガイド

このチェックアウトの画面操作を説明します。配布版との差は[変更履歴](../CHANGELOG.md)で確認してください。

<a id="appを登録する"></a>

## アプリを登録する

アプリ一覧の追加ボタンから公開GitHub／CodebergのURLを入力します。リリース・タグ・Issue等のページURLも使用できます。解析結果のアプリ名、配布元、登録目的、導入元を確認して登録してください。「登録設定」で選択肢を、「解析の詳細」でリポジトリIDや探索結果を確認できます。

登録後はリリース情報を取得し、アプリ詳細へ進みます。情報取得に失敗しても登録は残り、詳細画面から再試行できます。対応するURLとAPK形式は[互換性](compatibility.md#対応する配布元とapk形式)を参照してください。

## 一覧とグループを整理する

検索ボタンでアプリ名またはリポジトリを絞り込みます。グループ管理では表示方式の切替、グループの作成・名前変更・並べ替え・削除ができます。グループを削除すると所属アプリは未分類へ移り、アプリと履歴は残ります。

<a id="releaseを確認する"></a>

## リリースを確認する

アプリ詳細で「リリースを確認」を押します。未検査のAPK候補には「APKを取得」と表示します。APK検査後、同じリリース観測に対して導入済みのversionCodeが同じか新しければ、更新を促す表示を止めます。詳細画面への進入・復帰時にも、保存済みAPKから導入状態を再確認します。

設定 → 更新確認・通知では、確認間隔・指定時刻・停止、通信・バッテリー条件、通知を設定できます。アプリごとの上書きも可能です。定期確認で取得するのはリリース情報で、APK取得・ビルド・比較・インストールは各画面から開始します。Androidのバックグラウンド制限により実行が遅れる場合は、同じ設定画面で権限と制限状態を確認してください。

間隔や入力値の制約は[ReleaseCheckPolicy.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/ReleaseCheckPolicy.kt)の`ReleaseCheckPolicy.validate`、既定値は[ReleaseCheckEntities.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/local/ReleaseCheckEntities.kt)の`ReleaseCheckSettingsEntity`にあります。

## 公式APKを取得する

アプリ詳細から取得画面を開き、APKとサイズを確認します。1件なら選択済みになり、複数なら対象を選択します。「公式APKをダウンロード」を押すと取得と検査が始まります。

取得後はAPKからSHA-256、パッケージ名、バージョン、署名者を検査します。技術情報で配布元・ハッシュ・検査結果を確認し、画面に示されたインストール条件へ進んでください。

## Build configurationを保存する

アプリ設定、または検証画面の「ビルド設定を開く」で設定を保存します。入力フィールドと制限は[BuildConfiguration.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/BuildConfiguration.kt)の`BuildConfigurationInput`と`BuildConfigurationValidator`が定義しています。保存した設定は正規化済みJSONとハッシュを持つリビジョンになります。

## Toolchainを導入する

設定 → 検証環境 → Managed build toolchainsでカタログと導入状態を確認します。必要な構成を計画し、ライセンス、ダウンロード容量、必要容量、ハッシュを確認して導入します。完了状態`INSTALLED`を確認してからビルドへ進みます。

## Jobsとsource scan

アプリ詳細 → 検証からそのアプリのビルド・比較を進めます。ジョブ一覧は設定 → 検証環境 → Runner jobsで開きます。

1. リポジトリと解決済みの完全なコミットSHAを確認します。
2. ビルド設定、実行環境、ツールチェーンを確認します。
3. Gradleスクリプトによる任意コード実行を確認してBuild Aを承認します。
4. ソーススキャンが確認待ちになった場合は、検出内容・対象パス・スキャンのハッシュを確認して継続を判断します。
5. Build Bでも独立して同じ確認を行います。

Runnerの設定条件は[ビルドとツールチェーン](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/builds-and-toolchains.md)、結果は[再現可能性の判定](reproducibility.md)を参照してください。

## 比較する

比較画面で各軸の結果を開き、差異や不足する証拠を確認します。[再現可能性の判定](reproducibility.md)に比較範囲、表示条件、補助証拠の読み方をまとめています。

## Install／updateする

アプリ詳細または取得画面でインストール操作を選びます。既定ではAndroid標準インストーラーが開き、パッケージ・バージョン・署名を確認してインストールします。公式APKとローカルビルドの署名が異なる場合、通常の上書き更新はできません。

設定 → 取得・インストールでShizuku／Suiを選び、ReproDroidへの権限を付与できます。使用条件と標準インストーラーへ戻る条件は[APKの取得と検査](security.md#apkの取得と検査)を参照してください。「Google Playをinstallerとして記録」の意味も同じ節で説明しています。

操作結果は起動・取消・拒否・失敗・成功を区別して履歴へ保存します。

## Storageとcleanup

設定 → データ管理で保存容量と削除候補を確認し、対象を選んで削除します。Runnerの保存容量は設定 → 検証環境 → Runner storageからも確認できます。

現在使用中、確認待ち、保留、参照中のデータは保護されます。プレビュー後に対象が変わった場合は再度プレビューしてください。削除に失敗した項目は結果に残ります。

## Export

設定 → データ管理から監査記録またはAndroidログをエクスポートします。監査記録には登録・リリース・ビルド・比較・インストール等の操作、AndroidログにはReproDroid自身の運用イベントが含まれます。復元機能はありません。

RunnerとJobのログは[RunnerのローカルCLI](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/operations.md#log-export)で出力します。外部共有前にログ内のパスや秘密情報を確認してください。

## 追跡停止と完全削除

追跡を停止しても履歴は残ります。完全削除はプレビューで対象と妨げになる処理を確認し、関連操作が終了してから実行します。端末上のアプリをアンインストールする場合はAndroidのシステム操作を使用してください。
