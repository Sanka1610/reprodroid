# Runner API v2: foundation contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Foundation、`storage-retention@1`、`toolchain-install@1`、`generic-build@1`、`apk-comparison@1`、Phase 4.6 `runner-authentication@1` and Phase 4.7 Runnerの`codeberg-source@1` are implemented. Paired mode authenticates every normal v1／v2 route; development HTTP remains an exclusive loopback-only mode. Android Codeberg registration／APK inspection and paired Build A／B／raw comparison are locally verified.
- Date: 2026-09-08 (Phase 4.7 Runner addendum; foundation baseline 2026-09-05)
- Decision: [ADR-0018](../adr/0018-phase-4-operational-foundation.md); Phase 4.7 addendum: [ADR-0025](../adr/0025-phase-4-codeberg-provider-and-apk-selection.md)
- Domain and authority: [Phase 4.0 contract](../design/phase-4-foundation-contract.md)
- Implemented baseline: [API v1](runner-api.md) remains available; API v2 paired／development modes use Runner SQLite12 / Android Room22

## 1. Version boundary

API v2は実行・管理APIの番号であり、APK raw comparison protocol v2とは別である。製品version、capability contract、build設定、Manifest、scan、DB、export／backupのschemaは独立管理する。

この文書のfield名・状態は4.0の基礎契約である。4.2はloopback development modeへfoundationと`storage-retention@1`を実装し、4.3は`toolchain-install@1`、4.4は`generic-build@1`と`apk-comparison@1`、4.6はexclusive paired HTTPS、`runner-authentication@1`とprincipal ownershipを追加した。4.7はRunnerへpublic GitHub／Codebergの閉集合source host validatorと`codeberg-source@1`を追加した。provider metadata／release／asset download／APK inspection・selectionはAndroidが所有し、Runnerはsource取得・scan・build・comparisonだけを担当する。4.1のAndroid local登録はv2サーバーを必須としない。非loopback／release clientをdevelopment HTTPへ拡張せず、paired modeで全通常routeを認証する。

新Runnerへ切替えた段階で/v1の実行mutationを受付けず、426 API_UPGRADE_REQUIREDとする。旧requestを内部でv2へ変換しない。旧binaryへの新Android接続では新Jobを停止する。互換性不明・Runner停止でもlocal履歴・登録・provider確認は維持する。read-onlyなlegacy移行経路が必要なら別契約で定義し、v1 Job操作の裏口にしない。

## 2. Transport and authority

- /v2は同じRunnerへの固定originだけを使用し、redirectを追跡しない。
- release／LAN受入はHTTPSとpaired principalを必須とする。health、capabilities、operation結果、log、artifactも同じ認証境界へ入れる。
- loopback debug受入はexclusive development-only構成の単一local principalを使い、認証済み端末とは表示しない。非loopback bindやrelease clientでこの経路を使わない。
- paired modeは通常APIへ無認証例外を残さない。pairing開始はPC側操作、期限・一回限りsecretとPC承認を持つ限定手順であり、通常APIを迂回しない。
- principalIdはserver contextで確定し、JSON bodyに受け取っても権限の根拠にしない。token、secret、endpoint内部情報、absolute pathをerrorへ返さない。
- paired principalへのdevelopment履歴の所有権移行は明示照合・監査を必要とし、旧RCEやholdから権限を推定しない。

Phase 4.6のpairing API、exclusive transport mode、certificate／pin、token、失効、ownership adoption、上限、state、negative testは[secure Runner connectivity contract](../design/phase-4-runner-connectivity-contract.md)を正本とする。`runner-authentication@1`はpaired implementationで広告し、development modeをsecureと表示しない。QRは4.6対象外であり、manual payloadを必須経路とする。

## 3. Common wire constraints

