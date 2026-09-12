# Phase 4.4: generic build and comparison contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Local implementation and automated verification completed. Android + Dockerでの二project Build A／B・比較E2Eは利用者指示により省略し、`NOT_RUN`のまま受入成功には数えない
- Date: 2026-09-05
- Decision: [ADR-0021](../adr/0021-phase-4-generic-build-sandbox-and-comparison.md)
- Targets and runtime evidence: 非公開のtarget台帳・実装記録で管理
- Public verification status: [Current status](../status/current.md)
- Previous contracts: [4.0 foundation](phase-4-foundation-contract.md), [4.3 toolchain](phase-4-toolchain-contract.md), [Runner API v2](../api/runner-api-v2.md)

## 1. この文書の扱い

これは4.4の実装前に未確定だった境界を閉じ、その後の実装を拘束する契約である。`generic-build@1`と`apk-comparison@1`はloopback development opt-inで実装・広告済みである。一方、AndroidとDockerを通す二projectの公開source Build A／B・比較E2Eは利用者指示により省略した。自動test、fixture、4.3の復旧確認をこの製品経路の成功へ読み替えない。2026-09-05のスコープ確定により、Job単位のhard disk／inode quotaと固定egress allowlistは現行4.4から除外する。これらを実装済み・検証済み・安全なネットワーク隔離として扱わない。

4.4は4.4a、4.4b、4.4cを一つの利用可能機能として扱う。4.4aの強制sandbox、4.4bの動的探索、4.4cのidentity・raw comparisonが揃わない状態で、固定MicroG recipeやHOST実行をgeneric buildの代替にしない。

## 2. Capability and endpoint boundary

実装時にAPI v2へ追加するcapabilityは次の2つだけとする。

| capability | version | 意味 |
|---|---:|---|
| `generic-build` | 1 | 4.4a／4.4bの実行、探索、Job lifecycle、sandbox evidenceを提供できる |
| `apk-comparison` | 1 | Official-vs-A、Official-vs-B、A-vs-Bのraw三軸を同一attemptへ保存できる |

4.4aのcontainer基本境界が未成立、または4.4b／4.4cの契約を実装していないRunnerは、これらを広告しない。`foundation@1`、`storage-retention@1`、`toolchain-install@1`の広告だけではgeneric executionを許可しない。

endpointは次の単位で固定する。`jobId`は既存Runner Jobのidentity、`operationId`は受付のdurable identityであり、同一視しない。

| endpoint | 用途 | 受付条件 |
|---|---|---|
| `POST /v2/builds` | AまたはBのgeneric buildを受付ける | `generic-build@1`、RCE acknowledgement、固定source snapshot、設定snapshot、sandbox gate |
| `GET /v2/builds/{jobId}` | Job・scan・sandbox・artifactの状態参照 | principalとownerを再検証 |
| `POST /v2/builds/{jobId}:confirm` | RCE acknowledgement後のclone開始 | Job／commit／ack digestの一致 |
| `POST /v2/builds/{jobId}:scan-continue` | source scan review後の探索・build継続 | Job／scan result digest／reviewの一致 |
| `POST /v2/builds/{jobId}:cancel` | 実行取消 | ownerとactive resourceを再検証 |
| `POST /v2/comparisons` | 公式APKとA／Bのattemptを作成 | `apk-comparison@1`、公式identity、A／B Jobの固定関係 |
| `GET /v2/comparisons/{comparisonId}` | raw三軸・trust・install可否の参照 | principalと保存済みsnapshotの照合 |
| `POST /v2/comparisons/{comparisonId}:retry-resource` | 限定資源retryの明示要求 | retry許可、原因証拠、budget、全体空き容量 |

`POST /v2/builds`はJob作成だけを意味し、build成功・APK生成・比較可能性を保証しない。実装は既存のv1 execution mutationを内部変換しない。v2 modeでv1のcreate／confirm／scan-continue／retryを受けた場合は既存方針どおり426とする。

すべての副作用操作はAPI v2 foundationの `Idempotency-Key`、`X-ReproDroid-Contract`、principal／owner検証、受付永続化を使う。入力不備はoperationを作らず、受付後のDocker／resource／cleanup不確定は`RECONCILIATION_REQUIRED`へ保存する。

## 3. 4.4a sandbox contract

### 3.1 Execution profile

