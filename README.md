# ReproDroid

**Phase 4.5実装（2026-09-06）:** Android Room20、provider IDのlossless TEXT migration、public GitHub metadata-only確認、Roomを正本とするschedule／cooldown／retry、candidate／notification outbox／dedup、明示的な通知permission操作、全体／app別設定UIを実装しました。scheduled／manual metadata checkはAPK・source archiveを取得せず、toolchain、Runner Job、build／comparison／trust／installを開始しません。debug／release JVM testは各147件（skip／failure／error 0）、lintDebugはerror 0（warning 32、hint 1）、debug／release assembleとdebug AndroidTest APK compileがPASSです。端末がないためinstrumentation実行、Android 16の実public GitHub／scheduled／background／notification製品受入は`NOT_RUN`であり、製品経路の完了を意味しません。正本は[Phase 4.5契約](../reprodroid-project/docs/design/phase-4-release-check-contract.md)、[ADR-0023](../reprodroid-project/docs/adr/0023-phase-4-scheduled-release-discovery-and-notifications.md)、[実装記録](../reprodroid-project/reports/2026/09/2026-09-06-phase-4-5-implementation.md)です。

**UI-R現在地（2026-09-06）:** Phase 4.4と4.5の間の特殊工程として、Room19、typed route、Apps／Add／Settingsとapp内Information／Edit／Settings／Remove、単一所属group、追跡解除／再開、Settingsからの完全local deletion、System／Light／Dark＋Dynamic Color、英語base／日本語resource、将来Phaseのdisabled接続点を実装しました。Phase 4.5〜4.8のbackend機能は先行実装していません。debug／release JVM testは各127件、lint、debug／release assembleがPASSです。接続端末を確認できず、Room migration、TalkBack、font scale、uninstall／deletion、Android 16製品経路は`NOT_RUN`です。詳細は[UI-R実装記録](../reprodroid-project/reports/2026/09/2026-09-06-ui-r-implementation.md)を参照してください。

**Phase 4.4 baseline（2026-09-05）:** Room18へgeneric build／comparison client、configuration snapshot、raw 3軸履歴、限定resource retry表示、release単位の複数APK明示選択を実装しました。複数候補は先頭やsizeで自動選択せず、候補metadataだけを表示し、利用者が選んだ1 APKだけを取得・検査します。Runner-owned truthを複製せず、API capability／runner ID／schemaを厳格検証します。JVM testは123件、Android 16 instrumentationは64件（明示opt-in skip 9）、いずれもfailure 0です。全9 artifactの空store導入とRunner再起動後inventory復元は確認しました。generic Android + Docker Build A/B製品E2Eは利用者指示により実施しておらず、Reproducible判定の証拠ではありません。v1 execution mutationへのfallbackはありません。

OSS AndroidアプリをPC側Runnerでソースからビルドし、生成APKの情報を確認してAndroid標準インストーラへ渡すクライアントです。最終的には公式APKとローカルビルドAPKを比較し、利用者自身が再現性を判断できる状態を目指します。

## 現在の状態

Phase 2C（trust表示、更新関係、公式APK install、設定継承）とPhase 2D（独立再ビルド、APK全entry inventory、DEX構造比較、Manifest／resource table意味比較）は実装済みです。Phase 2Dの高度比較は説明用の補助証跡であり、protocol v2のraw 3軸判定を変更しません。

Phase 3A（Build Environment Manifest public API、Room v10、Build A / B dependency diff）、3B（dependency pinning API取込、Room v11、Job／comparison表示）、3C（determinism API／Manifest取込、Room v12、bounded表示）、3D（pre-build static source scan API取込、Room v13、条件付きreview gate、bounded表示）は実装済みです。pinning、determinism、scan findingsは補助的な監査証拠であり、raw comparison、trust、update、install policyを変更しません。3Eのsandbox取込み／Room v14／個別同意UIも実装し、検証範囲は最終受入記録にまとめています。後続順序は [Phase 3 roadmap](../reprodroid-project/docs/design/phase-3-roadmap.md) を参照してください。

Phase 4.1では、release／APK／package名／Runner接続を登録条件から分離しました。bounded GitHub metadata readerはrepository IDとpublic状態を検証し、default branchをfull commit SHAへ解決して非再帰treeだけを走査します。blob本文、symlink、submodule、`.git`／`.gradle`配下を取得・実行しません。登録、再探索、設定保存はAPK download、Gradle、Job、comparison、installを開始せず、既存raw comparison／trust／signer policyも変更しません。