| 要素 | 制約 |
|---|---|
| Content type | application/json、UTF-8。requestのcharset不一致を拒否 |
| 通常request／response | 実bytesで256 KiB／1 MiB。Content-Lengthだけを信用しない |
| 通常JSON nesting | rootを1として最大32。解析中に制限 |
| 通常JSON string | decoded UTF-8最大16 KiB。field固有の小さい上限を優先 |
| 通常一覧 | limit 1〜100、既定50。opaque cursor最大1 KiB |
| UUID | lowercase canonical UUID string。空文字や未検証の任意pathにしない |
| Git full SHA | 初期GitHubは40桁hex、小文字へ正規化。短縮SHA・ref名で代用しない |
| SHA-256 | 64桁lowercase hex |
| provider 64-bit ID／bytes／versionCode | 非負10進文字列、先頭ゼロなし（0を除く）、fieldの上限内 |
| 小さいversion／limit／attempt index | 上限付きJSON整数。小数・指数表記・overflow拒否 |
| 時刻 | UTCのRFC 3339文字列。時刻を権限の唯一の根拠にしない |
| 不正JSON | 重複key、BOM、不正UTF-8／孤立surrogate、non-finite数値、末尾余分を拒否 |

未知request fieldは拒否する。公開表示用の任意response fieldはschemaで許可した範囲だけ無視できるが、未知state／permission／isolation／evidence schemaは依存操作を止める。nullと省略はfieldごとに定義し、勝手にHOST／NONE／成功へ変換しない。配列要素数は各endpoint契約でさらに制限する。

Manifest8 MiB、scan4 MiB、APK上限、bounded log等の専用応答へ通常1 MiBを流用しない。これらの既存検証境界は維持し、schema変更は別契約で行う。

## 4. Capability negotiation

実装endpoint: GET /v2/capabilities。以下は、API v2のgeneric executionが有効なdevelopment構成で固定local principalの境界内に返す応答例である。generic executionが無効な場合は、`generic-build@1`、`apk-comparison@1`、`codeberg-source@1`を広告しない。

~~~json
{
  "apiVersion": "v2",
  "foundationContractVersion": 1,
  "runnerId": "00000000-0000-4000-8000-000000000001",
  "runnerVersion": "0.1.0-alpha02",
  "capabilities": [
    {"id":"foundation","contractVersion":1},
    {"id":"storage-retention","contractVersion":1},
    {"id":"toolchain-install","contractVersion":1},
    {"id":"generic-build","contractVersion":1},
    {"id":"apk-comparison","contractVersion":1},
    {"id":"codeberg-source","contractVersion":1}
  ]
}
~~~

runnerVersionは表示・互換表照合用で、大小から機能対応を推測しない。capabilitiesは最大64件の{id, contractVersion}、idは1〜64文字の[a-z0-9.-]、versionは1〜2147483647の整数、id重複を拒否する。実装済み機能だけを登録し、pairingを広告しない。foundationContractVersionの一致だけでは個々の実行を許可しない。

clientは操作ごとに必要なcapability IDとcontractVersionを照合する。未知の無関係なcapabilityを使う必要はない。実行requestではX-ReproDroid-Contractへ対象契約のid@versionを指定し、Runnerも認証・owner・当該機能の契約・runtime gateを再確認する。headerは128 ASCII bytes以内、欠落は400 CONTRACT_REQUIRED、不一致は409 CONTRACT_MISMATCHとする。capability名と各operationの対応は該当工程で固定する。

Phase 4.7の`codeberg-source@1`は、API v2 routeが有効でgeneric executionが成立する場合（`REPRODROID_ENABLE_REAL_BUILDS=true`かつ`REPRODROID_BUILD_SANDBOX=DOCKER`）に限り、`generic-build@1`と`apk-comparison@1`と併せて広告する。generic executionが無効なRunnerは3つとも広告しない。AndroidはCodebergのJob作成前に3つすべてのcapabilityとversionを確認し、旧Runner、欠落、version不一致ではv1／HOSTへfallbackせずfail-closedで停止する。

capability応答後の設定変更、engine停止、quota不足、失効は受付時に再検査する。古いcapability cacheを実行許可にしない。HTTP失敗、不正JSON、別runnerIdをcompatibleへ変換しない。

