# ReproDroid

OSS AndroidアプリをPC側Runnerでソースからビルドし、生成APKの情報を確認してAndroid標準インストーラへ渡すクライアントです。最終的には公式APKとローカルビルドAPKを比較し、利用者自身が再現性を判断できる状態を目指します。

## 現在の状態

Phase 1E（実機相当Emulator検証）まで完了しています。

- `SIMULATED` Jobの成功・失敗を作成するCompose UI
- Ktor clientによるRunner API v1接続
- Job、ログカーソル、差分ログ、APKメタデータを保存するRoom database
- 画面表示中の2秒ポーリングと再表示時の即時同期
- WorkManagerによる起動時・バックグラウンド再同期
- cancelと、新しいJob IDを発行するretry
- `SIMULATED`/`REAL_TRUSTED`作成モード
- Runnerが解決したcommit SHA、固定build root/task、RCE警告の確認UI
- 確認状態を保存するRoom v2 migration
- Runnerからアプリ専用領域へのstreaming APK download
- MIME type、Content-Length、ETag、受信byte数、Android側SHA-256のfail-closed検査
- package、version、署名証明書SHA-256 fingerprintの解析・表示
- URLから判明した任意packageのインストール済みversion・signer比較と不一致警告
- `PackageInstaller.Session`によるユーザー確認付き単一APKインストール
- unknown app sources設定への誘導
- download結果とインストール試行を分離して保存するRoom v3 migration
- Android 14以降のPackageInstaller status PendingIntentに必要なcreator-side BAL opt-in
- callbackを失ってsessionも消失した非terminal install attemptの起動時回収
- fresh Runnerから`JOB_NOT_FOUND`となった古い非terminal Jobの`INTERRUPTED`化

Phase 1EではWindows 11側のWHPX Android EmulatorとWSL2側RunnerをWindows `adb.exe reverse`で接続し、MicroG-RE実ビルド、Android側downloadとSHA-256照合、package/version/signer表示、unknown app sources、標準`PackageInstaller`、成功／platform拒否／利用者キャンセルcallback、Room再起動復元まで確認した。詳細は[Phase 1E検証レポート](../reprodroid-project/reports/2026/08/2026-08-21-phase-1e.md)、履歴と最終状態は[Phase 1E再開・完了記録](../reprodroid-project/docs/handoffs/phase-1e-resume.md)を参照してください。

初期実装では次の縦切りを対象にします。

```text
GitHub URL・ref入力
  → RunnerへJob作成
  → 模擬ビルド または 信頼済み実ビルド
  → 永続的な進捗・ログ表示
  → APK候補選択・ダウンロード
  → 転送SHA-256確認
  → package/version/signature表示
  → Android標準インストーラ
```

実ビルド成功と転送SHA-256一致は`Buildable`を意味します。公式APKとの再現性一致を意味しません。

## リポジトリ構成

ReproDroidは3つの独立リポジトリで管理します。

- `reprodroid`: 本リポジトリ。Androidアプリ
- `reprodroid-runner`: PC側Runner
- `reprodroid-project`: 全体設計、API、ADR、環境構築、作業レポート

全体設計は[ReproDroid設計書](../reprodroid-project/docs/design/Reprodroid%20Document.md)、Runner APIは[Runner API v1](../reprodroid-project/docs/api/runner-api.md)を参照してください。

## 技術基盤

| 項目 | 内容 |
|---|---|
| 言語/UI | Kotlin + Jetpack Compose |
| Package ID | `com.sanka1610.reprodroid` |
| minSdk | 26 |
| target/compileSdk | 36 |
| build-tools | 36.0.0 |
| JDK | 21 |
| ビルド設定 | Kotlin DSL + Gradle Version Catalog |
| 永続化 | Room |
| バックグラウンド同期 | WorkManager |

## Runnerとの接続

Runnerを先に起動します。

```bash
cd ../reprodroid-runner
./gradlew run
```

`REAL_TRUSTED`を使用する場合は、Runner側でも明示的に有効化します。

```bash
REPRODROID_ENABLE_REAL_BUILDS=true ./gradlew run
```

この設定後も、Jobはref解決後に`AWAITING_CONFIRMATION`で停止します。画面に表示されたcommit SHAと固定taskを確認し、Gradle build scriptがRunnerホストで任意コードを実行できる旨に同意した場合だけbuildを開始できます。allowlistとWrapper checksum検査はサンドボックスではありません。

Runnerはデフォルトで`127.0.0.1:8080`へbindします。開発端末またはエミュレータから接続する場合はADB reverseを使用します。

```bash
adb reverse tcp:8080 tcp:8080
```

Android debugビルドの既定base URLは`http://127.0.0.1:8080`です。cleartext HTTPはdebugに限定します。認証を実装するまで、Runnerの無認証HTTPをLANへ公開しないでください。

