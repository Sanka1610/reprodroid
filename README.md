# ReproDroid

OSS AndroidアプリをPC側Runnerでソースからビルドし、生成APKの情報を確認してAndroid標準インストーラへ渡すクライアントです。最終的には公式APKとローカルビルドAPKを比較し、利用者自身が再現性を判断できる状態を目指します。

## 現在の状態

Phase 2C（trust表示、更新関係、公式APK install、設定継承）とPhase 2D（独立再ビルド、APK全entry inventory、DEX構造比較、Manifest／resource table意味比較）は実装済みです。Phase 2Dの高度比較は説明用の補助証跡であり、protocol v2のraw 3軸判定を変更しません。

Phase 3A（Build Environment Manifest public API、Room v10、Build A / B dependency diff）は実装済みです。pinning level 表示、determinism 表示、static scan summary、Docker sandbox は未実装です。実装済みの公開境界は [ADR-0013](../reprodroid-project/docs/adr/0013-build-environment-manifest-public-api.md)、後続順序は [Phase 3 roadmap](../reprodroid-project/docs/design/phase-3-roadmap.md) を参照してください。

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
- public GitHub repository URLの登録とlatest stable release取得
- release tagのGit refをannotated tagを含めてfull commit SHAへ解決
- uploaded APKが1件なら採用し、複数ならアプリ別のvariant／ABI設定で一意に絞るfail-closed選択
- HTTPS／許可host／最大5 redirect／512 MiB／Content-Length／SHA-256を検査する参照APK取得
- release snapshotとasset検査結果を保存するRoom v4 migration
- variant／ABI設定と現在選択中assetを保存するRoom v5 migration
- APKから抽出・保存したアプリアイコン、アプリ別設定、登録、全体設定を持つMaterial 3 UI
- Runnerのcommit／recipe／variant／Java／taskとAPKのpackage／versionを先に照合する対象同一性gate
- `classes*.dex`と`lib/<abi>/*.so`の非圧縮bytesをentry単位で比較するfail-closed comparator
- path traversal、重複entry、malformed ZIP、展開量上限、保存後改ざんの拒否
- comparison runとentry結果を保存するRoom v6 migration
- Build-and-compare、full SHA／host RCE確認、状態／結果／理由を表示する最小UI
- 現在のrelease／asset／full SHAだけに限定した`Reproducible`／`Buildable`／`Different`／`Incomparable`／`Failed`表示
- official APKとinstalled packageの`longVersionCode`から独立して保存する更新関係
- 署名済み公式APKを既定にしたInstall／Updateと、専用のrelease install attempt履歴
- 未インストール時だけ選択できる署名済みlocal comparison artifactのinstall source
- theme、release variant、ABI、APK download limit、登録時management mode／install sourceを変更できる全般設定
- global default追従とアプリ別override、およびRoom v7 migration
- 同一tag／full SHA／固定recipeからBuild AとBuild Bを独立Jobとして作るprotocol v2
- 公式APK対Build A、公式APK対Build B、Build A対Build Bを別軸で保存するRoom v8 migration
- Build Bにもcommit／host RCE確認を要求し、3軸raw一致だけを`Reproducible`へするPhase 2D-1 UI／policy
- APK全entryをsignature、DEX、native code、Manifest、resource table、resource file、asset、otherへ一意分類するstreaming inventory
- entryごとの非圧縮SHA-256、size、CRC、圧縮方式、added／missing／changed／sameを3軸で保存するRoom v9 migration
- path traversal、重複entry、symlink／外部path、未知size／圧縮方式、宣言値不整合、破損ZIP、件数／展開量／memory／時間上限のfail-closed拒否
- raw DEX差異時だけ、descriptor／signatureを安定keyにclass、field、method、implementationを比較する構造証跡
- multidex配置順、table index、debug lineを比較意味から外し、annotation、encoded value、try/catch、register、分岐先、payload、参照先をcanonical化
- binary `AndroidManifest.xml`と`resources.arsc`の意味比較、およびpackage／type／name／configurationを安定keyにしたresource差異証跡
- raw outcome、inventory、semantic outcome、理由、安定key差異を分離して表示し、semantic一致でraw `Different`を昇格させないUI
- Runnerのredaction／integrity検査済みBuild Environment Manifest public schema v1を厳格・上限付きで取得するclient
- Job単位のenvironment headerとdependency multisetを原子的に保存するRoom v10 migration
- Job detailのJava／Gradle／validated SDK API／Build Tools／APK hash／dependency件数表示
- 現在のprotocol v2 Build A / Bについて、同一canonical repository URL・同一full SHAの場合だけ作成するdependency diff
- Manifest取得失敗をsession-only warningとして表示し、保存済みManifest、raw 3軸outcome、trust、install policyを変更しない境界

