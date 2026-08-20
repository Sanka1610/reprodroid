# ReproDroid

OSS AndroidアプリをPC側Runnerでソースからビルドし、生成APKの情報を確認してAndroid標準インストーラへ渡すクライアントです。最終的には公式APKとローカルビルドAPKを比較し、利用者自身が再現性を判断できる状態を目指します。

## 現在の状態

Phase 1C（確認付き信頼済み実ビルド）まで実装済みです。

- `SIMULATED` Jobの成功・失敗を作成するCompose UI
- Ktor clientによるRunner API v1接続
- Job、ログカーソル、差分ログ、APKメタデータを保存するRoom database
- 画面表示中の2秒ポーリングと再表示時の即時同期
- WorkManagerによる起動時・バックグラウンド再同期
- cancelと、新しいJob IDを発行するretry
- `SIMULATED`/`REAL_TRUSTED`作成モード
- Runnerが解決したcommit SHA、固定build root/task、RCE警告の確認UI
- 確認状態を保存するRoom v2 migration

APK転送・Android側SHA-256照合・package/version/署名情報・標準インストールはPhase 1Dで実装します。Phase 1Cでは実ビルドAPKのファイル名・サイズ・Runner側SHA-256を表示しますが、APKファイルはダウンロードしません。

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
5. package、version、署名証明書fingerprintを表示
6. 利用者の明示操作で標準`PackageInstaller`を起動

Phase 1Dで標準インストーラを実装する際は、`REQUEST_INSTALL_PACKAGES`と端末側の「不明なアプリのインストール」許可が必要です。Phase 1Cでは不要なため、この権限をまだ宣言しません。既存の同一packageアプリと署名が異なる場合、通常は上書きできません。本アプリは自動アンインストール、silent install、root/Shizuku、署名検証回避を行いません。

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

Phase 1Cでは`build`を実行し、Debug/Releaseのassemble、単体テスト、Lint、Room schema v2生成、Runner APIの確認endpointを検証します。Room schemaは`app/schemas/`でバージョン管理します。

## 初期実装で扱わないもの

- 公式APKとの署名除外・DEX比較
- `Reproducible`の実判定
- split APK、APKS、AAB
- root/Shizuku特権インストール
- silent install、自動アンインストール、署名検証回避
- 取得モード
