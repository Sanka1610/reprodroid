# User guide

## Root画面

ReproDroidには3つのroot pageがあります。

- Registered apps: 登録済みapp、group、search、filter
- Add app: public repositoryの登録
- Settings: appearance、update、permission、notification、Runner、storage、export、license

root navigationと横swipeは同じ選択状態を共有します。Add appのanalysis/options/confirmation中はnested flowとして扱います。

## Appを登録する

Add appへpublic GitHub/Codeberg repository URLを入力します。リリース、タグ、IssueなどのページURLも使用でき、owner/repositoryより後のパス、クエリ、フラグメントは登録対象に含めません。analysisでprovider identity、repository、release条件を確認し、management mode、installation source、variant、ABIを選択します。

登録後は最新Releaseのmetadataだけを確認し、結果を登録完了画面に表示します。取得モードでは公式APKの取得・installへ進むか、検証モードでは再現性検証を開始するかを選べます。「後で」を選んだ場合も、Informationの状態行に必要なactionが残ります。metadata確認だけではAPK download、Runner Job、build、comparison、installを開始しません。

APKが1件の場合は取得画面で自動選択され、「公式APKをダウンロード」から取得できます。複数ある場合は対象を選んで取得します。将来のリリースにも同名APKを使用するかの確認項目は表示しません。自動選択だけではダウンロードを開始しません。

## Registered appsを整理する

- searchはapp名またはrepositoryを絞り込みます。
- group accordionはAll、Ungrouped、custom groupを表示します。
- custom groupは作成、rename、drag ordering、削除ができます。
- group削除時、所属appはUngroupedへ移り、app/historyは残ります。
- tracking停止とAndroid packageのuninstallは別操作です。

## Releaseを確認する

Informationは、登録時またはscheduled checkで保存されたRelease metadataを表示します。インストール可能なReleaseがある場合だけ、状態行の右側に取得または検証のactionを表示します。検査済みAPKと同じリリース観測で、インストール済みのversionCodeが同じか新しい場合は、候補が残っていても更新actionを表示しません。詳細画面を開いたときと復帰時には保存済みAPKを使ってインストール状態を再確認します。通常のInformation画面には独立した更新確認actionを置きません。global/per-app policyでは次を設定できます。

- 1/2/3/4/5/6/12時間、1/3/5/7日interval、24時間dialで選ぶ指定時刻、または更新しない
- network/battery policy
- charging-only
- release-check notification
- appごとのoverride

scheduled checkはbounded release metadataを取得し、release observationとhistoryを更新して通知します。APK download、toolchain install、Runner Job、build、comparison、trust変更、installは各画面の明示操作から開始します。

SettingsのPermissionsではnotification permissionとAndroidのbackground restriction状態を確認できます。background operationが制限されている場合はsystem settingsを開いて変更します。既定のbattery optimizationではAndroidが実行を遅らせる場合があり、指定時刻は厳密な実行時刻ではありません。

## 公式APKを取得する

Informationの状態行から取得フローを開き、release、tag、asset名を確認します。詳細なprovider情報やdigestはOpen technical detailsで確認できます。download後、ReproDroidはSHA-256、package、version、signerをAPK bytesから検査します。

provider metadata、download response、computed digest、APK inspectionを分けて保存します。download成功だけでは比較またはinstall可能とは判定しません。

## Build configurationを保存する

app settingsまたはOpen technical detailsでbuild root、module、variant、tasks、Java、Gradle、compile SDK、build tools、任意NDK/CMakeを入力します。保存するとcanonical JSONとdigestを持つrevisionになります。保存操作だけではRunner Jobを作成しません。

## Toolchainを導入する

Settings → Runner → Managed build toolchainsでcatalogとinventoryを確認します。planにはartifact digest、download/reservation size、licenseが表示されます。内容を確認してinstallし、`INSTALLED`まで進んだことを確認します。

## Jobsとsource scan

Settings → Runner → Runner jobsまたはOpen technical detailsからJobを開きます。

1. repositoryとresolved full commitを確認します。
2. recipe/configuration、sandbox、toolchainを確認します。
3. Gradle任意コード実行のriskを確認します。
4. source scan findingがある場合はdetector、path、bounded excerpt、scan digestを確認します。
5. Build AとBuild Bを別々にconfirmします。

一方の確認は他方へ継承されません。cancel、failure、interrupted、successは別stateとして保存されます。

## 比較する

Comparison evidenceは三軸のraw resultを表示します。

- Official vs Build A
- Official vs Build B
- Build A vs Build B

補助証拠にはAPK entry、DEX、native library、Manifest、resource、dependency、determinism、source scan、sandboxがあります。判定条件は[Reproducibility](reproducibility.md)を参照してください。

## Install／updateする

既定では、eligible artifactのInstallを選ぶとAndroid標準`PackageInstaller`が開きます。Androidがpackage、version、signer lineage、利用者確認を処理します。

Settings → External tool integrationsでShizuku／Suiを選び、ReproDroidへの権限を付与できます。特権経路はsigner一致更新または`Reproducible` artifactだけでOEM installer確認を省略します。署名不一致、未比較、または再現性警告が残るAPKはシステムinstallerへ戻ります。これはbackground自動更新を有効にしません。

「Google Playをinstallerとして記録」は、installer-of-recordを参照するアプリ向けの任意互換設定です。実際のAPK取得元をGoogle Playへ変更する機能ではありません。

ReproDroidはinstaller launch、cancel、platform rejection、failure、successを別々に記録します。official APKとlocal buildのsignerが異なる場合、既存official appをlocal APKで通常updateできません。

## Storageとcleanup

Settings → Data managementでAndroid storage summary、cleanup preview、complete deletionを使用します。Runner storageはSettings → Runner → Runner storageで確認します。

cleanupはcurrent、pending review、hold、参照中resourceを保護します。preview後にresourceが変化した場合は新しいpreviewを作ります。partial failureを完全成功として表示しません。

## Export

- Audit export: 登録、release、build、comparison、install等のbounded structured record
- Android log export: ReproDroid自身のoperational event
- Runner log export: Runner PC上のlocal CLI操作

exportはbackup/restoreではありません。Job stdout/stderrにはbuild scriptが出力したpathやsecretが含まれる可能性があるため、外部共有前に確認します。

## Tracking停止と完全削除

tracking停止後もhistoryは残ります。完全削除はpreviewで対象とblockerを確認し、関連operationが終了してから実行します。Android packageのuninstallは別のsystem operationです。