Room15はprovider repository IDと管理slot、immutableなsource discovery、Gradle候補、JCS SHA-256付きbuild設定revision、current headを既存tableのsidecarとして追加します。Room14からの起動前migration gateはWAL checkpoint後のprivate snapshotをSHA-256／integrity／Room identityまで検証し、未知schema・容量不足・snapshot破損ではdestructive fallbackせず停止します。

Room16は同じprovider release IDの再観測をJCS SHA-256で区別し、旧comparisonが参照するrelease／asset rowを上書きしません。過去のraw outcome、trust、signer、install attemptをbytes availabilityから分離し、bytesが`DELETED`／`MISSING`／`CORRUPT`なら現在のinstall／再比較だけを閉じます。Androidの4 GiB budget、16 MiB recovery reserve、永続reservation、NOFOLLOW集計、保護理由付きmanual cleanup、item別partial／reconciliationを追加しました。

Room17はtoolchain installationの参照と最終観測だけを追加します。Managed toolchains画面はbundled catalogから解決されたexact plan、download／reservation bytes、license本文・source・digestを表示し、すべてのcurrent licenseへの個別同意後だけ導入を開始します。cancelは現在のI/O停止を待つ要求であり即時成功表示にしません。inventory削除は対象と解放予定bytesをpreviewし、その後の明示的な確定操作を必須とします。共有developer JDK／Android SDKの探索・import・変更は行いません。

Room18はgeneric comparison attempt、公式APK identity、A／B Job、Official-vs-A／Official-vs-B／A-vs-Bのraw outcome、resource retry referenceを保存します。releaseの有効APK候補が複数でABI／variant条件でも一意に決まらない時は`AWAITING_ASSET_SELECTION`へ止め、candidateのfile名、provider size、content type、provider digest、file名由来のABI／variant推定だけを表示します。package／signerはdownloadとAPK検査後まで表示せず、selectionは同じrelease observation内の1回だけに固定します。generic build開始にはRunnerの`generic-build@1`／`apk-comparison@1`、Room保存済み設定hash、公式APK identity、RCE confirmationを要求し、unknown capability、別runner、旧v1 mutationをfail closedで拒否します。

Storage画面はAndroidと互換Runnerのused／reserved／budget／usable spaceを表示します。Runner停止、capability不一致、runnerId変更を空容量へ変換せず、Android側の履歴と監査exportは維持します。監査exportは公開allowlistからschema1 JCSを再構築し、APK、source本文、private Manifest、raw log、credential、storage pathを含めません。app-private staging後にSAFへcopyし、destinationを再読込してsize／SHA-256が一致した場合だけ`COMPLETE`とします。自動share／uploadはありません。

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
- uploaded APKが1件またはvariant／ABI設定で一意に絞れる場合だけ採用し、複数候補はrelease単位の明示選択まで停止するfail-closed選択
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
- Runnerのredaction／integrity検査済みBuild Environment Manifest public schema v1／v2を厳格・上限付きで取得するclient
- Job単位のenvironment headerとdependency multisetを原子的に保存するRoom v10 migration
- Job detailのJava／Gradle／validated SDK API／Build Tools／APK hash／dependency件数表示
- 現在のprotocol v2 Build A / Bについて、同一canonical repository URL・同一full SHAの場合だけ作成するdependency diff
- Manifest取得失敗をsession-only warningとして表示し、保存済みManifest、raw 3軸outcome、trust、install policyを変更しない境界
- Runnerの`effectiveBuild.dependencyPinning`を欠落時`NONE`・未知値拒否で取り込むAPI互換境界
- JobとBuild A／B snapshotを保存するRoom v11 migration
- RCE確認前、Job detail、comparison detailのbounded pinning表示
- Build A／Bのpinning差異をadvisoryに限定し、raw outcome、trust、update、install policyへ接続しない境界
- Runnerの`effectiveBuild.determinism`とpublic Manifest v2を欠落／schema／Job一致まで検査するAPI互換境界
- Job policyとManifest evidenceを別列で保存し、v11 evidenceを保持するRoom v12 migration
- epochの値とUTC時刻、Gradle Build Cache policy、process localeだけを表示するbounded UI
- Build A／Bのdeterminism差異をadvisoryに限定し、raw outcome、trust、update、install policyへ接続しない境界
- `SCANNING_SOURCE`／`AWAITING_SCAN_REVIEW`、compact summary、4 MiB上限のbounded source scan detailを厳格検査するAPI client
- scan header／detector counts／duplicateを含むordered findings／review stateをJob単位で保存するRoom v13 migration
- Job detailとBuild A／B comparison detailの最大40 findings表示、残件数、digest bind済みreview checkbox／continue action
- Build AのRCE確認・scan reviewをBuild Bへ継承せず、scan evidenceをraw outcome、trust、update、install policyへ接続しない境界

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

