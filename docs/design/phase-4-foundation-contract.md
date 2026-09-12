# Phase 4.0: 実装基礎契約

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Foundation specified; environment cleanup complete; local document commit closes code-entry gate
- Date: 2026-09-01
- Decision: [ADR-0018](../adr/0018-phase-4-operational-foundation.md)
- Scope and defaults: [Architecture overview](../architecture/overview.md)
- Current implementation and verification scope: [Current status](../status/current.md)

## 1. 適用範囲と開始条件

4.0は全Phase 4のコードを一度に実装する工程ではない。ここでは共通モデル、authority、互換性、副作用の受付、失敗時の不変条件を確定する。4.1以降の具体DB DDL、採用library、catalog、quota backendは対応工程のcode前に契約化する。

契約作成時baselineはAPI v1、Room14、SQLite8、private Manifest4／public3だった。4.2完了時点は既定API v1、loopback development API v2 `foundation@1`／`storage-retention@1`、Room16、SQLite9である。API v2契約は[別文書](../api/runner-api-v2.md)に置き、未実装のendpointを利用可能と表示しない。

code着手前に次を満たす。

1. 3Eの最終受入と現在HEAD／schemaを照合する。
2. 保持環境を現況確認し、対象別承認の後、必要なmigration証拠を保存してcleanupする。過去PID・port・reverseを使い回さない。
3. 前回の計画文書差分を失わず整理し、明示されたlocal commit範囲で3repo cleanを確認する。merge／pushは別扱い。
4. 本契約、ADR、API、roadmap、README、reportが同じ再開地点を示す。
5. 最初の実装は4.1契約／negative ledger／Room変更を具体化してから行う。

## 2. 共通identityとデータ責務

ここでの名称はdomain contract。SQL table／wire fieldのすべてを現在割り当てるものではない。

| モデル | 固定identity・内容 | 所有工程 |
|---|---|---|
| ManagedApp | UUID registeredAppId。package未確定でも作成可能。同じrepositoryの独立packageは別ID | Android 4.1 |
| RepositoryIdentity | provider、instance host、providerRepositoryId。owner/name/URLは検証済みlocator | Android 4.1、Codeberg 4.7 |
| SourceSnapshot | RepositoryIdentity、full commit SHA、root tree SHA、取得時刻、検査範囲 | Android 4.1 |
| GradleDiscovery | SourceSnapshot、候補path/type、探索状態と上限・失敗理由。build可否を断定しない | Android 4.1 |
| BuildSettingsRevision | app ID、単調増加revision、schemaVersion、構造化設定、content hash。未確定項目を明示 | Android 4.1 |
| ReleaseObservation | 新しいlocal UUID、provider release ID、tag、full SHAの確認状態、metadata・asset観測 | Android 4.2 |
| AssetObservation | provider asset IDとmetadata、取得APKのidentity・bytes hashを分離 | Android 4.2／4.4 |
| ExecutionSnapshot | repository/full SHA、設定revision/hash、実効toolchain／policy／資源。受付後immutable | Runner 4.4 |
| ComparisonAttempt | comparison ID、attempt ID/index、同条件A/BのJob参照、固定参照APK | Android 4.4 |
| ExecutionAuthorization | principal、固定要求、対象比較／Job、許可範囲、取消状態 | Runner 4.4／4.6 |
| SourceScanReview | Job ID／full SHA／scanner version／digestへの個別同意 | 既存境界維持 |
| Availability | 保持bytesの取得／検証／削除／欠損／破損状態と最終確認時刻 | 各owner 4.2 |
| RetentionHold | Runner ID、principal、Job/artifact参照、理由、明示解除。切断で解除しない | Runner 4.2 |
| OperationRecord | principal、操作、request key/hash、状態、result参照。再送・restart照合 | Runner 4.2以降 |
| ToolchainInventory | 検証済みexact version/digest、license同意、予約／使用参照 | Runner 4.3 |
| NotificationOutbox | app、候補identity、通知種別、安定ID、投稿／抑止状態 | Android 4.5 |

providerのIDはdomain／v2／新exportで10進文字列とし、数値の丸めやURLからの推測をしない。初期instanceはgithub.com、4.7でcodeberg.org。provider情報が未確認の旧履歴はLEGACY_UNRESOLVED相当の状態を持ち、migrationでnetwork解決や架空ID生成をしない。URLから解決したrepository IDが変わった場合は、同じrepositoryとして黙って再bindしない。

registeredAppIdは既存値を保持する。4.1でURL unique制約を見直す際、通常の重複登録と、利用者が別管理対象として明示する操作を区別する。packageはnullableで、空の仮packageやrepository名を埋めない。表示名の変更はidentity変更ではない。