## 5. Durable operation identity

副作用のあるv2操作はIdempotency-Key headerへclient生成のcanonical UUIDを送る。keyの有効namespaceは(runnerId, authenticated principalId, operation kind, key)。operation kindはversioned endpoint契約が定義し、対象Job等のIDを要求hashへ含める。credentialはhash対象に含めず認証を別に再検査する。

Runnerは検証済み要求を次の論理payloadへ組み立て、JCS＋SHA-256を計算する。

- foundation／operationのcontract version。
- operation kind。
- target resource identity（存在しない新規作成ならnull）。
- 構造化したrequest payloadと明示的な実効default。動的な現在時刻やsecretは入れない。

同じnamespace/keyと同じhashは同じoperationId・結果参照へ結び付ける。異なるhashは409 IDEMPOTENCY_CONFLICT。同じ要求でもkeyを変えれば別操作なので、active比較やretry budget等のdomain gateを別途適用する。

受付後にdomain defaultが変わっても、元のoperation契約と受付時defaultで再送を照合する。暗黙default変更で同keyの別操作を生成しない。元契約を読めなければ再実行せず停止する。

key、hash、principal、operation ID、作成意図と結果参照をtransactionで永続化し、成功前にqueue／download／Docker等へdispatchしない。重複同時要求はDB unique制約で同じrowへ収束させる。再送にも現在の認証・失効・owner検証を適用し、失効tokenから過去のresponseを取得できないようにする。

## 6. Operation state

実装endpoint: GET /v2/operations/{operationId}。GETにIdempotency-Keyは不要で、同じprincipalとowner検証は必要。stateはJob state、comparison outcome、sandbox cleanup statusとは独立する。

| state | 意味 | 次に許可する処理 |
|---|---|---|
| RESERVED | 受付・固定要求・意図が保存済み。外部副作用は未開始 | domain gate再確認後にAPPLYING |
| APPLYING | durable intentから処理中 | resource/resultを照合して完了またはreconcile |
| COMPLETED | 操作結果参照を永続化済み | 同じ結果を読む。新Job生成なし |
| REJECTED | 受付後の確定した失敗。副作用がない、または回収を確認済み | reasonを読む。勝手な再実行なし |
| RECONCILIATION_REQUIRED | 外部副作用の成否・所有・回収が未確定 | owner／intent／resourceを照合。新規dispatch禁止 |

作成操作のCOMPLETEDは「Job作成結果の永続化済み」でありbuild成功ではない。JobがQUEUED等でも両立する。終了結果を現在のJob状態から上書きしない。

202は新規受付とoperation IDを返す。受付済みkeyへの再送は200で同じoperation IDと現在の受付結果参照を返す。入力・認証の受付前拒否はoperationを作らない。受付後の失敗はREJECTED等へ記録する。

restart後は保存状態を照合し、APPLYINGを無条件RESERVEDへ戻さない。外部副作用のexactly-onceを保証するのではなく、未確定な再dispatchを禁止して重複を防ぐ。副作用を伴ったか不明な失敗をREJECTEDに丸めない。

履歴cleanup後もnamespace/key/hash/result IDの最小tombstoneをstateの存続中保持し、同keyから別Jobを再生成しない。詳細結果を削除した場合は410 OPERATION_RESULT_GONEとし、監査header／削除理由を保持する。tombstoneもstorage budgetに算入し、保存不能なら新規操作を拒否する。

## 7. Common failure mapping

errorは{code, message}を基本とし、messageは固定した公開文言を使用する。raw exception／provider body／private resource detailを転記しない。

