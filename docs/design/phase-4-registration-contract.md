# Phase 4.1: GitHub登録・静的探索・build設定の実装契約

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented on Android; automated boundary acceptance passed; live public GitHub product path remains partial
- Date: 2026-09-01
- Basis: [ADR-0018](../adr/0018-phase-4-operational-foundation.md), [4.0共通契約](phase-4-foundation-contract.md)
- Scope: Phase 4.1 source registration and discovery
- Operational gate: cleanup、証拠保存、commit、公開はそれぞれ独立して承認・記録する

## 1. 到達点と変更範囲

Android単体でpublic GitHub repositoryを登録し、full SHAに固定したtreeからGradle候補を静的に検出し、アプリ単位の構造化build設定を保存・再表示できるようにする。release、APK、package名、Runner接続を登録条件にしない。

登録、source再検査、設定保存からAPK download、Runner Job、RCE確認、clone、scan、動的Gradle、toolchain導入、comparison、installを開始しない。Gradle候補検出をAndroid project／APK生成可能／build成功と表示しない。4.4の汎用実行に必要なdomain境界を用意するが、汎用実行の受入を4.1完了へ読み替えない。

対象はAndroid code、Room14→15 migration、UI、provider client、自動test・Android16受入と対応文書。Runner code／SQLite8／現行API v1／Manifest／raw comparatorはこの工程で変更しない。新Job作成はPhase 4の互換性gateで止め、固定MicroG profileやSIMULATEDをv1へfallbackさせない。4.1には対応v2 Runnerがまだないため新Jobは利用不可と表示する。既存historyは保持し、保留Jobを勝手にconfirm／retryしない。

## 2. 登録フローとUI

1. URLを入力し「repositoryを確認」でpublic repository metadataを取得する。
2. provider repository IDを確定した後、default branchをfull commit SHAへ解決し、静的tree探索の結果を表示する。
3. 「登録」でmetadata・source探索結果・初期設定draftを同一Room transactionへ保存する。登録完了後にAPKを自動取得しない。
4. 候補なし、空repository、探索未完了でもrepository identityを確認できた場合は、状態を明示して管理対象として保存できる。repository自体の404／拒否／通信失敗では確認済み登録を作らず入力draftを残す。
5. 登録済みの再確認が失敗しても登録や以前の成功snapshotを消さず、最新attemptの失敗を別に保存する。

release metadataはこの登録経路では取得しない。未照会の対象を「releaseが存在しない」と断定せず、「参照APK未選択／release未確認」と表示する。source-only projectを登録できることと、release不存在を確認したことを区別する。取得操作は既存の利用者操作へ分離し、管理モードやinstall sourceの設定保存だけで実行しない。

一覧・detailは候補数、探索完了／未完了、対象SHA、設定の不足項目を表示する。package未確定ではrepositoryの表示名と汎用iconを使い、他packageのicon・signerを推測しない。既存比較がある対象では、その結果をsource探索や設定の状態で上書きしない。

previewにはrequest generation IDと正規化URLをbindする。URL変更・画面離脱で旧requestをcancelし、cancel不能で遅れて返った結果もgeneration不一致なら捨てる。登録直前にもURL／generation／repository IDを照合する。連打・二重coroutineは同一登録transactionへ収束し、画面のisLoadingだけを重複防止にしない。

## 3. URLとrepository identity