generic buildはDocker必須であり、HOST fallbackを持たない。A／Bは同じprofileを使うが、container、source、`HOME`、Gradle user home、output、discovery stateを分離する。A完了とcleanup確認後にBを開始し、Runner execution slotは1とする。

| control | fixed value | evidence requirement |
|---|---:|---|
| image | `ubuntu@sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316`、`linux/amd64` | image ID、digest、architecture、engine versionを保存 |
| network | bridge、host network禁止、port publish禁止、Docker socket禁止 | network allowlist／host-LAN denyは現行スコープ外。bridgeを隔離証明と呼ばない |
| CPU | 4 CPU相当 | container inspectとcgroupを照合 |
| memory | 8 GiB | cgroup `memory.max`を照合 |
| swap | 0（memoryとswap上限を同値） | cgroup `memory.swap.max`を照合 |
| PID | 1,024 | cgroup `pids.max`を照合 |
| tmpfs | 合計1 GiB。`/tmp` 960 MiBは`noexec`、`/run/reprodroid-native` 64 MiBはnative library抽出専用の`exec` | path、size、`rw`／`nosuid`／`nodev`／`noexec`をmount情報と実行probeで照合。disk quotaの代用にしない |
| detailed log | 64 MiB | append時とimport時の双方で上限を適用 |
| Gradle workers | 最大2 | 実効Gradle設定とauditを保存 |
| build timeout | A／B各60分 | timeout、cancel、process failureを分離 |
| discovery timeout | A／Bを含む探索全体15分 | discovery timeoutとbuild timeoutを分離 |
| filesystem | root read-only、可変領域はJob専用 | mount、owner、symlink、regular fileを検証 |

Docker container内で実行してよいのは、Runnerがcatalog digestで検証済みのGradle distribution内`org.gradle.launcher.GradleMain`とその子processだけである。repositoryの`gradlew`、downloadしたscript、Docker daemon、Docker socket、host device、Runner credential、他Jobのpathは渡さない。JDK、Android SDK、Gradle distributionは4.3 storeから検証済みread-only mountとして提供し、共有可変cacheを作らない。

新規generic Jobは`docker-generic-v2`を使い、SQLite JNIなどJVMが展開するnative libraryだけを`-Dorg.sqlite.tmpdir=/run/reprodroid-native`へ固定する。汎用一時領域`/tmp`は`noexec`を維持し、native専用mountの実行成功と`/tmp`の実行拒否をGradle開始前にprobeする。既存の`docker-generic-v1` Job／Manifestはcanonical bytesとprofile hashを変更せず読み取り可能とし、v1をv2へ暗黙変換しない。

### 3.2 Explicit scope exclusions

2026-09-05に、次の2項目を現行4.4の実装・受入スコープから除外した。除外は実測済みという意味ではなく、未実装・未検証のまま4.4の必須gateにしないという範囲決定である。

- Job単位のhard disk／inode quota。`tmpfs`、bind mount、Runnerの事前空き容量、Dockerのstorage flagをhard quotaとは呼ばない。超過時の`SANDBOX_STORAGE_LIMIT_EXCEEDED`やquota専用retryも現行4.4では実装しない。
- 固定egress allowlist、host／LAN／link-local／Runner APIへのdeny proof。bridge、DNS、proxy環境変数をnetwork isolationの証明とは呼ばない。依存先の通信範囲は現行backendの実効範囲に従い、allowlist enforcementを広告しない。

現行範囲で残すnetwork境界は、host network、port publish、Docker socket、DinD、任意image／mount／optionを禁止することである。将来この2項目を再導入する場合は、別のversioned contract／ADR更新と実環境の受入証拠を先に追加する。

### 3.3 Failure classification

| condition | code | retry |
|---|---|---|
| engine／image／mount／基本resource準備不能 | `SANDBOX_UNAVAILABLE` | しない |
| container内memory cgroup OOMを証拠付きで確認 | `SANDBOX_MEMORY_LIMIT_EXCEEDED` | 4.4cの限定対象 |
| tmpfs／log上限超過 | 個別のresource failure | しない |
| Java heap不足 | `JAVA_HEAP_EXHAUSTED` | しない |
| host filesystem空き不足 | `HOST_STORAGE_UNAVAILABLE` | しない |
| PID／CPU／timeout／cancel | 個別の`SANDBOX_*`／`PROCESS_TIMEOUT`／`CANCELLED` | しない |
| cleanup／import／auditの成否不明 | `RECONCILIATION_REQUIRED` | しない |