Phase 2A E2EではMicroG-RE `6.1.4`を取得し、release tagから`d8df10ab687a1c1ca05221634cfa46bad262023a`を解決しました。13,393,291 byteのAPKについて、GitHub provider digest、streaming中のAndroid計算SHA-256、保存後のAndroid `sha256sum`がすべて`907b0f1d64d4bdf2fc15df596129cdf9f140f5360f557d24ff2e987c9f586f15`で一致しました。package、version、signer、`INCOMPARABLE`理由、APK内アイコンの一覧表示と、アプリ別variant／ABI設定のforce-stop後復元も確認しています。

Phase 2Bでは同じtag／full SHAをTemurin 18、`clean :play-services-core:assembleDefaultRelease`で再ビルドしました。ローカルAPKのraw SHA-256は公式APKと異なりますが、package `app.revanced.android.gms`、version `6.1.4`、versionCode `255034004`が一致し、`classes.dex`、`classes2.dex`、4 ABIのnative libraryはsize／SHA-256が全件一致しました。Phase 2Bの限定比較結果は`MATCH`です。

ローカルrelease APKは上流workflowの後段sign action前なのでunsignedです。comparison専用downloadだけがunsigned APKのidentity解析を許容し、通常のinstall用downloadと`installArtifact`はsigner metadata必須を維持します。Runner Job UIではcomparison-onlyと表示し、installer導線を出しません。

Phase 2C E2Eでは、unknown app sources設定への誘導、設定画面から復帰した際の権限再評価、公式APKの利用者キャンセル／成功callback、force-stop後のattempt復元、同version再install抑止、インストール中のsource lockを確認しました。MicroG-REを一時的に未インストールへ戻して固定releaseを再ビルドし、`COMPLETED`／`MATCH`／`Reproducible`まで到達したうえで、unsigned local artifactが`PackageInstaller`起動前にfail closedで拒否されることも確認しています。さらに、同じ公式signerの`6.1.3`をfixtureとして`Update available`を導出し、ReproDroidから標準`PackageInstaller`を経て`6.1.4`へ更新しました。最終状態はversion `6.1.4`、installer package `com.sanka1610.reprodroid`です。

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

## Phase 2の方針

Phase 2では、公式APKまたは開発者公開APKをAndroidアプリ側で取得し、検証モードの参照APKと取得モードの更新候補に共通のAPK検査・保存境界を適用します。Runnerから取得したローカルビルドAPKと、配布元から取得したAPKの比較処理もAndroid側で行います。

`package name`は比較対象の同一性と更新対象の特定に使用し、`longVersionCode`は端末内アプリとの新旧判定、`versionName`は表示・補助情報に使用します。signing certificateは更新可否と標準`PackageInstaller`の結果に関わる情報として、比較結果とは別に扱います。versionが新しいことやsignerが一致することだけで`Reproducible`とは判定しません。

Phase 2Aの初期providerはpublic GitHub Releasesに限定します。`tag_name`からGit refを解決し、annotated tagをcommitまでpeelしたfull SHAを保存します。`target_commitish`は証跡として保存しますが、checkout対象にはしません。uploaded APKが1件ならそのまま採用します。複数の場合はアプリ別設定（既定`Release`／`arm64-v8a`）でfile nameを絞り、厳密に1件となる場合だけ採用します。`Preview`／`Debug`は明示tokenを要求し、`Release`は`preview`／`debug`でないassetとして扱います。

比較不能は`INCOMPARABLE`として`Different`から分離します。Phase 2BはMicroG-RE `6.1.4`だけを許可し、Runnerがtagを独立解決した後、保存済みfull SHAと一致した場合だけ利用者がbuildを確認できます。Androidは取得したRunner artifactを再検査し、対象同一性確認後にDEX／native libraryだけを比較します。`MATCH`はこの限定範囲の一致であり、APK全体やsourceの安全性を証明しません。設計判断は[ADR-0009](../reprodroid-project/docs/adr/0009-phase-2-reference-apk-and-update-boundary.md)と[ADR-0010](../reprodroid-project/docs/adr/0010-phase-2b-executable-apk-content-comparison.md)に記録しています。