Phase 3B E2Eは2026-08-28にfresh Runner／アプリ状態から同じMicroG-RE `6.1.4`を2回ビルドしました。既存recipeのBuild A／Bは別Job・別RCE確認で`NONE`を表示・保存し、Runner SQLite v5のprivate pre/post lock hashは非lock modeのため`null`でした。両Jobは成功し、公式対A、公式対B、A対Bはすべて`MATCH`、trustは`Reproducible`です。Room v11はA／Bの`NONE` snapshot、同一toolchain／APK hash、各1,031 dependency recordを保存し、cold start後も状態を復元しました。

Phase 3C E2Eは2026-08-28に既存MicroG-RE `6.1.4` release recipeへepoch `1777393787`、Gradle `--no-build-cache`、`C.UTF-8`を一時設定して実行しました。Build A／Bは別Job・別RCE確認で成功し、両artifactは13,258,872 byte、同一SHA-256、各1,031 dependencyでした。公開Manifest v2とRoom v12に同じ3値を保存し、公式対A、公式対B、A対Bはすべて`MATCH`、trustは`Reproducible`でした。cold start復元後、一時recipe、Runner state、ADB reverse、debug packageを削除しています。既存recipeのcommitted stateはdeterminism未設定です。

Phase 3D E2Eは2026-08-29にfresh Runner SQLite v7／Android Room v13から同じMicroG-RE `6.1.4`を2回ビルドしました。Build A／Bは別Job、別workspace、別RCE確認、別scan reviewを通過しました。両scanは1,071 files／3,318,959 bytes、binary skip 0、symlink skip 1、121 findings、同一result SHA-256 `a2ac7ddd78fbb17fcbb716b6076459368bad404a5f985cf6cbcf24ca9fa5c92c`です。UIは各Jobの40 findingsと残り81件を表示し、Room v13は各121 findingsをordinal 0〜120で保存しました。両artifactは13,258,872 byte／同一SHA-256で、公式対A、公式対B、A対Bはすべて`MATCH`、trustは`Reproducible`です。force-stop後の`LaunchState: COLD`でも一覧とRoom v13証拠を復元しました。

## Phase 3 の Android 境界（3A〜3E実装）

Phase 3Eは[使い捨てprobe](../reprodroid-project/reports/2026/08/2026-08-30-phase-3e-feasibility.md)を経て[ADR-0017](../reprodroid-project/docs/adr/0017-docker-build-sandbox-feasibility.md)と[実装契約](../reprodroid-project/reports/2026/08/2026-08-30-phase-3e-contract.md)をAcceptedとしました。現行はRoom v14／public Manifest v3、strict sandbox取込み・永続化・Job／A／B表示に対応します。Androidは任意image／mount／Docker引数を指定せず、選択mode・実行監査・cleanupを区別します。旧rowはunavailableとして保持し、不正refresh・確定mode変更・schema downgradeを拒否します。sandbox evidenceはraw comparisonやtrustを変更しません。実A/B、HOST移行、mixed実APK比較、個別同意UIと失敗系の検証範囲は[最終受入記録](../reprodroid-project/reports/2026/08/2026-08-31-phase-3e-closeout.md)を参照してください。

live instrumentationは明示引数を必要とし、通常実行ではskipされます。継続E2Eでは`adb install -r`と直接`am instrument`を使い、Gradle connected testsの終了時uninstallによって製品Roomを失わないようにしてください。`LiveMixedSandboxComparisonTest`は既存成果物のread-only比較であり、新規buildやinstallを開始しません。

Phase 3 は protocol v2 の raw 3軸、APK comparator、trust truth table、公式 APK install / update policy を変更しません。3A では Runner が redaction / integrity 検査済みの Manifest projection を返し、Android は Job 単位で Room v10 に保存して同一 repository・同一 full SHA の dependency 差分を補助説明として表示します。取得失敗は session-only warning であり、`Reproducible`、`Different`、`Incomparable`、`Failed`、install policyを変えず、以前に保存した正常Manifestも削除しません。

3Bでは、Runner API v1の`effectiveBuild.dependencyPinning`をJobへ保存し、Build A / Bのlevelをcomparison snapshotとしてRoom v11へ記録します。既存rowとlegacy Runnerのfield欠落は`NONE`、未知値は拒否します。表示は`None`／`Lockfile checked`／`Lockfile checked · Gradle offline resolution`に限定し、完全なdependency coverageやnetwork isolationを断定しません。level差異はraw outcome、trust、update、install policyを変更しません。