exit 137、`ENOSPC`、Java exception、timeoutだけから原因を推定しない。engine state、cgroup、container process、host resource measurement、cleanup結果を揃えられない場合はunknownとしてretry不可にする。cleanup未確認のresourceを成功artifactとしてimportしない。

## 4. 4.4b dynamic discovery and independent A/B

sourceのfull commit SHA、repository identity、設定revision／hash、toolchain digest、network policy、resource profileをExecutionSnapshotへ固定する。`POST /v2/builds`の要求では、任意shell、任意JVM option、任意environment map、任意mount、任意imageを受け付けない。

実行順は次のとおり。

1. 固定source snapshotと構造化設定を検証する。
2. RCE acknowledgementを保存する。
3. host側でclone、detached checkout、full SHA確認、static source scanを行う。
4. findingsがある場合はJob固有のscan reviewで停止する。
5. container内で設定探索を行い、候補・実効設定・観測host・失敗理由を保存する。
6. 探索結果はA／Bの入力へコピーせず、A／Bそれぞれが同じcommitから独立に再探索する。
7. allowlist、lock／verification、`--no-build-cache`、対応している場合のconfiguration cache無効、`--rerun-tasks`、`C.UTF-8`、UTCを固定する。
8. `SOURCE_DATE_EPOCH`は既定未設定。上流設定と利用者明示の非負値以外をRunnerが導出しない。
9. container停止、cleanup確認、bounded import、artifact identity／manifest検査の後にJob結果を確定する。

探索完了はbuild成功を意味しない。Gradle module、Android task、variant、APK候補、plugin／dependency host、必要SDKはcandidate evidenceとして扱い、sourceの設定を書き換えない。`cvzi/ScreenshotTile`の複数公式APKはdownload前に手動選択し、`NewPipe`はdesktop／iOS moduleをAndroid APK taskとして誤選択しない。targetの固定値はgeneric target文書に置き、実行時にfull SHAを再照合する。

## 5. 4.4c identity, comparison, and limited retry

各comparisonは公式APK identityを先に確定し、同じcomparison IDに次の3軸を保存する。

| axis | input | raw result |
|---|---|---|
| Official-vs-A | 公式APK identity、A artifact | `MATCH`／`DIFFERENT`／`INCOMPARABLE` |
| Official-vs-B | 公式APK identity、B artifact | `MATCH`／`DIFFERENT`／`INCOMPARABLE` |
| A-vs-B | A artifact、B artifact | `MATCH`／`DIFFERENT`／`INCOMPARABLE` |

Reproducibleは3軸すべてのraw `MATCH`、公式APK identityの確定、trust／install policyの成立を必要条件とする。sandbox、pinning、determinism、scan、Manifest、signer、install結果はraw `DIFFERENT`や`INCOMPARABLE`を上書きしない。identity不明、APK候補複数、artifact欠損、取得失敗、signature／package／version不一致は`INCOMPARABLE`または既存のtrust errorへ保持する。`DIFFERENT`と`INCOMPARABLE`を同じ「失敗」に丸めない。

retryは既定OFFで、通常の`retry`をそのまま引き継がない。現行4.4では、container memory cgroup OOMを独立evidenceで確認できる場合だけ、`retry-resource`を明示要求できる。

- memory cgroup OOMを独立evidenceで確認できる。
- 追加は一回だけで、A／Bを新しい一組として作る。
- 固定source SHA、公式APK identity、設定snapshot、allowlist、scan review範囲を維持する。
- memoryは最大12 GiB、その他の上限は勝手に緩和しない。
- 前回Jobのcleanup完了、全体budget、実空き容量、retry budgetを再確認する。

Java heap、host disk、PID／inode／tmpfs／log、timeout、cancel、依存／network／scan／security／identity／signature／hash／license、`DIFFERENT`、`INCOMPARABLE`、原因不明はretry対象外である。旧Aと新Bを混ぜず、retryの許可と根拠をaudit recordへ保存する。

## 6. Database and evidence additions

4.4実装でAndroid Room18／Runner SQLite11を追加した。migration、API payload、private Manifest v5／public Manifest v4は実装commitで確定しており、互換表にはそのfull HEADだけを記録する。

| owner | current | 4.4 addition | content |
|---|---:|---:|---|
| Android Room | 17 | 18 | comparison attempt、公式identity、3軸raw、retry reference、複数APK選択 |
| Runner SQLite | 10 | 11 | execution snapshot、discovery evidence、sandbox resource evidence、retry authorization |
| private Manifest | 既存 | 5 | artifact identityとsandbox evidenceの参照。raw logやcredentialは含めない |
| public Manifest | 既存 | 4 | 公開allowlist fieldのみ。内部path、private manifest、raw logは含めない |

