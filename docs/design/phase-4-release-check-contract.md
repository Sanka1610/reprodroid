# Phase 4.5: scheduled release discovery and notification contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Android Room20 implementation complete; automated JVM／lint／assemble／AndroidTest APK compile PASS, instrumentation `COMPILE_ONLY`, Android 16 product acceptance `NOT_RUN`
- Date: 2026-09-06
- Decision: [ADR-0023](../adr/0023-phase-4-scheduled-release-discovery-and-notifications.md)
- Preparation and implementation evidence: 非公開の実装記録で管理
- Public verification status: [Current status](../status/current.md)
- Previous contracts: [4.0 foundation](phase-4-foundation-contract.md), [UI-R contract](ui-r-contract.md)

## 1. この文書の扱い

この契約は、4.5のcode着手前に未割当だったschedule、candidate、notification outbox、provider cooldown、Room20 migration、failure state、retention、受入ledgerを固定した。Android implementationと自動検証の結果は工程reportへ分離し、JVM unitのPASSやinstrumentation sourceのcompile成功をAndroid 16製品経路の`PASS`へ変更しない。

4.5はAndroid単体のpublic GitHub release metadata確認、候補保存、通知だけを扱う。Runner API／SQLite／capability、APK download、toolchain、build、comparison、trust、installを変更・起動しない。GitHub Stars importはprovider queue／cooldownの受入後に行う独立follow-upであり、4.5本体の完了条件に含めない。

## 2. Authority and side-effect boundary

Androidはschedule、provider metadata、check result、candidate、outbox、notification ID、muteを所有する。Runnerは関与せず、Runner停止、ADB reverseなし、Runner API設定なしでも確認・候補保存が成立しなければならない。

`ReleaseCheckWorker`と利用者が明示する`Check metadata now`が許可される処理は次だけである。

1. 永続設定、tracking state、due time、provider cooldownを読む。
2. OSが報告するnetwork／battery状態を評価する。
3. public GitHub APIからboundedなrelease／tag metadataを取得する。
4. provider response、tag full SHA、asset metadataを検証する。
5. check result、candidate、notification intent、次回予定をRoomへ保存する。
6. 保存済みoutboxに従ってnotificationを投稿または抑止する。

次を自動開始しない。

- repository tree／source discovery、clone、dynamic Gradle discovery
- APK／source archive／external asset bytesのGET、HEAD、download
- Android／Runner storage reservation、toolchain導入・削除
- Runner API call、Runner Job、RCE acknowledgement、source scan／review
- Build A／B、resource retry、comparison
- current releaseの差替え、trust／update relation／install eligibilityの変更
- `PackageInstaller`、system installer、package queryを使うcandidate昇格

既存`ManagedAppRepository.refresh()`はsource discoveryとAPK downloadへ進み得るため、Workerまたはmetadata-only actionから呼ばない。共通化できるのは、純粋なprovider parsing、identity validation、hashing、Room queryだけとする。background用のboolean flagを既存`refresh()`へ追加して副作用を分岐させない。

## 3. User-visible settings

### 3.1 Global settings

| field | values | default | validation |
|---|---|---|---|
| `enabled` | boolean | `true` | global OFFは全app overrideより優先 |
| `scheduleMode` | `INTERVAL`／`DAILY_LOCAL_TIME` | `INTERVAL` | unknownはschedule停止 |
| `intervalHours` | 1〜24の整数 | 6 | 範囲外は保存拒否 |
| `dailyLocalMinute` | 0〜1439 | 420（07:00） | daily選択時必須 |
| `releaseChannel` | `STABLE_ONLY`／`INCLUDE_PRERELEASE` | `STABLE_ONLY` | APK filename variantとは別 |
| `networkPolicy` | `ANY_AVAILABLE`／`UNMETERED_ONLY` | `UNMETERED_ONLY` | offlineは常に延期 |
| `batteryPolicy` | `ANY`／`ABOVE_20_PERCENT` | `ANY` | unknownは低電池回避時に延期 |

`dailyLocalMinute`は設定UIを初めてdailyへ切り替えたときの初期値であり、正確な07:00実行を保証しない。実行可能になる最短時刻を示し、OS、Doze、network、battery、provider cooldownにより後ろへずれる。exact alarm permissionは要求しない。

### 3.2 App override

active appは各global fieldを継承し、nullable overrideを持てる。ただしglobal `enabled=false`をapp側で回避できない。app `enabled=false`は当該appのscheduled checkだけを止め、手動`Check metadata now`、履歴、candidate表示を削除しない。