3Cでは、Jobのeffective determinismと成功時Manifest evidenceをRoom v12へ別々に保存します。public v1は未設定値に限って受理し、public v2はrequired determinism object、非負epoch、既知locale、Job policy完全一致を要求します。UIはexact epoch／UTC、`Gradle build cache disabled by Runner`、`process locale C.UTF-8`だけを表示し、全timestamp／全cacheの制御や再現性を断定しません。Build A／B差異もadvisoryであり、raw outcome、trust、update、install policyを変更しません。

3Dでは、clone前のRCE同意を維持し、scan summaryを最初の同意画面ではなくJob／comparison detailに表示します。Androidはcompact Job summaryとbounded detailを相互検査し、full resolved commit、scanner version、canonical result digest、件数、path／line／column、review状態をRoom v13へ原子的に保存します。findingがあるJobは、表示digestに対するcheckbox確認後だけcontinueできます。Build A／Bは個別にreviewし、同じdigestでも承認を継承しません。scan取得失敗はsession-only warningとして既存正常証拠を保持し、terminal Job状態との不整合や不正responseはfail closedに扱います。finding内容はsafe／malicious verdictではなく、raw comparison、trust、update、signer、install policyを変更しません。

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

## Phase 4.5実装後の状態・対象外

Phase 3Eの固定profile、Phase 4.0基礎契約、4.1登録、4.2 history／storage、4.3 trusted toolchain、4.4 generic build／comparison client、特殊工程UI-R、4.5 scheduled release metadata discoveryを実装しています。4.5はRoom20に設定、app override、schedule state、check run、candidate、provider cooldown、notification outbox／dedup／representationを追加し、既存provider release／asset IDをcanonical decimal TEXTへ移行します。one-time unique WorkManager dispatchはRoomのnext eligibleを参照し、public GitHub metadataとtag full SHAだけを確認します。APK bytes、Runner、toolchain、build、comparison、trust、installへ副作用を接続していません。自動検証はdebug／release JVM test各147件、lintDebug、debug／release assemble、debug AndroidTest APK compileがPASSです。Room19→20 migration／WorkManager等のinstrumentationはcompile-onlyで、接続端末上の実行とAndroid 16製品経路は`NOT_RUN`です。[Phase 4 roadmap](../reprodroid-project/docs/design/phase-4-roadmap.md)、[4.5実装契約](../reprodroid-project/docs/design/phase-4-release-check-contract.md)、[ADR-0023](../reprodroid-project/docs/adr/0023-phase-4-scheduled-release-discovery-and-notifications.md)、[実装記録](../reprodroid-project/reports/2026/09/2026-09-06-phase-4-5-implementation.md)を正本とします。4.1の実public GitHub製品経路と4.2の追加証跡3件は独立して残る。Android + Docker公開source二projectのBuild A／B・比較E2Eは利用者指示により`NOT_RUN`であり、製品経路の成功・raw outcome・Reproducibleの証拠ではない。

計画範囲はpublic GitHub／Codebergのsource-onlyを含むGradle登録、汎用build／comparison、不足toolchain導入、history／手動cleanup／監査export、定期確認・通知、release HTTPS／pairing、暗号化backup／migration、Android／Runnerのlog export、署名releaseとlicense・privacy対応です。GitLabは将来候補。Play Store／F-Droid配布・適合性評価、Google Play services、共有用診断・自動送信は対象外です。

初期受入はAndroid 16・debug＋loopback HTTP＋ADB reverse、releaseはADB経由でもHTTPSを要求します。定期確認は1時間刻み・既定6時間または毎日指定時刻、releaseのみ／非従量制のみ／充電条件なしを既定とし、全体／app OFFと個別設定を持ちます。定期確認はmetadata・通知までで、既存15分周期のJob同期とは別です。承認済み比較の資源不足に限る自動増量・再試行は別契約・既定OFFとし、Job別scan reviewと全体budgetを維持します。今回コード・設定・環境・署名・公開は変更していません。

### 未実装の機能とPhase 3の対象外

- Build Environment Manifest／dependency差分からの自動的なbuild原因推定（3Aは観測値と差分だけを表示）
- DEX／native raw差異をsemantic一致で`Reproducible`へ昇格する判定
- ReproDroid鍵によるlocal comparison artifactの署名
- MicroG-RE `6.1.4`以外のrelease comparison profile
- private repository／GitHub token、GitHub Stars import、任意assetの自動選択・取得
- split APK、APKS、AAB
- root/Shizuku特権インストール
- silent install、自動アンインストール、署名検証回避