| HTTP | code | 挙動 |
|---|---|---|
| 400 | INVALID_REQUEST / CONTRACT_REQUIRED / IDEMPOTENCY_KEY_REQUIRED | 受付前拒否 |
| 401 | AUTHENTICATION_REQUIRED / CREDENTIAL_INVALID | 通常APIで認証不成立。credential値は返さない |
| 403 | OPERATION_NOT_ALLOWED | 認証済みでも当該操作を許可しない |
| 404 | RESOURCE_NOT_FOUND | 不存在または他principal所有。存在情報を漏らさない |
| 409 | CONTRACT_MISMATCH / CAPABILITY_UNAVAILABLE / IDEMPOTENCY_CONFLICT | 自動fallback／key差替えで再実行しない |
| 410 | OPERATION_RESULT_GONE | 同じ受付結果は消去済み。再作成しない |
| 413 | REQUEST_TOO_LARGE | 解析／保存前に受信打切り |
| 426 | API_UPGRADE_REQUIRED | v1実行や非対応APIを停止 |
| 503 | STORAGE_UNAVAILABLE / RECONCILIATION_REQUIRED | 新規副作用を止める。cleanup／cancelの安全経路は維持 |

これ以外のdomain error、Job create／confirm／retry、pairing payloadは各工程で追加する。4.2のhold／reservation／cleanup payloadと状態は[storage実装契約](../design/phase-4-storage-contract.md)で`storage-retention@1`として固定する。4.3 toolchain payloadは[toolchain実装契約](../design/phase-4-toolchain-contract.md)で固定する。foundationだけでそれらのendpointが完成したとは扱わない。

## 8. Acceptance linkage

4.0 ledger F4-06、F4-09〜15、F4-18、F4-25に、厳密JSON・byte上限・capability照合・同時再送・永続化障害・restart・失効再送を割り当てる。4.3はtoolchain contract T43-01〜15を追加し、4.4はG44-05〜14のfixture／API／Android保存を追加する。test engineだけでなく実SQLite／Android保存・旧client混在・実応答喪失を区別して記録する。

非公開workspaceの`compatibility/workspace.toml`は実装済みfull HEAD・実schemaだけを記録する。既定APIはv1のまま維持し、development opt-in v2と実装済みcapabilityを別fieldで記録する。pairingをcompatibilityへ列記しない。公開時点の値は[Current status](../status/current.md)へ記録する。

## 9. `toolchain-install@1`

`GET /v2/capabilities`は`foundation@1`、`storage-retention@1`、`toolchain-install@1`を広告する。4.3 endpointは`/v2/toolchains`以下だけで、v1 Job mutationや4.4 generic Jobへfallbackしない。catalog artifactにはarchive digest／size、expanded bytes、entry count上限、license、任意のpublisher signature policyを含める。

要求と応答の正本は[Phase 4.3 toolchain contract](../design/phase-4-toolchain-contract.md)。主要resourceはcatalog、plan、license、installation detail、inventory、removal previewである。installation responseは`installationId`とfoundation `operationId`の両方を返し、Androidはrunner IDとdigestを含む参照だけをRoom17へ保存する。

detail stateは`PLANNED`、`AWAITING_LICENSE`、`RESERVING`、`DOWNLOADING`、`VERIFYING_ARCHIVE`、`EXTRACTING`、`VERIFYING_CONTENT`、`PUBLISHING`、`INSTALLED`、`CANCEL_REQUESTED`、`CANCELLED`、`FAILED`、`RECONCILIATION_REQUIRED`。`INSTALLED`はcatalog archiveとpublisher policy、展開metadata、content manifest、atomic publish、inventory保存まで完了した意味であり、build／comparison成功ではない。

domain failureは400 `TOOLCHAIN_UNSUPPORTED`／`INVALID_REQUEST`、409 `TOOLCHAIN_PLAN_STALE`／`LICENSE_ACCEPTANCE_REQUIRED`／`IDEMPOTENCY_CONFLICT`／`TOOLCHAIN_REMOVAL_STALE`、503相当の容量・再照合停止を使用する。download body、absolute path、signature tool output、license以外の第三者本文は公開errorへ返さない。

## 10. `generic-build@1` and `apk-comparison@1`