- 利用者入力はUTF-8で4 KiB以下。前後の通常空白はUIで除けるが、NUL／制御文字／不正Unicodeを受理しない。
- https://github.com/{owner}/{repository}だけを受理し、末尾slashと.gitを正規化する。userinfo、query、fragment、明示port、空segment、encoded separator、dot segment、余分なpathを拒否する。
- provider=GITHUB、instance=github.com。owner/nameはlocator、identityはprovider／instance／providerRepositoryId。provider IDは正の64-bit整数を丸めず10進文字列へ変換する。
- API応答のprivate=false、repository ID、owner.login、name、full_name、html_urlを相互検証する。公開状態が欠落・不明ならpublicへdefaultしない。
- 通信originはhttps://api.github.com固定。応答中のurl／clone_urlを任意取得先に使わない。redirectは追跡せず、repository移転は新locatorの明示再確認を要求する。
- 同じURLでprovider IDが変わったら既存登録へ黙って再bindしない。同じIDへの明示的なlocator更新でも過去source／comparisonの参照を変更しない。
- 同じrepositoryの通常登録はPRIMARY slotで一意。同じrepositoryから独立packageを管理する場合は「別の管理対象」を明示し、別UUID slot／registeredAppIdを作る。package名がまだなくても管理単位を分けられる。
- 旧登録はLEGACY_UNRESOLVEDのbindingを作り、初回の明示確認で同じ登録IDへidentityを結び付ける。migration中にnetworkへ接続せず、repository IDを捏造しない。

## 4. 静的探索の取得手順

provider API version headerは既存と同じ2022-11-28を明示し、この工程で自動更新しない。endpointと解釈の対応をMockEngineおよび実public APIで検証する。

1. GET /repos/{owner}/{repo}でID／public状態／default_branchを確認する。
2. default branchのgit refを取得し、object.type=commit、full SHAを検証する。branch名はURL segmentとしてencodeし、slashを含むbranchを文字列連結で壊さない。
3. GET /repos/{owner}/{repo}/git/commits/{fullSha}を読み、応答shaの一致とtree.shaを検証する。
4. root treeからGET /repos/{owner}/{repo}/git/trees/{treeSha}を非再帰で辿る。recursive parameterを送らない。応答shaは要求tree SHAと一致しなければ不正とする。
5. 取得時のbranch移動は別commitのtreeを混ぜる理由にしない。最初に解決したcommit→root tree→child tree SHAの関係だけを辿る。
6. 同一tree SHAを別pathで参照する場合は取得bytesを再利用できるが、pathごとのentry走査・深さ・候補数はそれぞれ計上する。祖先treeへの循環は不正として停止する。

候補はregular blob（mode100644／100755かつtype=blob）のbuild.gradle、build.gradle.kts、settings.gradle、settings.gradle.kts。parent pathをbuild root候補とし、Groovy／Kotlin DSLを記録する。settingsの存在は候補根拠であり、その内容を実行してmodule一覧を取得しない。build fileがないsettings-onlyも候補として扱う。

mode120000のsymlink、mode160000のsubmodule、.git／.gradle配下は追跡せず、除外数と範囲を記録する。submodule内を含む全projectのabsenceを断定しない。mode／typeの不整合、重複path、不正な単一entry名、path confinement違反は不正metadata。一般のbuildディレクトリをcacheと決めつけて除外しない。

blob本文を取得しないので、plugin適用、Android／library種別、Gradle／AGP／JDK／SDK版、module／variant／taskの存在はここでは未確認。候補なしを「非Androidである」とは表示しない。wrapper properties等からの追加静的推定を行う場合は別の上限付き契約を追加してからとし、任意source解析を暗黙に増やさない。

### 上限と結果状態

| 項目 | 制限・計数 |
|---|---|
| 1 metadata応答 | 展開後実受信8 MiB。Content-Length以外も検査 |
| 探索全体 | 50,000 entry、深さ32、100 HTTP request、展開後32 MiB、開始から120秒 |
| request数／bytes | repository／ref／commit／treeとerror応答も合計に算入 |
| path | UTF-8で1 KiB。rootは.、depthはroot0／直下1 |
| JSON | 解析中depth32、重複key／不正UTF-8／overflow／必須欠落を拒否。tree配列50,000、文字列はfieldの上限 |
| per-request timeout | connect5秒、socket15秒。全体120秒deadlineを超えるretryなし |
| parser | 応答を無制限body()へ渡さず、bounded受信＋構造・型検査。圧縮を受理するなら展開後にも上限 |
| 部分結果 | 確認済み候補のみ、検査範囲・中断理由と一緒に表示。唯一／最新の自動選択に使わない |