具体的なDDLとmigration testは実装側に置く。`compatibility/workspace.toml`には実装済みschemaとfull HEADだけを記録し、未実装の将来schemaを先行記録しない。

## 6.1 Implementation dependencies

4.4では新しい外部libraryを追加しない。既存の固定版をそのまま使用し、Docker CLI／engineとcontainer resource controlsはRunnerの管理対象backendとして検査する。filesystem quotaとegress policyは現行スコープ外であり、実効済みとは表現しない。コード実装時のversion gateは次のとおりとする。

| owner | dependency | version |
|---|---|---:|
| Android／Runner | Kotlin | 2.3.20 |
| Android／Runner | Ktor | 3.5.0 |
| Android／Runner | kotlinx.coroutines | 1.11.0 |
| Android／Runner | kotlinx.serialization | 1.11.0 |
| Runner | SQLite JDBC | 3.51.1.0 |
| Android／Runner | Java JSON Canonicalization | 1.1 |
| Android | Room | 2.8.4 |

Docker engine／CLIは環境依存の実体をversion・server・storage driverごと監査し、Gradle／JDK／Android SDKは4.3 catalogのdigestを参照する。4.4aのcontainer基本境界を満たすために未承認library、daemon、共有proxyを追加しない。変更が必要な場合は、この契約とADRを先に更新する。

## 7. Negative-test ledger for 4.4

このledgerの`NOT_RUN`は製品経路の受入状態であり、Runner fixture／API test、Android JVM・instrumentation testの総合結果とは別である。4.4実装の自動testは成功したが、二projectのAndroid + Docker Build A／B・比較E2Eは利用者指示により実行していない。

| ID | condition | required result | status |
|---|---|---|---|
| G44-01 | quota backendなし | 現行4.4ではquota gate／hard quotaを要求しない | OUT_OF_SCOPE by 2026-09-05 decision |
| G44-02 | overlay storage quota flagだけで超過 | 実効制限でないことを証拠化。quota採用はしない | OUT_OF_SCOPE by 2026-09-05 decision |
| G44-03 | bridgeから許可外DNS／host | allowlist deny proofを要求しない。bridgeを隔離証明と呼ばない | OUT_OF_SCOPE by 2026-09-05 decision |
| G44-04 | host／LAN／link-local／Runner API到達 | network deny proofを要求しない。host network／publish／socketは禁止 | OUT_OF_SCOPE by 2026-09-05 decision |
| G44-05 | root write／symlink／mount差替え | import拒否、他Job非干渉 | NOT_RUN |
| G44-06 | memory OOM／byte quota／Java heap／host disk | 原因別code、evidence不足ならretryなし | NOT_RUN |
| G44-07 | discovery生成物をBへ投入 | A／B独立探索、生成物共有なし | NOT_RUN |
| G44-08 | Gradle module／desktop／複数APK誤選択 | candidateを停止・reviewしAndroid APK誤採用なし | NOT_RUN |
| G44-09 | Official-vs-A／B／A-vs-Bの一軸Different | Reproducibleへ昇格しない | NOT_RUN |
| G44-10 | identity不明／候補複数／artifact欠損 | INCOMPARABLE保持、retryなし | NOT_RUN |
| G44-11 | memory OOMまたはbyte quotaの限定retry | 新A／B一組、最大1回、別Job review、budget保持 | NOT_RUN |
| G44-12 | retry対象外原因 | retry拒否、旧A／新B混在なし | NOT_RUN |
| G44-13 | cancel／timeout／cleanup不明 | artifact importせず、reconciliation | NOT_RUN |
| G44-14 | old v1 client／capability欠落／別runnerId | 426／409、generic executionなし | NOT_RUN |

## 8. Implementation and acceptance state

| item | state |
|---|---|
| 4.3 empty-store recovery | PASS: safe relative symlink、executable mode、9 catalog artifact、inventory、Runner restart復旧を確認 |
| 4.4a／b／c code, v2 capability, Room18／SQLite11, Manifest v5／v4 | PASS: local implementation and migration／API regression tests |
| Runner regression | PASS: 130 tests, failure 0, explicit opt-in skip 16 |
| Android regression | PASS: JVM 123 tests, connected 64 tests, failure／error 0; connected opt-in skip 9 |
| Android + Docker public two-project Build A／B／comparison | NOT_RUN: 利用者指示による省略。raw outcome、Reproducible、公開source product-path成功の証拠はない |