app notification muteはschedule OFFと別fieldにする。mute中も有効なscheduled check、candidate保存、last／next check表示を続ける。inactive appはoverrideに関係なくscheduled query、outbox新規作成、notification投稿から除外する。tracking再開は過去の未送信通知を再送せず、次回予定を現在時刻から再計算する。

### 3.3 Manual metadata check

`Check metadata now`は同じside-effect-free pipelineを使う。schedule OFFを明示的に回避できる利用者操作だが、inactive、network／battery policy、provider cooldown、request／time budgetは回避しない。既存のdownloadを伴う手動refreshとはlabel、callback、busy stateを分離する。

UI-Rの`Automatic download, build, comparison, and install`接続点は操作可能な設定にせず、4.5では`Always manual`の説明へ置き換える。

## 4. Scheduling and reconciliation

### 4.1 Work topology

既存15分周期`JobSyncWorker`は変更しない。4.5は次を別のunique work namespaceで実装する。

- `ReleaseCheckScheduler`: effective settingとRoom上の`nextEligibleAt`から次のunique one-time workを作る。
- `ReleaseCheckWorker`: due appを公平に選び、GitHub queueへ直列投入する。
- `NotificationDeliveryWorker`: committed outboxだけを投稿・抑止し、provider requestを行わない。
- `ReleaseScheduleReconcileReceiver`: timezone／時刻／日付／package replaceのsignalを受け、Room設定を正としてscheduleを再計算する。

`PeriodicWorkRequest`だけをschedule truthにしない。Roomの設定、last attempt、next eligible、cooldownをauthoritativeとし、WorkManager stateは再生成可能なdispatch参照とする。unique work名にapp表示名、repository URL、secretを含めない。

### 4.2 Due-time calculation

- interval modeは直近のsuccessfulまたはterminal check基準で、`intervalHours`以上後を次回最短時刻とする。
- daily modeは端末local timezoneにおける次の`dailyLocalMinute`を求める。過去時刻なら翌日へ送る。
- timezone／wall clock変更では未来の次回時刻を再計算し、過去の未実行分を連続実行しない。
- rebootはWorkManagerの永続化を利用し、application startとreconcile signalでRoomとのずれを修復する。
- force-stop中の実行を回避しない。利用者がappを再開した後、1回だけreconcileする。
- constraint／cooldown延期はcandidateと過去checkを消さず、待機理由と次回最短時刻を保存する。

### 4.3 Worker bounds and fairness

1回の`ReleaseCheckWorker`は次の全条件で停止する。

- 最大8 app
- wall-clock最大8分
- provider response body合計最大32 MiB
- provider cooldownまたはrate remaining 0を観測
- cancellation／WorkManager stop

due appは`nextEligibleAt`、`lastAttemptAt`、`registeredAppId`の安定順で選ぶ。同じappの失敗が他appを恒久的にstarveさせない。残りdue appがある場合は、cooldownとOS constraintを満たす次のunique workを1件だけ作る。

## 5. Network and battery gates

`UNMETERED_ONLY`はAndroidのnetwork metering分類を使い、Wi-Fi接続と同義にしない。`ANY_AVAILABLE`でも実network capabilityがない場合はrequestしない。network constraintが実行中に失われた場合は、そのappの未commit responseを成功として保存しない。

`ABOVE_20_PERCENT`はWorkManagerの`BatteryNotLow`だけで実装しない。Worker開始時にbattery percentとcharging状態を読む。

| observed battery | decision |
|---|---|
| 21%以上 | 実行可能 |
| 20%以下 | charging中でも延期 |
| percent不明 | 延期 |
| `ANY` | app独自のpercent gateなし。OS制約は残る |

constraint評価はcheck successに数えず、`DEFERRED_NETWORK`／`DEFERRED_BATTERY`としてschedule stateへ保存する。

## 6. GitHub provider contract

### 6.1 Scope and endpoints

4.5は認証tokenなしのpublic `github.com`だけを扱う。private repository、GitHub token、HTML scraping、GitLab、Codeberg、arbitrary Forgejo／Gitea、外部asset link追跡を追加しない。

- `STABLE_ONLY`: `GET /repos/{owner}/{repo}/releases/latest`
- `INCLUDE_PRERELEASE`: `GET /repos/{owner}/{repo}/releases?per_page=20&page=N`を最大2 page
- identity再確認が必要な404: boundedなrepository metadata確認
- tag: `GET /repos/{owner}/{repo}/git/ref/tags/{tag}`とannotated tag peel最大8段
- 必要時の同一release metadata再確認: provider release ID endpoint