50,000件等にちょうど達して走査すべき対象が残らない場合だけCOMPLETEにできる。次のentry／requestが必要ならINCOMPLETE。truncated=trueは非再帰応答でもINCOMPLETEであり、部分treeから「候補なし」を確定しない。未知truncatedはfalseへdefaultしない。

discovery stateはRESOLVING／SCANNING_TREE／COMPLETE／INCOMPLETE／FAILED／CANCELLED／INTERRUPTED。candidate presenceは別軸とし、COMPLETEでも0件なら「検査範囲にGradle候補なし」。RUNNINGをcold startで成功へ変えずINTERRUPTEDにする。新規previewの未保存結果は復旧対象にしない。

failure reasonはNOT_FOUND_OR_NOT_PUBLIC、ACCESS_DENIED、RATE_LIMITED、NETWORK_ERROR、EMPTY_REPOSITORY、INVALID_METADATA、LIMIT_ENTRIES／DEPTH／REQUESTS／BYTES／TIME、PROVIDER_TRUNCATED等を分離する。404から「repository不存在」と「private」を判別できない場合は両者を含む表現にする。403を一律rateとせず、rate header／Retry-After等の根拠を要する。4.1は利用者操作だけで、自動backoff再試行・定期scheduleは4.5まで追加しない。

## 5. Build settings schema1

保存対象は任意source codeではなく以下の構造化要求。未入力nullを許可するdraftと、構文上不足のないCONFIGUREDを分ける。CONFIGUREDもRunner実行可能、toolchain導入済み、task存在確認済みを意味しない。4.4のExecutionSnapshotとは別モデルである。

| field | 制約 |
|---|---|
| schemaVersion | 整数1 |
| buildRoot | nullまたは.／相対path、UTF-8 1 KiB以下。absolute／..／backslash／NUL／空segment拒否 |
| modulePath | nullまたは:／:app／:feature:demo形式。segmentは[A-Za-z_][A-Za-z0-9_.-]{0,127}、全体1 KiB以下 |
| variant | nullまたは[A-Za-z_][A-Za-z0-9_]{0,127}。存在は未検証 |
| tasks | 順序付き0〜32件、各1 KiB以下。rootからの:task／:module:task形式、上と同じsegment文法 |
| javaMajor | nullまたは1〜255の整数。候補値の保存で導入可能とはしない |
| gradleVersion | nullまたは128 ASCII bytes以下の[0-9]+([.][0-9]+){1,2}(-rc-[0-9]+\|-milestone-[0-9]+)?。latest、範囲、snapshot等の可変指定拒否 |
| compileSdk | nullまたは1〜10000のAPI level整数。preview名を数字へ推測変換しない |
| buildToolsVersion | nullまたは[0-9]+([.][0-9]+){1,3}(-[A-Za-z0-9]+([.-][A-Za-z0-9]+)*)?、128 ASCII bytes以下 |
| ndkVersion／cmakeVersion | optional nullまたはbuildToolsVersionと同じ文法。不要・未確定は根拠なしにrequiredへ変換しない |

shell quote、whitespace区切りのcommand列、オプション（--等）、@argfile、;、$、改行をtaskで受理しない。taskは1要素1引数として後続Runnerへ渡す契約であり、shellで再解釈しない。tasksは順序を変えず、重複を検出して利用者へ訂正を求める。buildRoot以外のfilesystem path、download URL、image／mount／env mapは設定schemaに含めない。

CONFIGUREDの必須項目はbuildRoot、modulePath、variant、非空tasks、javaMajor、gradleVersion、compileSdk、buildToolsVersion。library等の非APK用途で未入力があってもdraft保存可能。候補が1件かつ探索COMPLETEならbuildRootを候補値としてUIへ補完できるが、module／variant／taskやtoolchainを捏造しない。非対応文法をsource本文の編集で自動回避しない。

