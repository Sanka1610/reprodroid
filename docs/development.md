# Development

通常利用はGitHub Releaseのproduction-signed APKを使用します。この文書はsource checkoutからdebug APKをbuildする場合の手順です。

## 必要要件

- JDK 21
- Android SDK 36
- Build Tools 36.0.0

Android SDK pathをGit管理外の`local.properties`または`ANDROID_SDK_ROOT`で指定します。

## 最小build

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

debug application IDは`com.sanka1610.reprodroid.debug`で、development keyと別app dataを使用します。production appのupdate artifactにはなりません。

## Instrumentation

Android device/emulatorを使用するtestではdebug APKとAndroidTest APKをbuildし、対象testを明示して実行します。locale依存のCompose text selectorはtest localeを固定するかlocalized resourceを使用します。

## Development Runner

debug buildではexact loopbackのdevelopment HTTPを使用できます。

```bash
export REPRODROID_TRANSPORT_MODE=DEVELOPMENT_HTTP
export REPRODROID_HOST=127.0.0.1
export REPRODROID_PORT=8080
./gradlew run
adb reverse tcp:8080 tcp:8080
```

この接続はpaired identityを作らず、release接続には使用しません。

## 文書との同期

画面名、permission、version/schema、comparison disposition、Runner capabilityを変更した場合は対応するcurrent documentを更新します。Runner route、request/response、CLI、environmentを変更する場合はRunner repositoryの文書を更新します。