release listは最大40 release、選択releaseのassetは最大256件とする。responseは1件最大2 MiB、1 appの合計4 MiB、request最大12回、wall-clock最大45秒とする。上限到達を「releaseなし」「assetなし」に変換しない。

選定はpublished metadataを持ち、draftでなく、channel条件に合うreleaseだけを対象にする。providerが返す安定順とpublished time、provider release IDを使って決定論的に選ぶ。同じpublished timeではprovider release IDのcanonical decimal stringをtie-breakerにする。unknown／invalid field、duplicate JSON key、非正規ID、空tag、過大値はfail closedとする。

### 6.2 Provider IDs

Room20で次の既存fieldを`INTEGER`／Kotlin `Long`からcanonical decimal `TEXT`／Kotlin `String`へ変更する。

- `release_snapshots.providerReleaseId`
- `release_snapshots.selectedProviderAssetId`
- `release_assets.providerAssetId`

GitHub IDは正の10進文字列とし、leading zero、符号、空白、指数表現を拒否する。migrationは既存INTEGERをlosslessな10進文字列へ変換し、PK／FK、snapshot ID、download path、comparison参照、`observationSha256`を変更しない。現行`ReleaseObservationHasher`はprovider IDを既に10進文字列としてcanonical payloadへ入れているため、schema version 1を維持し、Kotlin入力型だけを`String`へ変える。candidateの順序はcanonical decimalの数値順（桁数、次にASCII）とし、既存Long順と同じhashを生成するgolden vectorを要求する。table置換前後で全既存hashがbyte-for-byte一致し、同一app内の重複がないことを確認する。失敗時にRoom19を削除・初期化しない。

### 6.3 Conditional request and 304

ETagはendpointと固定queryごとに保存する。304はそのHTTP representationが未変更であることだけを表す。

304時は次を行う。

1. 保存済みrepresentation、release ID、tag、candidate assetsが完全であることを確認する。不足・破損時は同じrunで無条件refetchを1回だけ行う。
2. 保存済みtagをproviderへ再照合し、full commit SHAまで解決する。
3. full SHAが変われば新しいobservation／candidateを作り、過去trustを継承しない。
4. asset metadataは保存済みrepresentationとして保持するが、asset bytes、signer、package、versionの不変を主張しない。
5. APK GET／HEAD、download、update relation評価を行わない。

### 6.4 Failure classification and cooldown

| condition | code | scheduling |
|---|---|---|
| 304＋tag同一 | `NOT_MODIFIED` | 通常次回 |
| public repository確認済み、published releaseなし | `NO_PUBLISHED_RELEASE` | 通常次回 |
| 選択releaseにAPK metadataなし | `NO_APK_ASSET` | 候補状態を分離して通常次回 |
| 複数APK | `ASSET_SELECTION_REQUIRED` | downloadせず候補保存 |
| 404でpublic identityを再確認不能 | `NOT_FOUND_OR_NOT_PUBLIC` | repeated pollを抑制 |
| 403でrate根拠あり／429 | `PROVIDER_RATE_LIMITED` | shared cooldown |
| 403でrate根拠なし | `ACCESS_DENIED` | retryせず通常次回 |
| offline／timeout／5xx | `NETWORK_ERROR`／`PROVIDER_UNAVAILABLE` | bounded retry |
| JSON／identity／上限不正 | `INVALID_METADATA`／`LIMIT_*` | retryせず通常次回 |

403を一律rate limitにしない。`Retry-After`を最優先し、なければ`X-RateLimit-Remaining=0`とvalidな`X-RateLimit-Reset`を使う。provider instance単位のcooldownをRoomへcommitし、短周期、手動check、複数app、process restartで回避しない。

通常の一時失敗は30分、1時間、2時間の最大3 retryとする。3回後は次回定期確認へ戻す。retry count、reason、next eligibleをRoomへ保存し、WorkManager `runAttemptCount`だけをauthorityにしない。同じfailureから重複workを作らない。

## 7. Persistent state and Room20

### 7.1 Tables