base URLはGradle propertyで上書きできます。値には`/v1`を含めず、scheme、host、任意のportだけを指定します。

```bash
./gradlew assembleDebug -Preprodroid.runnerBaseUrl=http://127.0.0.1:18080
```

## Jobと永続化

- Job ID、状態、ログカーソル、APK候補、ダウンロード、インストール結果をRoomへ保存
- 解決済みcommit、確認要否、固定build root/taskをRoomへ保存
- 画面表示中はCoroutineで短周期ポーリング
- バックグラウンドはWorkManagerで同期
- アプリ再表示時はRunnerから即時更新
- Runnerが再起動しても、過去Jobと`INTERRUPTED`状態を表示

## APKダウンロードとインストール

初期実装は単一APKに限定します。split APK、APKS、AABは対象外です。

1. RunnerからAPKと期待SHA-256を取得
2. アプリ専用領域へ保存
3. Android側でSHA-256を再計算
4. 不一致なら保存・インストールを拒否
5. 候補APKとインストール済みpackageのversion、署名証明書fingerprintを表示
6. 利用者の明示操作で標準`PackageInstaller`を起動

標準インストーラには`REQUEST_INSTALL_PACKAGES`と端末側の「不明なアプリのインストール」許可が必要です。未許可の場合はReproDroid用の`ACTION_MANAGE_UNKNOWN_APP_SOURCES`設定を開きます。既存の同一packageアプリと署名が異なる場合、通常は上書きできません。本アプリは自動アンインストール、silent install、root/Shizuku、署名検証回避を行いません。

Android 14以降では、systemから返るstatus `PendingIntent`経由で標準確認UIを開くため、明示的な内部activityにcreator-side background activity launch opt-inを設定します。これはuser confirmationを成立させるためのplatform要件であり、確認画面を省略するものではありません。terminal callbackを失いPackageInstaller sessionも消失したattemptは、30秒の猶予後に起動時回収します。platformから受信していないstatus codeは作らず、回収理由だけを保存します。

URLから登録されるアプリのpackage nameはビルド時に確定できないため、Manifestでは`QUERY_ALL_PACKAGES`を宣言しています。主目的はインストール済みアプリ一覧の表示ではなく、ダウンロードしたAPKから判明した任意のpackage nameについて、現在のインストール状態、`longVersionCode`、`versionName`、署名証明書情報を取得し、更新可否を端末内で判定することです。

取得したインストール済みpackage情報と署名fingerprintはローカル判定にだけ使用し、Runner、配布元、analytics、広告、telemetryへ送信しません。ネットワーク通信には、利用者が入力したrepository URL、Job操作、artifact取得など、明示した処理に必要な情報だけを使用します。ReproDroidはanalytics、広告、crash reporting SDKを組み込んでいません。詳細な設計判断は[ADR-0008](../reprodroid-project/docs/adr/0008-query-all-packages-for-url-registered-apps.md)を参照してください。

Android Developer Verificationの適用状況によっては、未登録または証明書が異なるローカルビルドAPKにadvanced flowが必要になる可能性があります。OSの拒否は回避せず、結果と必要な操作を表示します。

## 環境構築

WSL2共通環境は次のスクリプトで構築します。

```bash
../reprodroid-project/scripts/setup-env.sh
```

スクリプトはJDK 21.0.12.1+1、Android SDK API 36、build-tools 36.0.0、platform-tools、emulator、Google APIs x86_64 system image、SDKライセンスを扱います。環境変数は実行結果に表示し、shell設定ファイルを自動変更しません。

初回構築後の例:

```bash
export JAVA_HOME="$HOME/.local/share/reprodroid/jdk-21.0.12.1+1"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export LD_LIBRARY_PATH="$HOME/.local/share/reprodroid/emulator-host-libs/usr/lib/x86_64-linux-gnu:$HOME/.local/share/reprodroid/emulator-host-libs/usr/lib/x86_64-linux-gnu/pulseaudio${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/15859902/bin:$PATH"
```

## ビルド・検証

基本コマンド:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Phase 1Dでは`build`を実行し、Debug/Releaseのassemble、単体テスト、Lint、Room schema v3生成、artifact streaming clientを検証します。Room schemaは`app/schemas/`でバージョン管理します。Phase 1E完了時に`./gradlew testDebugUnitTest lintDebug build --rerun-tasks -Preprodroid.runnerBaseUrl=http://127.0.0.1:18080`を実行し、113 actionable tasksすべてexecuted、`BUILD SUCCESSFUL`を確認しました。標準installerの各callbackとRoom復元はWindows Android Emulator上のE2Eで確認しています。

## 初期実装で扱わないもの

- 公式APKとの署名除外・DEX比較
- `Reproducible`の実判定
- split APK、APKS、AAB
- root/Shizuku特権インストール
- silent install、自動アンインストール、署名検証回避
- 取得モード
