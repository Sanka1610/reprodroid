# User guide

## Root画面

ReproDroidには3つのroot pageがあります。

- Registered apps: 登録済みapp、group、search、filter
- Add app: public repositoryの登録
- Settings: appearance、update、notification、Runner、storage、export、license

root navigationと横swipeは同じ選択状態を共有します。Add appのanalysis/options/confirmation中はnested flowとして扱います。

## Appを登録する

Add appへpublic GitHub/Codeberg repository URLを入力します。analysisでprovider identity、repository、release条件を確認し、management mode、installation source、variant、ABIを選択します。

複数APK assetをfilenameだけで自動確定できない場合は、Information → Open technical detailsからexact assetを選択します。選択前にAPKをdownloadしません。

## Registered appsを整理する

- searchはapp名またはrepositoryを絞り込みます。
- group accordionはAll、Ungrouped、custom groupを表示します。
- custom groupは作成、rename、drag ordering、削除ができます。
- group削除時、所属appはUngroupedへ移り、app/historyは残ります。
- tracking停止とAndroid packageのuninstallは別操作です。

## Releaseを確認する

InformationのRelease trackingからCheck for updatesを実行します。global/per-app policyでは次を設定できます。

- 1〜24時間intervalまたは指定時刻
- network/battery policy
- charging-only
- release-check notification
- appごとのoverride

scheduled checkはbounded release metadataを取得し、release observationとhistoryを更新して通知します。APK download、toolchain install、Runner Job、build、comparison、trust変更、installは各画面の明示操作から開始します。

## 公式APKを取得する

Open technical detailsでrelease、tag、asset名、provider sizeを確認します。download後、ReproDroidはSHA-256、package、version、signerをAPK bytesから検査します。

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

eligible artifactのInstallを選ぶとAndroid標準`PackageInstaller`が開きます。Androidがpackage、version、signer lineage、利用者確認を処理します。

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