| table | identity | main content |
|---|---|---|
| `release_check_settings` | singleton `1` | global defaults、revision、updatedAt |
| `app_release_check_overrides` | `registeredAppId` | nullable overrides、notification mute、revision |
| `release_schedule_states` | `registeredAppId` | last／next、waiting reason、consecutive retry |
| `release_check_runs` | UUID `checkRunId` | trigger、effective setting snapshot、outcome、provider evidence、timestamps |
| `release_candidates` | UUID `candidateId` | app、release observation、candidate state、first／last seen |
| `notification_outbox` | UUID `outboxId` | candidate、type、stable notification ID、delivery state |
| `provider_cooldowns` | provider＋instance | reason、notBefore、rate headers、updatedAt |
| `notification_dedup_headers` | app＋release identity＋type | last disposition、first／last time、stable ID |

設定変更はrevisionを増やし、既存check runのeffective snapshotを書き換えない。unknown enum、invalid timestamp、overflow、重複identityはscheduleを停止し、成功やdefaultへ変換しない。

### 7.2 Candidate states

最低限、次を別状態として保存する。

- `NEW_RELEASE_DISCOVERED`
- `VERIFICATION_REQUIRED`
- `VERIFIED_UPDATE_AVAILABLE`
- `ASSET_SELECTION_REQUIRED`
- `NO_APK_ASSET`
- `OBSOLETE`

`NEW_RELEASE_DISCOVERED`は`VERIFIED_UPDATE_AVAILABLE`ではない。新candidateに対応するAPK identity、version／signer gate、同一release observationにbindされたcomparisonが既に成立している場合だけ、既存truthを参照して`VERIFIED_UPDATE_AVAILABLE`と表示できる。Worker自身はdownload、comparison、trust mutationを行わない。

channel変更はcandidateを再評価するが、current release、installed package、履歴、進行中comparisonを変更しない。stableへ戻したときにpre-releaseから自動downgradeしない。

### 7.3 Atomicity

check run、new candidate、dedup header、outbox intent、schedule stateは1 Room transactionで保存する。transaction失敗時はnotificationを投稿せず、last successを更新しない。notification投稿はcommit後に行い、response消失／process death時はoutboxから再照合する。

## 8. Notification contract

4.5は1つのAndroid notification channel `release_updates`を作る。channelはrelease候補の状態通知だけに使い、build、comparison、Runner、一般診断errorを混在させない。

通知種別は次に限定する。

- `NEW_RELEASE`
- `VERIFICATION_REQUIRED`
- `ASSET_SELECTION_REQUIRED`
- `VERIFIED_UPDATE_AVAILABLE`

通常のnetwork失敗、rate limit、provider一時失敗を毎回push通知しない。app内のlast check／waiting reasonへ表示する。

dedup keyはprovider、instance、repository ID、release ID、observation hash、notification typeへbindする。notification IDはdedup keyのSHA-256から安定生成し、integer衝突時はRoomに保存した別IDへ決定論的に割り当てる。

notification tapはimmutable `PendingIntent`で`MainActivity.EXTRA_ROUTE=apps/{registeredAppId}/information`だけを渡す。action button、download、build、comparison、install intentを付けない。missing／inactive IDではAppsまたは保持履歴へ安全にfallbackし、新しい作業を開始しない。

Android 13以降の`POST_NOTIFICATIONS`はManifestへ宣言するが、permission dialogはapp起動、Worker、candidate発見時に自動表示しない。Updates画面上の明示操作からだけ要求する。拒否／dismiss時はcandidateを保持し、outboxを`SUPPRESSED_PERMISSION`へする。muteまたはpermission解除後に過去outboxを一括再送せず、抑止済みoutboxはterminalのままにする。現在候補はapp内で確認でき、次に発見した新identityから通知を再開する。

## 9. Retention and cleanup

自動maintenanceは1回100 recordまでとし、active checkと同じtransactionで大量削除しない。

| data | retention |
|---|---|
| current／unseen candidate、pending outbox、comparison／current参照 | 自動削除しない |
| detailed `release_check_runs` | 90日。各appの最新terminal runは保持 |
| delivered／suppressed／obsolete outbox payload | terminal後90日 |
| minimal notification dedup header | 365日 |
| provider cooldown | cooldown失効後90日。ただし最新instance stateは保持 |
| release observation／asset metadata | 既存4.2 retentionと参照保護に従う |

削除前にcandidate、outbox、current release、comparison、tracking、audit参照を再確認する。一部失敗はpartialとして保存し、dedup headerを先に消して大量再通知を発生させない。

## 10. Implementation dependencies and repository scope

新しい外部library、Firebase、Google Play services、exact alarm、foreground serviceを追加しない。既存のRoom 2.8.4、WorkManager 2.11.2、Ktor 3.5.0、kotlinx.serialization 1.11.0、JCS 1.1とplatform notification APIを使う。採用版が実装前に変わった場合は契約とlock情報を先に更新する。