content hashはschemaVersionと上記全field（nullを含む）をJCSでcanonicalizeしSHA-256。app ID、revision、時刻、探索結果、表示名はhash対象外。revisionはappごとの正の64-bit整数、保存時にoptimistic concurrencyを確認する。同一payloadの再保存は同じrevisionを返せるが、異なるpayloadは必ず新revision。旧revisionをUPDATEしない。

4.1のJCS実装をio.github.erdtman:java-json-canonicalization:1.1に固定する。配布物は下記のとおり照合し、Android依存へ追加した。JVM numeric／UTF-16 property順序vectorとAndroid側の設定保存を実行し、保存canonical JSONとSHA-256の不一致を拒否する。厳密な入力検査を前段に置き、canonicalizerを安全なJSON受信parserの代替にしない。

| 公開Maven配布物 | bytes | SHA-256 |
|---|---:|---|
| java-json-canonicalization-1.1.pom | 6378 | 37114938a89def00596ff5541b794abc6be626b4c73e988d5a5bd457a3f38477 |
| java-json-canonicalization-1.1.jar | 28379 | ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691 |
| java-json-canonicalization-1.1-sources.jar | 31239 | 98de9b4ee9e220da0a8b4338f0612924c36612d060ca71064a9f56422e7e2068 |

公開POMはApache-2.0、dependencyはtest scopeのJUnit4.12のみ。現行Android側JUnit版をこれへ変更しない。jarにはlicense本文が同梱されていないため、4.8の配布同梱対象へ記録する。digest一致は真正性やlibraryの安全性の証明ではない。

## 6. Room15への変更契約

Room15はこの工程に割り当てる次期schema番号。現行Room14を変更済みとは扱わず、code実装・generated schema／migration受入後にだけcompatibilityへ反映する。

既存registered_appsのURL unique indexを同名non-unique indexへ変更し、既存row／primary keyは保持する。既存getRegisteredAppByCanonicalUrlの単一結果仮定を解消し、同URL複数対象で先頭を任意選択しない。release／comparison／trust／Job／Manifest／scanの既存tableには意味変更を加えない。

| 新table | primary key／参照 | 主要fieldと制約 |
|---|---|---|
| app_repository_bindings | registeredAppId、registered_appsへのFK CASCADE | provider、instance、providerRepositoryId nullable、identityStatus、registrationSlot、verifiedAt nullable |
| source_discoveries | discoveryId UUID、registeredAppIdへのFK CASCADE | repository identity snapshot、requested branch、resolvedCommitSha／rootTreeSha nullable、state、reason、entry/request/byte/depth/candidate counts、excluded counts、startedAt／finishedAt |
| gradle_candidates | (discoveryId, relativePath)、source_discoveriesへのFK CASCADE | relativePath、buildRoot、fileKind（4 basename）、blobSha、mode、dsl |
| app_build_configurations | (registeredAppId, revision)、registered_appsへのFK CASCADE | schemaVersion、canonicalJson、contentSha256、validationState、createdAt |
| app_source_heads | registeredAppId、registered_appsへのFK CASCADE | latestDiscoveryId nullable、selectedConfigurationRevision nullable、updatedAt |

bindingは(provider, instance, providerRepositoryId, registrationSlot)にunique indexを持つ。PRIMARY slotで既存と衝突した登録はその対象を案内し、同意なく別slotへ逃がさない。nullの旧provider IDをNULL文字列や0へ変換しない。明示別slotはUUIDで、表示名から生成しない。

source_headsの参照先は同じregisteredAppIdでなければならない。DAO transaction内の検査と、discovery側／configuration側のapp込みunique keyを参照する複合FKで制約する。参照削除はCASCADEで黙ってcurrentを消さずRESTRICTし、4.2でretention intentとともに更新する。候補行はimmutableなterminal discoveryの内容と整合させる。