4.4の詳細なinput validation、Docker境界、resource retry、raw truthは[Phase 4.4 generic build and comparison contract](../design/phase-4-generic-build-contract.md)を正本とする。`generic-build@1`は`POST /v2/builds`、`GET /v2/builds/{jobId}`、`POST /v2/builds/{jobId}:confirm`、`POST /v2/builds/{jobId}:scan-continue`、`POST /v2/builds/{jobId}:cancel`を提供する。create requestはcanonical full commit SHA、JCS化済みconfiguration snapshot、comparison ID／AまたはB attempt、expected APK base name、明示的な`riskAcknowledged: true`だけを受ける。任意shell、JVM option、environment、image、mount、resource retry fieldは受けない。

`apk-comparison@1`は`POST /v2/comparisons`、`GET /v2/comparisons/{comparisonId}`、`POST /v2/comparisons/{comparisonId}:retry-resource`を提供する。comparison createは公式APKのSHA-256／size／package／version、同じconfiguration hashの独立A／B Job、3軸raw outcome、trust／install eligibilityを永続化する。`reproducible`は3軸すべて`MATCH`かつtrust／install eligibilityの時だけtrueである。`DIFFERENT`と`INCOMPARABLE`を成功やretryへ変換しない。

両mutationは該当する`X-ReproDroid-Contract`とcanonical UUIDの`Idempotency-Key`を要求する。generic buildは`REPRODROID_ENABLE_REAL_BUILDS=true`かつ`REPRODROID_BUILD_SANDBOX=DOCKER`、cleanup完了、catalog-managed toolchainを再確認する。memory retryは保存済みcomparisonから新しいA／B一組を生成する一回限りの経路であり、通常のv1 retryへfallbackしない。hard disk／inode quotaと固定egress allowlistは現行4.4の対象外で、bridgeをnetwork isolationとして広告しない。4.4完了時点の公開source二project Build A／B E2Eは`NOT_RUN`として履歴を保持する。後続4.7では`qwerty287/ftpclient`のCodeberg製品経路を完走した。

### 10.1 Phase 4.7 public Codeberg source

Phase 4.7のRunner local実装commit `ecf9fa974e8ec3d4d623b7d0f844acef85d3ff46`は、generic sourceのprovider hostを`github.com`と`codeberg.org`の閉集合に限定する。両方ともHTTPSかつpublic URLだけを受け、credentials、明示port、query、fragment、opaque URI、owner／repository以外の余分なpath、空／`.`／`..` segment、percent-encoded traversal／separator、制御文字、不正なUnicodeを拒否する。末尾`/`と`.git`は同一repository identityとして正規化し、ASCIIのowner／repositoryはlookup仕様に合わせてlowercase化する。

正規化後のcanonical repository URLはJobのsource snapshot、clone、private／public Manifestで同一値を使う。generic Codeberg requestのcommitはlowercaseのfull 40桁SHA-1だけを受ける。4.7は既存`generic-build@1`のrequest body shape、API v2 endpoint、Runner SQLite12を変更せず、Codeberg対応を別capability `codeberg-source@1`として広告する。Runnerへprovider metadata、release／attachment lookup、APK download、APK inspection、APK selectionの責務は追加しない。

RunnerのURL／capability／canonical persistence回帰は自動testで確認済みである。Android製品では実Codeberg登録、release／asset metadata、APK selection／download／inspectionとcold startまで確認した。Runner製品経路ではWrapper不一致、source-scan上限、旧API 37 directoryをfail closedに拒否し、JDK `jspawnhelper`と正規`android-37.0`配置を修正・導入確認した。修正後の`qwerty287/ftpclient` Build A／Bは独立Jobとして成功し、Androidはraw outcomeをOfficial vs A `DIFFERENT`、Official vs B `DIFFERENT`、A vs B `MATCH`として保存し、cold start後も表示した。Codebergのprovider metadataとAPK選択は[Phase 4.7 provider contract](../design/phase-4-codeberg-provider-contract.md)と[ADR-0025](../adr/0025-phase-4-codeberg-provider-and-apk-selection.md)を正本とし、公開時点の検証状態は[Current status](../status/current.md)で管理する。
