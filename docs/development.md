# Development

通常利用はGitHub Releaseのproduction-signed APKを使用します。この文書はソースのチェックアウトからdebug APKをビルドする場合の手順です。

## 必要要件

- JDK 21
- Android SDK 36
- Build Tools 36.0.0

Android SDK パスをGit管理外の`local.properties`または`ANDROID_SDK_ROOT`で指定します。

## GitHub Actionsから取得する

[Android CI](https://github.com/Sanka1610/reprodroid/actions/workflows/ci.yml)で対象コミットの成功した実行を開き、`reprodroid-unsigned-<commit SHA>`内の`app-debug.apk`を取得します。成果物名と保持期間は[ci.yml](../.github/workflows/ci.yml)の`Upload unsigned verification artifacts`で確認できます。`app-release-unsigned.apk`は署名前の配布用入力です。

## 最小build

Androidリポジトリのルートで実行します。

```bash
./gradlew :app:assembleDebug
```

変更箇所に応じて`testDebugUnitTest --tests <テストクラス>`や`lintDebug`を追加します。

debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

debug application IDは`com.sanka1610.reprodroid.debug`で、development keyと別アプリ dataを使用します。製品版アプリの更新成果物にはなりません。

## Instrumentation

Android device/emulatorを使用するテストではdebug APKとAndroidTest APKをビルドし、対象テストを明示して実行します。locale依存のCompose text selectorはテスト localeを固定するかlocalized リソースを使用します。

## Development Runner

debug ビルドではexact loopbackのdevelopment HTTPを使用できます。次はRunnerリポジトリのルートで実行します。

```bash
export REPRODROID_TRANSPORT_MODE=DEVELOPMENT_HTTP
export REPRODROID_HOST=127.0.0.1
export REPRODROID_PORT=8080
./gradlew run
```

別ターミナルで`adb reverse tcp:8080 tcp:8080`を実行します。接続方式の条件は[Runner接続ガイド](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/networking-and-pairing.md#development-httpとadb-reverse)を参照してください。

## 文書との同期

画面名、権限、version/schema、comparison disposition、Runner capabilityを変更した場合は対応するcurrent documentを更新します。Runner route、request/response、CLI、環境変数を変更する場合はRunner リポジトリの文書を更新します。