同じrelease／asset IDでも、新たなtag SHAまたはmetadata／APK hashを観測したら新snapshotを作る。過去comparisonが参照するsnapshotは変更しない。完全に同じ観測内容の再確認時刻はcheck recordへ記録できる。現在選択中、発見した候補、実行中比較の対象を分ける。

## 3. 状態と判断を混ぜない

- 登録成功はAPK download成功、Android projectの確定、build成功を含まない。
- 静的検出は検査範囲内のGradle候補を示す。探索完了かどうかは別軸で、上限／timeout／truncatedを「候補なし」にしない。
- 設定の構文検証と、不足toolchain解決、Runner実行可能性を分ける。保存はRCE承認ではない。
- Job SUCCEEDEDはbuild成功。公式APKとのidentity不一致や未取得は比較不能として扱う。
- protocol v2 raw 3軸すべてMATCHだけがReproducibleの必要条件。trustの既存truth table／current identity gateを維持する。
- 監査headerの過去結果はbytes削除で改変しないが、現在install／再比較できるとは表示しない。
- imported history、旧schemaの未記録値、unknown enum、破損evidenceを新しい成功／権限へ変換しない。

## 4. 設定・snapshot・digest

build root／module／variant／task／toolchain要求は構造化する。任意shell、JVM起動引数、環境変数map、mount／image／proxyの自由入力を共通設定へ作らない。pathはUTF-8 1 KiB以下の許可root内相対path。absolute、親参照、NUL、制御文字、separator曖昧性を拒否する。taskの許可文法は4.1で固定する。

設定保存は新revisionを作り、既存Jobを更新しない。要求hashとsnapshot hashは異なる役割を持つ。Runnerが検証・解決した実効値だけをsnapshotへ固定し、clientが送ったhashを無検証で信用しない。不足設定のあるdraftから実行snapshotを作らない。

新たなJSON content hashはRFC 8785 JCS＋SHA-256を共通方式とし、schemaVersionと対象payloadを含め、当該hash field自身は除く。authoritativeなhashには取得時刻や現在のdisplayName等の非実行値を混ぜない。設定hash、request hash、snapshot hashのpayload schemaを分ける。schemaVersionや意味が変われば同一hash対象にしない。

既存のAPK SHA-256、source-scanのcanonical-result digest、Manifestの既存検証方式を変更しない。JCS hashは署名や真正性証明ではない。JCS実装の採用版・golden vectorsは最初に利用する工程で固定する。

## 5. Runner／principal／cleanup owner／credential

| 識別子 | 意味 | 禁止する解釈 |
|---|---|---|
| runnerId | 専用stateに永続化するRunner identity。endpointとは独立 | URL一致だけで同じRunnerと断定 |
| principalId | Runnerが管理する端末別認証主体 | clientがbodyへ書けば権限を持つ |
| sandbox ownerId | 既存SQLiteの資源回収namespaceとengine照合 | Bearer token、client identity、真正性証明 |
| jobId／operationId | Runner内の対象resource identity | IDを知っているだけで操作を許可 |

端末の表示名は権限に使わない。Runner側は認証済みprincipalをrequest contextから確定する。Job、hold、operation等を同じprincipalへbindし、他principalの参照・変更を拒否する。端末失効は新操作を止めるがholdを自動消滅させず、PC側の明示処理と監査で整理する。失効しても進行中Jobのtimeout／停止／cleanupはRunner側で維持する。

4.6ではAndroid Keystore鍵で暗号化したtokenをprivate fileへ保存し、Roomには秘密でない参照だけを置く。token、pairing secret、CA private key、実行許可の再利用権限はexport／backupへ入れない。Runnerはtoken hashを保存し、raw credentialをbuild process、source、logへ渡さない。TLS・期限・hostname・pin検証を済ませてからsecretを送り、redirectに転送しない。

4.6前のdebug受入は、合意済みloopback HTTP＋ADB reverseのローカル開発環境だけに限定する。これは端末認証済みを意味しない。最初のv2実装ではdevelopment-only接続と単一local principalを明示し、release／LAN／public運用には使えない構成にする。そのprincipalからpaired principalへの権限自動移行は禁止し、PC側で再照合する。4.6の必須受入後は通常APIすべてに認証を要求し、無認証healthを迂回路として残さない。

既存sandbox ownerのnamespaceは資源回収が完了するまで維持する。Runner ID／principal新設時に旧owner・RCE確認から端末tokenや追加試行権限を生成しない。

## 6. 受付と重複防止