Phase 2Cでは、現在選択中のrelease snapshot、asset、expected full commit SHAに一致するcomparison runだけをtrust表示へ使います。検証モードのinstall sourceは署名済み公式APKが既定です。local buildは未インストール時に明示選択し、現在runに結び付いた署名済みartifactだけを許可します。現行MicroG-RE comparison artifactはunsignedなのでfail closedで拒否します。将来のReproDroid鍵は候補ですが、Phase 2への採用は確定していません。詳細は[ADR-0011](../reprodroid-project/docs/adr/0011-phase-2c-trust-update-and-install-policy.md)を参照してください。

Phase 2Dは同じMicroG-RE `6.1.4` tagをRunnerがJobごとに独立解決し、Build AとBuild Bの両方で固定profileとhost RCE確認を要求します。公式対A、公式対B、A対Bの3軸がすべてDEX／native library raw bytesで一致した場合だけprotocol v2の`Reproducible`とします。各軸ではAPK全entry inventoryも保存し、raw差異があるDEXだけを構造比較、Manifestとresource tableを意味比較します。高度比較が`MATCH`でもraw `Different`は維持し、parser failure／未知形式／上限超過は理由付き`INCOMPARABLE`として補助証跡に残します。詳細は[ADR-0012](../reprodroid-project/docs/adr/0012-phase-2d-repeat-build-and-advanced-comparison.md)を参照してください。

Phase 2D最終E2Eは2026-08-26にfresh Runner／アプリ状態から再実行しました。Build AとBuild Bが別Job／別作業領域で同じfull SHA `d8df10ab687a1c1ca05221634cfa46bad262023a`を解決し、個別のhost RCE確認後に成功しました。両artifactは13,258,872 byte、SHA-256 `30de03caea3da52c9febbeebb5d7f0d3246811d288d81b522bb456da19e7b033`で一致しました。Android側で転送後metadataを再検証し、公式対A、公式対B、A対Bの各6 raw entryがすべて`MATCH`、protocol v2のtrustが`Reproducible`となり、cold start後にも復元されることを確認しています。全entry inventoryは公式対A／Bが署名3 entry欠落のみの`DIFFERENT`、A対Bが1,630 entryすべて`MATCH`で、semantic differenceは全軸0でした。

Phase 3A E2Eは2026-08-27にfresh Runner／アプリ状態からMicroG-RE `6.1.4`を再度2回ビルドして確認しました。Build A / Bのredacted public ManifestはJava `18.0.2.1`、Gradle `8.14.3`、validated SDK API `36`、Build Tools `36.0.0`、同一APK SHA-256、各1,031 dependency recordを返しました。dependency multisetはsame 1,031、changed 0、Build A only 0、Build B only 0で、raw 3軸`MATCH`とtrust `Reproducible`は独立して維持されました。cold start後の復元に加え、Build B private Manifestの一時欠落を`BUILD_MANIFEST_INVALID` warningとして表示し、保存済みManifest／comparison／trustを変更しないこと、復元後の再取得でwarningが消えることも確認しています。

## Phase 3 の Android 境界（3A実装済み）

Phase 3 は protocol v2 の raw 3軸、APK comparator、trust truth table、公式 APK install / update policy を変更しません。3A では Runner が redaction / integrity 検査済みの Manifest projection を返し、Android は Job 単位で Room v10 に保存して同一 repository・同一 full SHA の dependency 差分を補助説明として表示します。取得失敗は session-only warning であり、`Reproducible`、`Different`、`Incomparable`、`Failed`、install policyを変えず、以前に保存した正常Manifestも削除しません。

3B では run ごとの dependency pinning level を Room v11 へ記録・表示し、3C の determinism values は既存 Manifest 保存で表せる場合だけ追加 migration なしで表示します。3D の scan summary は、clone 前の RCE 同意を維持するため、最初の同意画面ではなく Job / comparison detail に表示します。3B以降は未実装であり、各小フェーズで [Phase 3 roadmap](../reprodroid-project/docs/design/phase-3-roadmap.md) の contract／ADR／negative testを先に確定します。

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