従って、4.4の実装と自動回帰は完了扱いにするが、製品経路E2Eの受入結果は将来の別実行まで未確定のまま保持する。quota／egressの除外を実効済み制御へ読み替えない。

## 9. Phase 4.7 source provider addendum

4.4時点の「追加するcapabilityは次の2つだけ」という記述（§2）は、4.4の実装範囲に限った基準である。4.7では`generic-build@1`の意味を黙って拡張せず、Runnerのpublic Codeberg source対応を独立した`codeberg-source@1`として追加する。確定Runner commitはcloseout時の`compatibility/workspace.toml`を正本とし、push／公開は別承認とする。

### 9.1 Runner／Android authority boundary

Runnerは`github.com`と`codeberg.org`だけを許可する閉集合host registryを使う。source URLはHTTPS、credentialsなし、portなし、query／fragmentなし、owner／repositoryのちょうど2 segmentだけを要求し、extra path、空／`.`／`..` segment、percent-encoded traversal／separator、制御文字、不正なUnicodeを拒否する。末尾`/`と`.git`はidentityを変えない場合だけ正規化し、ASCII owner／repositoryはlowercase canonicalizeする。canonical URLはJob、clone、Manifestで一貫して永続化・利用する。Codeberg generic requestのcommitはlowercase full 40桁SHA-1に限定する。

`codeberg-source@1`はAPI v2 routeが有効でgeneric execution gate（`REPRODROID_ENABLE_REAL_BUILDS=true`かつ`REPRODROID_BUILD_SANDBOX=DOCKER`）が成立するときだけ、`generic-build@1`および`apk-comparison@1`と一緒に広告する。AndroidはCodeberg buildの受付前に3つすべてを確認し、旧Runnerやcapability欠落／version不一致では新Jobを作らずfail-closedとする。v1 requestやHOST実行へのfallbackはない。既存のgeneric-build request body、endpoint、Runner SQLite12は変更しない（§6のSQLite11は4.4当時の追加値であり、4.7でschemaを再度増やす意味ではない）。

provider metadata、release／attachment lookup、APK download、APK inspection、APK selection、選択履歴はAndroidの責務であり、Runnerはsource取得・scan・build・comparisonだけを担う。この境界と4.7の受入条件は[ADR-0025](../adr/0025-phase-4-codeberg-provider-and-apk-selection.md)および[Phase 4.7 provider contract](phase-4-codeberg-provider-contract.md)を正本とする。

### 9.2 Phase 4.7 evidence status

RunnerのURL拒否、canonical repository URL、capability advertisement、既存GitHub generic regression、JDK process helper、API 37 exact package directoryは自動testで確認済みである。Android製品では実Codeberg登録、release／asset metadata、APK selection／download／inspectionとcold startまで確認した。Runner製品の負経路は、`android-example`の固定Wrapper不一致を`INVALID_DISTRIBUTION_URL`、`droidify/client`のsource-scan上限を`SOURCE_SCAN_RESOURCE_LIMIT_EXCEEDED`、`ftpclient`の旧API 37 directoryを`SANDBOX_TOOLCHAIN_MISMATCH`で拒否し、artifactを受入しなかった。

JDK導入は非symlink regular fileの`lib/jspawnhelper`を必須とし、API 37はcatalog、inventory、host／Docker preflightで正規の`android-37.0`へ解決する。専用storeでJDK再導入とAPI 37 remove／installを確認した。新規`docker-generic-v2`は汎用`/tmp`の`noexec`を維持し、SQLite JNI等のnative libraryだけを64 MiBの専用exec tmpfsへ分離する。SQLite log appendの競合はschema／journal modeを変えず`IMMEDIATE` transactionでsequence割当からfile／index commitまで直列化した。

修正後の`ftpclient` Android + Docker Build A／Bは独立RCE確認とscan reviewを経て成功し、raw三軸comparisonを`DIFFERENT`／`DIFFERENT`／`MATCH`として保存した。この4.7の単一Codeberg target受入は、§8に残る4.4の二project E2E省略を遡及的にPASSへ変更しない。段階別の生の結果は非公開のtarget台帳で保持し、公開状態は[Current status](../status/current.md)で管理する。