[API v2 foundation](../api/runner-api-v2.md)の操作key・要求hash・永続受付を共通化する。認証／失効、API／契約照合、入力上限、owner、必要な同意、reservationを確認してから副作用を許可する。

durableな受付→副作用intent→実resource照合→結果永続化を分ける。DB commit前にenqueueしない。応答が失われても同じkeyで同じ結果参照を返し、新Jobを作らない。外部資源の作成が不明なら再作成せずreconciliationを要求する。これは外部副作用の厳密なexactly-onceを保証する主張ではない。

既存v1 retryを限定許可へ読み替えない。資源retryの許可は4.4cで新規定義し、新しいA/B一組と新Jobごとのscan reviewを維持する。通常のrequest再送は新しい比較試行ではない。

## 7. Data classificationとretentionへの接続

| 分類 | 保存owner | 保護・保持 | export／backup |
|---|---|---|---|
| 登録／設定／current参照 | Android | 自動削除なし、migration保持 | 論理backup対象。権限は除く |
| APK／icon | Android／Runner | current・未完了保護。非current APK30日で候補化 | 本文を監査／backupへ入れない |
| workspace／書込みcache | Runner | active／queued／review保護。terminal7日で候補化 | 対象外 |
| immutable toolchain／staging | Runner | 使用・予約・導入中保護。未使用未pin30日で候補化 | 本文対象外 |
| public evidence詳細 | 各owner | current保護、大容量証跡90日で候補化 | allowlist再検証した監査のみ |
| comparison／cleanup監査header | 各owner | 少なくとも365日、明示削除 | version付き監査／任意履歴backup |
| credential／CA trust／permission | credential owner | 失効・削除は専用手順 | 対象外 |
| raw build log／private Manifest | Runner | 詳細log90日、容量上限 | 監査に入れない。log exportは別操作 |

予算はAndroid4 GiB／Runner Job64 GiB／toolchain32 GiB、80%警告。保管済み＋予約＋必要量と実空き容量を別々に検査する。4.2で予約・hold・削除intent・partial failureをdurableに実装する。現在の運用cleanupと、未実装の製品cleanup機能を混同しない。

## 8. 工程別code着手前の残契約

| 工程 | ここで未割当の具体項目 | 実装を認める範囲 |
|---|---|---|
| 4.1 | [登録契約](phase-4-registration-contract.md)でprovider／state／設定文法／Room15変更を具体化。generated DDLと実装受入は未実施 | GitHub metadata登録、source-only、静的探索、設定保存 |
| 4.2 | history／availability／reservation／hold／cleanup API・export schema・DB | historyと手動storage管理。generic buildなし |
| 4.3 | 公式catalog・digest／展開上限・license／inventory API | 専用store導入。system SDK変更なし |
| 4.4a | 実engineのcontainer基本境界・停止監査 | host network／port publish／Docker socket／DinDを禁止。通信allowlistとbytes／inode quotaは現行4.4対象外 |
| 4.4b/c | 動的探索／実行snapshot／A/B／identity・限定許可／snapshot schema | 独立buildと比較、限定資源retry |
| 4.5 | schedule／candidate／outbox／provider cooldown | metadata確認・通知のみ |
| 4.6 | [secure connectivity契約](phase-4-runner-connectivity-contract.md)に従いTLS／manual pairing、token／失効store、rate上限、Room21／SQLite12、legacy ownership adoptionを2026-09-08に実装・受入完了。RC46は44 PASS／0 PARTIAL／0 NOT_RUN、QRは将来候補 | 認証済みrelease HTTPS／明示LAN |
| 4.7 | Codeberg実payload／配信host／channel／asset選択 | provider同等機能 |
| 4.8 | 暗号library／backup形式／署名・license・CI採用版 | 通し移行・配布準備。公開は別承認 |

## 9. Negative-test ledger

以下は受入要件であり、実行結果ではない。現在すべてNOT_RUN。各工程reportにtest名／fixture／製品経路／観測結果を付けて更新する。