1. Runner artifactまたは公式release APKと期待SHA-256を取得
2. 用途別のアプリ専用領域へ保存
3. Android側でsize、SHA-256、package、version、signerを検査
4. 不一致なら保存・インストールを拒否
5. 公式APKの`longVersionCode`とインストール済みpackageから更新関係を判定し、signer relationとは別に表示
6. 未インストールまたは新しいversionだけ、利用者の明示操作で標準`PackageInstaller`を起動

標準インストーラには`REQUEST_INSTALL_PACKAGES`と端末側の「不明なアプリのインストール」許可が必要です。未許可の場合はReproDroid用の`ACTION_MANAGE_UNKNOWN_APP_SOURCES`設定を開きます。既存の同一packageアプリと署名が異なる場合、通常は上書きできません。本アプリは自動アンインストール、silent install、root/Shizuku、署名検証回避を行いません。

Android 14以降では、systemから返るstatus `PendingIntent`経由で標準確認UIを開くため、明示的な内部activityにcreator-side background activity launch opt-inを設定します。これはuser confirmationを成立させるためのplatform要件であり、確認画面を省略するものではありません。terminal callbackを失いPackageInstaller sessionも消失したattemptは、30秒の猶予後に起動時回収します。platformから受信していないstatus codeは作らず、回収理由だけを保存します。

URLから登録されるアプリのpackage nameはビルド時に確定できないため、Manifestでは`QUERY_ALL_PACKAGES`を宣言しています。主目的はインストール済みアプリ一覧の表示ではなく、ダウンロードしたAPKから判明した任意のpackage nameについて、現在のインストール状態、`longVersionCode`、`versionName`、署名証明書情報を取得し、更新可否を端末内で判定することです。

取得したインストール済みpackage情報と署名fingerprintはローカル判定にだけ使用し、Runner、配布元、analytics、広告、telemetryへ送信しません。ネットワーク通信には、利用者が入力したrepository URL、Job操作、artifact取得など、明示した処理に必要な情報だけを使用します。ReproDroidはanalytics、広告、crash reporting SDKを組み込んでいません。詳細な設計判断は[ADR-0008](../reprodroid-project/docs/adr/0008-query-all-packages-for-url-registered-apps.md)を参照してください。

Android Developer Verificationの適用状況によっては、未登録または証明書が異なるローカルビルドAPKにadvanced flowが必要になる可能性があります。OSの拒否は回避せず、結果と必要な操作を表示します。

全般設定のrelease variant、ABI、APK download limitは、新規アプリのdefaultであり、`Use global default`を選んだ既存アプリも将来変更へ追従します。management modeとinstall sourceは登録時に具体値をコピーし、全般設定変更へ追従しません。アプリ個別設定はglobal追従を解除した項目だけをoverrideします。install sourceは対象packageがインストールされている間は変更できず、repositoryが保存直前にも再照会します。

## 環境構築

WSL2共通環境は次のスクリプトで構築します。

```bash
../reprodroid-project/scripts/setup-env.sh
```

スクリプトはJDK 21.0.12.1+1、MicroG-RE release build専用JDK 18.0.2.1+1、Android SDK API 36、build-tools 36.0.0、platform-tools、emulator、Google APIs x86_64 system image、SDKライセンスを扱います。環境変数は実行結果に表示し、shell設定ファイルを自動変更しません。

初回構築後の例:

```bash
export JAVA_HOME="$HOME/.local/share/reprodroid/jdk-21.0.12.1+1"
export REPRODROID_JDK_18_HOME="$HOME/.local/share/reprodroid/jdk-18.0.2.1+1"
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

## Phase 3A完了時点の未実装・対象外

### Phase 3 で予定するが、まだ実装していないもの

- Build Environment Manifest／dependency差分からの自動的なbuild原因推定（3Aは観測値と差分だけを表示）
- recipe dependency pinning、`lockfile_offline`、runごとのpinning level表示
- `SOURCE_DATE_EPOCH`、`--no-build-cache`、fixed localeのRunner側注入と監査表示
- build前static source scan summary
- Docker sandbox feasibility調査とopt-in実行

### Phase 3 の対象外

- DEX／native raw差異をsemantic一致で`Reproducible`へ昇格する判定
- ReproDroid鍵によるlocal comparison artifactの署名
- MicroG-RE `6.1.4`以外のrelease comparison profile
- 定期更新、通知、任意assetの直接選択、private repository／GitHub token
- split APK、APKS、AAB
- root/Shizuku特権インストール
- silent install、自動アンインストール、署名検証回避