旧登録にはGITHUB_RELEASES→GITHUBのbindingを付け、instance=github.com、providerRepositoryId=null、identityStatus=LEGACY_UNRESOLVED、registrationSlot=PRIMARY。未知providerや不正既存URLは元rowを保持してLEGACY_INVALIDとし、新たな照合を要求する。fake source discovery／設定revision／新しい実行権限は生成しない。

旧app ID、release／asset ID、comparison ID、protocolとraw outcome、install attempt、既存setting・trust、sandbox evidenceを全値比較する。新tableのRoom生成DDL／index／FKは実装時に実schemaで確認し、単にuser_version=15だけで成功としない。

### Migration前snapshotと失敗復旧

4.1のmigrationにも計画合意14.3節のsnapshot要件を適用する。4.2の製品storage UIや4.8のportable backupが未実装であることを理由に省略しない。

通常repository／WorkerへDBを公開する前にmigration gateとprocess間lockを取得し、旧DB versionを検査する。Room未起動・他writerなしを確認してWAL checkpointを行い、busyや未checkpoint frameがあれば停止する。handleを閉じたquiescent DBからprivate snapshotを作り、SHA-256、integrity_check、user_version、room_master_tableを確認する。稼働中のDB単純copyでは代用しない。

snapshotはappのbackup除外領域に置く。DB／WAL／journalの合計を上界としてcopy／検証用の一時容量を見積り、Android4 GiB内の現在使用量と実空き容量を確認する。回復監査用16 MiBを残す。容量不足ではmigrationを開始せず、他の履歴／APKを自動削除しない。4.2では同じ検査を共通reservationへ接続する。

migration失敗・未知schema・snapshot破損時は通常起動を止め、元DBとsnapshotを保持し、初期化・destructive fallback・自動rollbackをしない。既存workerが旧DBへ書き込めないこと、kill時のcheckpoint／copy／migration cut pointからの復旧を試験する。snapshot検査に失敗した状態で4.1完了にしない。

## 7. 実装接点と順序

1. bounded provider metadata reader、GitHub repository／ref／commit／tree DTO、strict URL／identity／path検証を追加する。既存release clientのbody()を登録へ流用しない。
2. static discovery、設定validator／hash、immutableなdomainを追加する。HTTP client／clock／deadlineを注入可能にして境界を試験する。
3. Room15 migration、sidecar entity／DAO transaction、migration guardを実装する。
4. ManagedAppRepositoryにpreviewRepository／registerRepository／refreshSourceDiscovery／saveBuildConfigurationを追加し、downloadと独立させる。
5. ManagedAppsViewModelのpreview generation、登録連打、configuration revision競合を制御する。AddAppScreen、一覧／detail／設定画面を接続する。
6. 新Job create／retry／実行開始の操作に互換性gateを適用する。既存JobSyncWorkerは登録を起点に新しいJobを作らず、source-onlyのlocal閲覧をRunner状態から独立させる。
7. 自動test・migration・Android16製品経路と回帰を受け入れ、文書・確定HEAD・compatibilityを同期する。

空Runner URLは現在のclient constructorで許容され、API操作時に失敗する実装である。空URLだけを根拠に起動crashと断定しない。不正な非空URLや接続不能でlocal登録が巻き添え停止しないことを受入で確認する。

## 8. Acceptance ledger

2026-09-01のAndroid実装と自動試験による状態。`PASS`は表の必須結果を直接確認した項目、`PARTIAL`は実装済みでも製品経路の直接確認が不足する項目である。詳細なコマンド、件数、実DB fixture、外部rate limitは非公開の実装記録へ保持し、公開時点の状態は[Current status](../status/current.md)で管理する。