| ID | 境界・失敗 | 必須の観測 | 工程 | 状態 |
|---|---|---|---|---|
| F4-01 | release／APK／packageなし | 登録保持、架空package・download・Runner callなし | 4.1 | NOT_RUN |
| F4-02 | tree truncated／page未取得／量・深さ・時間上限 | 不完全として保存、候補なし／唯一と断定しない | 4.1 | NOT_RUN |
| F4-03 | symlink／submodule／cacheだけのGradle名 | 通常file候補と混同せず、source実行なし | 4.1 | NOT_RUN |
| F4-04 | 404／権限／rate／network／不正JSON | 原因分離、既存登録・snapshotを失わない | 4.1 | NOT_RUN |
| F4-05 | 同URLのrepository ID変更／同repo別package | 黙ってidentity再bindしない、明示した別管理を許容 | 4.1 | NOT_RUN |
| F4-06 | 不正path／task／過大URL／overflow／重複JSON key | 保存・実行前拒否、shell解釈なし | 4.1以降 | NOT_RUN |
| F4-07 | 実行中の設定変更 | 既存revision／snapshot／A/B条件保持 | 4.1／4.4 | NOT_RUN |
| F4-08 | 同release／asset IDでSHA・bytes変更 | 新観測、旧current／comparisonにtrust継承なし | 4.2／4.4 | NOT_RUN |
| F4-09 | old client/new Runner・逆の組合せ | 新Job停止、v1 fallbackなし、local履歴維持 | v2導入時 | NOT_RUN |
| F4-10 | unknown state／contract／schema／capability欠落 | 該当操作停止、成功・HOST等へのdefault変換なし | 各工程 | NOT_RUN |
| F4-11 | 同key同要求の同時再送／応答喪失／restart | durable result同一、重複Job／予約／資源なし | 4.2以降 | NOT_RUN |
| F4-12 | 同key異要求／操作／principal | 範囲照合、拒否または別namespace、他結果漏洩なし | 4.2以降 | NOT_RUN |
| F4-13 | INSERT失敗／外部create応答喪失 | 副作用先行なし、不明時reconcile、勝手な再dispatchなし | 4.2以降 | NOT_RUN |
| F4-14 | 旧owner／旧RCE／backup import | credential・追加試行・review権限を新規生成しない | 4.4／4.6／4.8 | NOT_RUN |
| F4-15 | 失効tokenで同key replay／他principal ID | 再認証・owner拒否、過去結果を返さない | 4.6 | NOT_RUN |
| F4-16 | current／review／cleanup PENDING／切断中holdの削除 | bytes・参照保護、黙った解除なし | 4.2 | NOT_RUN |
| F4-17 | preview後の参照変更／一部削除失敗 | 再検証、partial結果、監査headerとavailability整合 | 4.2 | NOT_RUN |
| F4-18 | budget境界／予約競合／実空き容量不足 | 新規受付停止、cancel・監査余地保持 | 4.2／4.3 | NOT_RUN |
| F4-19 | 破損evidence／export失敗／cleanup競合 | 正常証拠・完成fileにしない、機密非出力 | 4.2 | NOT_RUN |
| F4-20 | toolchain digest／license／途中cancel | 未検証installedなし、既存SDK不変 | 4.3 | NOT_RUN |
| F4-21 | 通信迂回／全書込みquota欠落／回収不明 | hard quota／fixed egress allowlistは現行4.4 scope外。bridgeを隔離、tmpfs／事前空き容量をhard quotaと広告しない。container cleanup不明時は実行停止、HOST fallbackなし | 4.4a | OUT_OF_SCOPE（quota／egress）＋product E2E NOT_RUN |
| F4-22 | OOM不明／Java heap／INCOMPARABLE／上限到達 | 自動retryなし、旧Aと新Bを混ぜない | 4.4c | NOT_RUN |
| F4-23 | 定期確認／通知tap／mute／20%電池／304 | 禁止副作用なし、候補保持、tag再照合 | 4.5 | NOT_RUN |
| F4-24 | TLS pin／期限／hostname不一致・redirect | credential送信前拒否、HTTP fallbackなし | 4.6 | NOT_RUN |
| F4-25 | migration失敗／未知DB／容量不足 | 元DBと整合snapshot保持、初期化・再実行なし | 各migration | NOT_RUN |
| F4-26 | 補助証跡・削除済みAPK・import履歴 | raw／trust／signer／install policyへの不正昇格なし | 全工程 | NOT_RUN |

4.1のpositive受入にはGroovy／Kotlin DSL、multi-module／subdirectory、source-only、Android非APK候補、Runner停止中の製品登録とcold startを含める。migration時はRoom14 baselineのcurrent／comparison／trustを保持する。4.4のGitHub2件、4.7のCodeberg1件、4.8の通し移行・release条件はroadmapのまま維持する。

## 10. 技術参照

- [GitHub Git trees API](https://docs.github.com/en/rest/git/trees?apiVersion=2022-11-28): file mode、truncated、非再帰探索。APIの上限をそのまま製品上限にはしない。
- [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html): JSON canonicalization。単なるkey sortや通常serializerをJCS実装とは扱わない。
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): 暗号鍵管理。採用API・端末でのcredential受入は4.6。