| repository | code scope |
|---|---|
| Android | Room20、provider、scheduler／Worker、notification、UI、tests |
| Runner | production code／SQLite／API／capability変更なし |
| Project | contract、ADR、roadmap、README、report、compatibility |

`compatibility/workspace.toml`はRoom20 codeとmigration testがcommitされるまでRoom19を維持する契約だった。実装後は仮SHAやdirty treeではなく、統合済みlocal `develop`の確定full SHAと実装済みschemaを記録する。2026-09-07時点でAndroid `259c91e2fe9496f771d3bb64f7c91be45ae3d044`／Room20、Runner `7206dcac2b07bfab87a0e77a8a0a634cf327cbd8`／SQLite11に同期した。

## 11. Negative-test ledger

code-entry時の全項目`NOT_RUN`から、2026-09-06の自動検証結果を次のように更新する。`UNIT PASS`は記載したpure policy／MockEngine／hash範囲だけ、`COMPILE_ONLY`はAndroidTest sourceとAPKのcompileだけを示す。接続端末がないためproduct列は全件`NOT_RUN`である。

| ID | condition | required result | automated evidence | product |
|---|---|---|---|---|
| RC45-01 | global OFF＋app ON override | request／outboxなし、global OFF優先 | UNIT PASS: effective settings | NOT_RUN |
| RC45-02 | app OFF／inactive app | scheduled request／通知なし、履歴保持 | COMPILE_ONLY: Room active query | NOT_RUN |
| RC45-03 | 1／6／24時間、daily time | due time正規化、範囲外拒否 | UNIT PASS: interval／daily／validation | NOT_RUN |
| RC45-04 | timezone／時刻／日付変更 | future schedule再計算、catch-up連打なし | UNIT PASS: local due-time calculation only | NOT_RUN |
| RC45-05 | reboot／app update／force-stop後再開 | 重複workなし、Room基準で復元 | COMPILE_ONLY: Worker／receiver／scheduler | NOT_RUN |
| RC45-06 | metered／unmetered／offline | policyどおり延期、candidate保持 | UNIT PASS: network gate only | NOT_RUN |
| RC45-07 | battery 20%／21%／charging 20%／unknown | fixed境界、`BatteryNotLow`への読み替えなし | UNIT PASS: battery gate | NOT_RUN |
| RC45-08 | stable／pre-release／draft | channelどおり選定、draft除外 | UNIT PASS: MockEngine selection | NOT_RUN |
| RC45-09 | releaseなし／APKなし／複数APK | 原因分離、架空candidate／downloadなし | UNIT PASS: releaseなし／multiple selection; product download inventory未確認 | NOT_RUN |
| RC45-10 | 304＋tag同一 | NOT_MODIFIED、tag再照合、bytes不変を主張しない | UNIT PASS: cached representation／tag resolve | NOT_RUN |
| RC45-11 | 304＋tag SHA変更 | 新observation、過去trust非継承 | UNIT PASS: MockEngine SHA change; repository path COMPILE_ONLY | NOT_RUN |
| RC45-12 | 同release IDでasset metadata変更 | 新observation／candidate、current非破壊 | UNIT PASS: observation hash; persistence COMPILE_ONLY | NOT_RUN |
| RC45-13 | 403 rate header有／無、429 | RATE_LIMITEDとACCESS_DENIED分離 | UNIT PASS: rate evidence／access denial | NOT_RUN |
| RC45-14 | Retry-After／rate reset | provider shared cooldown、手動回避なし | UNIT PASS: provider evidence; durable queue COMPILE_ONLY | NOT_RUN |
| RC45-15 | offline／timeout／5xx | 30m→1h→2h最大3回、次周期復帰 | NOT_RUN: implementation compiled | NOT_RUN |
| RC45-16 | malformed／過大JSON、page／asset／request／time上限 | fail closed、候補なしと断定しない | UNIT PASS: malformed／UTF-8／body bound; remaining bounds NOT_RUN | NOT_RUN |
| RC45-17 | Room transaction失敗 | success／outbox／last checkを部分commitしない | COMPILE_ONLY: Room transaction path | NOT_RUN |
| RC45-18 | posting直前／直後process death | outbox再照合、重複通知なし | COMPILE_ONLY: durable outbox path | NOT_RUN |
| RC45-19 | app mute／permission deny／dismiss | candidate保持、再要求／大量再送なし | COMPILE_ONLY: notification permission／outbox path | NOT_RUN |
| RC45-20 | notification tap cold／warm start | app Informationだけを開き副作用なし | COMPILE_ONLY: route／PendingIntent path | NOT_RUN |
| RC45-21 | notification ID hash collision | 永続した別ID、他app通知上書きなし | COMPILE_ONLY: persistent notification ID path | NOT_RUN |
| RC45-22 | candidate発見時の旧trust／comparison | 新identityへ昇格・継承なし | UNIT PASS: trust identity policy; repository path COMPILE_ONLY | NOT_RUN |
| RC45-23 | Worker実行中に設定／tracking変更 | commit前再検証、無効対象のoutboxなし | COMPILE_ONLY: commit-time recheck | NOT_RUN |
| RC45-24 | Runner停止／ADB reverseなし | metadata、candidate、通知がAndroidだけで成立 | COMPILE_ONLY: Android-only dependency path | NOT_RUN |
| RC45-25 | background side-effect inventory | APK bytes、Runner Job、toolchain、build、comparison、install増加0 | COMPILE_ONLY: separated metadata repository; inventory NOT_RUN | NOT_RUN |
| RC45-26 | Room19→20実data migration | IDをTEXT化し、既存hash／current／comparison／trust／settingsをbyte-for-byte保持 | COMPILE_ONLY: migration fixture／schema20 | NOT_RUN |
| RC45-27 | unknown enum／schema／timestamp | schedule停止、default成功への変換なし | UNIT PASS: enum／schedule validation; persisted corruption COMPILE_ONLY | NOT_RUN |
| RC45-28 | retention 90／365日境界とpartial cleanup | protected保持、dedup先行削除なし | COMPILE_ONLY: bounded cleanup queries | NOT_RUN |