| ID | 試験 | 必須結果 | 状態 |
|---|---|---|---|
| A4.1-01 | Groovy／Kotlin DSL、root／subdirectory／multi-module tree | SHA固定の候補検出、script／blob本文の実行・取得なし | PASS |
| A4.1-02 | releaseなし／APKなし／Java library／settings-only | 登録・cold start成功、APK／build可能性を捏造しない | PARTIAL |
| A4.1-03 | 完全探索0候補、cacheのみ、symlink、submodule | absenceと除外範囲を分離、追跡なし | PASS |
| A4.1-04 | 50,000／50,001、depth32／33、100／101 request、32 MiB、120秒 | 境界で完了と未完了を区別 | PASS |
| A4.1-05 | 8 MiB応答／圧縮／未知length／重複key／invalid UTF-8／overflow | 受信・解析中制限、不正保存なし | PASS |
| A4.1-06 | truncated、SHA不一致、循環、path重複／escape、mode矛盾 | 部分候補を唯一とせず、不正metadataを拒否 | PASS |
| A4.1-07 | 探索中のbranch移動／repository ID変更 | tree混在・黙った再bindなし | PASS |
| A4.1-08 | 404／private／403権限／rate／offline／cancel | 原因表示、既存登録と成功snapshot保持 | PASS |
| A4.1-09 | URL変更後に旧preview完了、連打、同時登録 | stale登録なし、PRIMARY重複なし | PASS |
| A4.1-10 | 同repoの明示別package管理、別provider ID | 独立app IDと設定、URLで任意対象を選ばない | PASS |
| A4.1-11 | 相対path／task injection／invalid version／最大数 | 保存前拒否、shell／command実行なし | PASS |
| A4.1-12 | 設定未入力／編集／同内容再送／旧revision保存 | draft保持、immutable revision、競合拒否 | PASS |
| A4.1-13 | JCS既知vector／Unicode sort／null／hash改変 | JVMとAndroidのcanonical bytes同一、不正入力拒否 | PASS |
| A4.1-14 | Room14→15と全1→15 chain、実3E snapshot | ID／current／raw／trust／settings全値保持、FK／index整合 | PASS |
| A4.1-15 | migration容量不足／busy WAL／snapshot破損／各cut point kill／未知schema | 元DBと整合snapshot保護、初期化・worker書込みなし | PASS |
| A4.1-16 | 対応Runnerなし／空URL／不正URL／v1応答 | local登録維持、新Job／retry／confirmのv1 fallbackなし | PASS |
| A4.1-17 | 登録・再探索・設定保存の副作用監視 | APK downloader／Job create／Gradle／installer call0 | PASS |
| A4.1-18 | 既存MicroG履歴、raw3軸・unknown evidence・signer policy | 静的検出・設定でtrust／installへ昇格しない | PASS |

製品経路受入はAndroid16上でRunnerを停止した状態からURL入力→確認→登録→設定保存→cold startを確認する。MockEngineの境界試験と実public GitHub経路を区別して記録する。非MicroGの代表repositoryは受入時にpublic状態・full SHA・treeを再確認して固定する。4.4の2件実build／comparisonの受入は別途必要。

## 9. 参照と未実施

一次資料: [GitHub repository metadata](https://docs.github.com/en/rest/repos/repos#get-a-repository)、[git commit](https://docs.github.com/en/rest/git/commits#get-a-commit)、[git tree](https://docs.github.com/en/rest/git/trees#get-a-tree)、[Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions)、[SQLite snapshotの整合性](https://sqlite.org/backup.html)、[JCS library](https://github.com/erdtman/java-json-canonicalization)、[公開POM](https://repo.maven.apache.org/maven2/io/github/erdtman/java-json-canonicalization/1.1/java-json-canonicalization-1.1.pom)。

Android commit `48174a77b7050bcbde0de274b767fe2c88e13a10`までにRoom15、JCS依存、provider client、repository／UI、migration gate、自動testを実装した。JVM 108件、Android 16の最終run 43件（failure 0、明示opt-in skip 7）、Phase 3E実Room14 snapshot移行、migrationの実プロセスkill 3 cut point、lint、debug／release assembleは成功した。ledgerはPASS 17／PARTIAL 1であり、残る`PARTIAL`は未認証rate limitで完走できていない実public GitHubのURL→登録→設定→cold startだけである。この製品経路を成功確認するまで4.1全体を完了扱いにしない。
