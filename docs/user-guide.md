# User guide

## Root画面

アプリ一覧を起点に操作します。追加は一覧の追加ボタン、全体設定は右上の設定ボタンから開きます。通常の戻る操作に終了確認はありません。

設定は、表示、更新確認・通知、取得・インストール、配布元の認証、検証環境、データ管理、アプリ情報に分かれています。通知権限は「更新確認・通知」から必要なときに許可します。

## Appを登録する

追加画面へ公開GitHub／CodebergのリポジトリURLを入力します。リリース、タグ、IssueなどのページURLも使用でき、owner/repositoryより後のパス、クエリ、フラグメントは登録対象に含めません。

解析後、アプリ名と配布元、登録目的、導入元を確認して登録します。個別の選択肢は「登録設定」、リポジトリIDや探索結果は「解析の詳細」で開けます。登録内容を繰り返す別の確認画面はありません。

登録後は最新リリースのメタデータを確認してアプリ詳細へ進みます。確認に失敗しても登録は残り、詳細からリリース確認を再試行できます。登録とメタデータ確認では、APK取得、ビルド、比較、インストールを開始しません。

APKが1件の場合は取得画面で自動選択され、「公式APKをダウンロード」から取得できます。複数ある場合は対象を選んで取得します。自動選択だけではダウンロードを開始しません。

## Registered appsを整理する

- searchはapp名またはrepositoryを絞り込みます。
- group accordionはAll、Ungrouped、custom groupを表示します。
- custom groupは作成、rename、drag ordering、削除ができます。
- group削除時、所属appはUngroupedへ移り、app/historyは残ります。
- tracking停止とAndroid packageのuninstallは別操作です。

## Releaseを確認する

アプリ詳細は、保存されたリリース情報、導入状態、検証状態を表示します。未検査の候補には「APKを取得」と表示し、タグ名だけで「更新あり」とは判定しません。検査済みAPKと同じリリース観測で、導入済みのversionCodeが同じか新しければ、候補が残っていても更新を促しません。

詳細への進入・復帰時には保存済みAPKから導入状態を再確認します。「リリースを確認」でメタデータ確認を明示的に再実行できます。検証は更新の有無と独立した入口から開きます。全体設定・アプリ設定では次を設定できます。

- 1/2/3/4/5/6/12時間、1/3/5/7日interval、24時間dialで選ぶ指定時刻、または更新しない
- network/battery policy
- charging-only
- release-check notification
- appごとのoverride

scheduled checkはbounded release metadataを取得し、release observationとhistoryを更新して通知します。APK download、toolchain install、Runner Job、build、comparison、trust変更、installは各画面の明示操作から開始します。

設定 → 更新確認・通知では、通知権限とAndroidのバックグラウンド制限を確認できます。background operationが制限されている場合はsystem settingsを開いて変更します。既定のbattery optimizationではAndroidが実行を遅らせる場合があり、指定時刻は厳密な実行時刻ではありません。

## 公式APKを取得する

アプリ詳細から取得・インストール画面を開き、対象APKとサイズを確認します。配布元の詳細やハッシュは技術情報から参照できます。download後、ReproDroidはSHA-256、package、version、signerをAPK bytesから検査します。

provider metadata、download response、computed digest、APK inspectionを分けて保存します。download成功だけでは比較またはinstall可能とは判定しません。

## Build configurationを保存する

アプリ設定、または検証画面の「ビルド設定を開く」からbuild root、module、variant、tasks、Java、Gradle、compile SDK、build tools、任意NDK/CMakeを入力します。保存するとcanonical JSONとdigestを持つrevisionになります。保存操作だけではRunner Jobを作成しません。

## Toolchainを導入する

設定 → 検証環境 → Managed build toolchainsでcatalogとinventoryを確認します。planにはartifact digest、download/reservation size、licenseが表示されます。内容を確認してinstallし、`INSTALLED`まで進んだことを確認します。

## Jobsとsource scan

アプリ詳細 → 検証では、そのアプリのビルド・比較を進められます。ジョブ一覧は設定 → 検証環境 → Runner jobsから開きます。

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

設定 → 取得・インストールでShizuku／Suiを選び、ReproDroidへの権限を付与できます。特権経路はsigner一致更新または`Reproducible` artifactだけでOEM installer確認を省略します。署名不一致、未比較、または再現性警告が残るAPKはシステムinstallerへ戻ります。これはbackground自動更新を有効にしません。

「Google Playをinstallerとして記録」は、installer-of-recordを参照するアプリ向けの任意互換設定です。実際のAPK取得元をGoogle Playへ変更する機能ではありません。

ReproDroidはinstaller launch、cancel、platform rejection、failure、successを別々に記録します。official APKとlocal buildのsignerが異なる場合、既存official appをlocal APKで通常updateできません。

## Storageとcleanup

Settings → Data managementでAndroid storage summary、cleanup preview、complete deletionを使用します。Runner storageは設定 → 検証環境 → Runner storageで確認します。

cleanupはcurrent、pending review、hold、参照中resourceを保護します。preview後にresourceが変化した場合は新しいpreviewを作ります。partial failureを完全成功として表示しません。

## Export

- Audit export: 登録、release、build、comparison、install等のbounded structured record
- Android log export: ReproDroid自身のoperational event
- Runner log export: Runner PC上のlocal CLI操作

exportはbackup/restoreではありません。Job stdout/stderrにはbuild scriptが出力したpathやsecretが含まれる可能性があるため、外部共有前に確認します。

## Tracking停止と完全削除

tracking停止後もhistoryは残ります。完全削除はpreviewで対象とblockerを確認し、関連operationが終了してから実行します。Android packageのuninstallは別のsystem operationです。