## 12. Product acceptance

Android 16で少なくとも2回の`ReleaseCheckWorker` product dispatchを行う。長時間待機をtest成功へ偽装せず、WorkManagerに登録されたproduction requestを端末のscheduler診断と明示的なtest dispatchで実行し、実際のprovider request、Room、notificationを観測する。既定6時間／1時間／daily timeの次回予定計算と登録状態は別に確認する。

製品受入では次を保存する。

- package／version、Room user_version／identity、migration前後件数
- WorkManager unique work、next scheduled time、constraint／waiting reason
- provider request path／count、ETag、304、tag full SHA、rate header。ただしsecretやraw private dataは保存しない
- candidate／outbox／dedup identity、notification ID、tap route
- Runner process／API call、ADB reverse、APK／toolchain／Job／comparison／PackageInstallerのbefore／after no-side-effect inventory
- reboot／timezone／network／battery／permission／muteの直接観測

MockEngine、WorkManager test driver、Room migration fixture、JVM testは必要だが、Android 16製品経路を代替しない。実public GitHubがrate limitedの場合は状態を`PROVIDER_RATE_LIMITED`として保存し、quota reset後に同じ無認証経路を再実行する。tokenを追加して受入を迂回しない。

## 13. Code-entry gate record

次は4.5 production code着手時に満たした履歴的gateであり、現在のmerge状態を表すものではない。

1. 本契約とADR-0023がlocal commitされている。
2. Android／Runner／Projectの`main`から`develop`、`agent/codex-phase-4-5`を作成済みである。
3. Android Room19、Runner SQLite11、API／UI-R route、現行dependencyをsourceで照合している。
4. GitHub Stars、Codeberg、Runner変更、download／build自動化がscope外として明記されている。
5. Room20 migration、provider ID TEXT、schedule／candidate／outbox／cooldown、retention、ledgerが未割当でない。
6. code着手時に3repoのworktreeがcleanで、4.5準備commitを`develop`／`main`へmergeせず、push／公開も行っていないことを確認した。

現在はAndroid feature commit `6e4ca30`をlocal `develop` `259c91e`へ、Runner文書commit `4a3f1cb`をlocal `develop` `7206dca`へ、Project文書commit `3f94329`をlocal `develop` `50ce8d4`へ統合済みである。3repoともpush／local `main`統合／公開は行っていない。

4.1／4.2の残証跡、4.4二project product E2E、UI-R端末受入は独立ledgerとして維持する。これらを4.5の自動testで閉じないが、4.5 code着手を禁止する新しい意味へ変更もしない。

## 14. References

- [WorkManager persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Define WorkRequests and constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [GitHub Releases REST API](https://docs.github.com/en/rest/releases)
- [GitHub REST API best practices](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api)
- [GitHub REST API rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
