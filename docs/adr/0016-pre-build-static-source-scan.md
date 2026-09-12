# ADR-0016: pre-build static source scan and conditional review gate

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Implemented
- Date: 2026-08-28
- Scope: Phase 3D

## Context

Phase 2Dのraw 3軸comparisonとPhase 3A〜3CのManifest、dependency pinning、determinism evidenceは、成果物と実行条件を説明できる。一方、detached checkout内のsourceやbuild scriptに、Bidi control、不可視format character、Unicode正規化異常、process起動APIが含まれる場合でも、現行RunnerはWrapper検証とGradle実行へ進む。

static scanはmalware判定ではない。regexはcomment／string literal／未使用codeを区別できず、findingがないことも安全性を証明しない。Phase 3Dは、sourceを実行せずに限定的なindicatorを抽出し、利用者がGradle開始前に確認できるbounded evidenceを追加する。

最初のhost RCE確認はclone前に必要であり、scanはdetached checkout後にしか実行できない。そのためscan resultを最初のRCE確認画面へ前提情報として表示することはできない。findingが存在する場合にGradleが先に開始される競合を避けるには、scan後の条件付きreview gateが必要である。

## Decision

### 1. REAL_TRUSTEDのdetached checkoutだけをscanする

RunnerはJob単位RCE確認、clone、detached checkout、resolved commit再検証の後、Android SDK検証、Wrapper検証、Gradle実行より前にin-process scannerを実行する。

- `SIMULATED`はscanner、scan API、review gateへ到達しない。
- scannerはsource、build script、Wrapper、任意external scannerを実行しない。
- checkout root直下の`.git` treeを除外する。
- symbolic linkを追跡しない。件数だけsummaryへ記録する。
- regular file以外をscanしない。
- normalized pathがcheckout root外へ出る場合はterminal failureとする。
- scan read中のfile type／size変化、I/O failure、結果永続化失敗はterminal failureとする。

directoryはentry countへ含めて走査だけに使用する。symbolic linkはentry countと`skippedSymlinks`へ含めてskipする。FIFO、socket、deviceなどdirectory／regular file／symbolic link以外のentryは`SOURCE_SCAN_INVALID`とする。pathのNFC／canonical collision検査はallowlist fileだけでなく、`.git`を除く全checkout entryへ適用する。

Job stateへ`SCANNING_SOURCE`と`AWAITING_SCAN_REVIEW`をadditiveに追加する。

`GET /v1/jobs/{jobId}`の`sourceScan.status`は`SCANNING`、`COMPLETED`、`FAILED`の3値とする。`SCANNING_SOURCE`では`SCANNING`とscanner versionだけを返し、result／count／review fieldは省略する。atomicなresult保存後は`COMPLETED`と全summary fieldを返す。scanner operational failureでは`FAILED`とscanner versionだけを返し、partial resultを公開しない。scan未到達またはlegacy Jobだけが`sourceScan` field自体を持たない。Androidはstatusごとのrequired／forbidden fieldをstrictに検証する。

### 2. text対象をallowlistで固定する

extension allowlist:

```text
.kt .kts .java .gradle .groovy
.xml .aidl .smali
.c .cc .cpp .cxx .h .hpp
.sh .bash .py .js .mjs .cjs .ts .ps1 .bat .cmd
.properties .toml .json .yaml .yml
```

basename allowlist:

```text
gradlew
settings.gradle
settings.gradle.kts
build.gradle
build.gradle.kts
gradle.properties
```

textはstrict UTF-8としてdecodeする。UTF-8 BOMはfile先頭だけ許可し、scan時に除外するがfileを書き換えない。NULを含むfileはbinaryとしてskipする。allowlist対象でNULがなくstrict UTF-8 decodeに失敗するfileは`TEXT_ENCODING_UNSUPPORTED` findingを1件記録し、内容detectorを適用しない。

### 3. detector v1を固定する

scanner versionは`reprodroid-static-v1`とする。detectorはfindingの存在だけを示し、実行可能性、悪意、到達可能性を断定しない。

| Detector ID | 固定対象 |
|---|---|
| `UNICODE_BIDI_CONTROL` | U+061C、U+200E、U+200F、U+202A〜U+202E、U+2066〜U+2069 |
| `UNICODE_INVISIBLE_FORMAT` | U+00AD、U+200B、U+200C、U+200D、U+2060、file先頭以外のU+FEFF |
| `UNICODE_NON_NFC` | text lineまたはrelative pathがUnicode NFCではない |
| `CANONICAL_PATH_COLLISION` | NFC化したrelative pathが別のcheckout entryと一致する |
| `TEXT_ENCODING_UNSUPPORTED` | strict UTF-8としてdecodeできないallowlist対象text |
| `PROCESS_EXEC_API` | 下記の明示pattern |
| `DYNAMIC_NATIVE_LOAD_API` | 下記の明示pattern |
| `NETWORK_DOWNLOAD_COMMAND` | 下記の明示pattern |

`PROCESS_EXEC_API`のv1 pattern:

```regex
\bRuntime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\(
\bProcessBuilder\s*\(
(?m)^\s*(?:project\s*\.\s*)?exec\s*\{
\bproviders\s*\.\s*exec\s*\{
\b(?:ExecOperations|JavaExec|Exec)\b
\.\s*execute\s*\(
\b(?:system|popen|execve)\s*\(
```

最後のnative patternはC／C++ extensionだけへ適用する。他のpatternはKotlin、Java、Gradle、Groovyへ適用する。`.execute()`はGroovy／Gradleだけへ適用する。

`DYNAMIC_NATIVE_LOAD_API`のv1 pattern:

```regex
\bSystem\s*\.\s*(?:load|loadLibrary)\s*\(
\b(?:dlopen|LoadLibraryA|LoadLibraryW)\s*\(
```

`NETWORK_DOWNLOAD_COMMAND`のv1 pattern:

```regex
(?i)(?:^|[;&|]\s*|\s)(?:curl|wget)\s+
```

これはshell、Gradle、Groovy、PowerShell、batch fileだけへ適用する。patternを変更する場合、既存versionの意味を書き換えずscanner versionを上げる。

finding cardinalityと位置は次で固定する。

- Bidi／invisible detectorはcode point出現ごとに1 findingとする。
- process／native／network detectorはnon-overlapping regex matchごとに1 findingとし、match開始位置を記録する。
- textの`UNICODE_NON_NFC`は非NFCの各lineにつき1 findingとし、正規化前後で最初に異なるUnicode code point位置を記録する。
- pathの`UNICODE_NON_NFC`はentryごとに1 findingとし、line／columnを持たない。
- `TEXT_ENCODING_UNSUPPORTED`はfileごとに1 findingとする。
- `CANONICAL_PATH_COLLISION`はpair数ではなくcollision groupに含まれる各entryを1回ずつ記録する。
- line／columnはUTF-16 code unitではなくUnicode code point単位の1-based値とする。
- findingは`detectorId`、`displayPath`、line欠落優先、line、columnの順にstable sortする。同一detector／path／line／columnの完全重複は1件に正規化する。

### 4. resource limitをfail closedで固定する

| 項目 | 上限 |
|---|---:|
| checkout entry | 50,000 |
| scan対象合計 | 256 MiB |
| 1 file | 4 MiB |
| finding総数 | 5,000 |
| scan wall-clock timeout | 60秒 |
| public response | UTF-8 4 MiB |
| escaped display path | UTF-8 1,024 bytes |

checkout entry上限は`.git`を除く全entryを数える。1 fileとaggregate bytesはallowlistに一致したregular-file candidateをbinary／encoding判定前にsizeで数える。これにより巨大fileを判定用として無制限にreadしない。

entry、bytes、file、finding、timeout、response、path上限を超えた場合、結果をtruncateしてbuildへ進めず、Jobをterminal failureとする。errorとlogへsource本文、raw unsafe path、absolute pathを含めない。

### 5. findingがあるJobだけ第二review gateで停止する

状態遷移:

```text
CLONING
  -> SCANNING_SOURCE
       -> finding 0: VERIFYING_WRAPPER
       -> finding 1以上: AWAITING_SCAN_REVIEW
       -> operational failure: FAILED
```

`AWAITING_SCAN_REVIEW`ではWrapper検証、dependency lock pre-hash、locale preflight、Gradleを開始しない。Androidはfinding summary／detailを表示し、利用者はJobごとにContinueまたは既存Cancelを選ぶ。

Continueは`POST /v1/jobs/{jobId}/source-scan/continue`へ、表示した`scanResultSha256`と`riskAcknowledged: true`を送る。digest不一致、別state、別Job、false acknowledgementは拒否する。Build AのreviewはBuild Bへ継承しない。retryは新Jobとして最初のRCE確認とscan reviewを再要求する。

findingはadvisoryであり、利用者がContinueしたJobを`FAILED`、`Different`、`INCOMPARABLE`へ自動変換しない。

### 6. compact Job summaryとbounded detail APIを分離する

API v1を維持し、`GET /v1/jobs/{jobId}`へnullable `sourceScan` summaryをadditiveに追加する。field欠落だけをlegacy Runnerとみなす。

finding detailは`GET /v1/jobs/{jobId}/source-scan`から返す。public schema v1は次だけを含む。

- Job ID
- full resolved commit SHA
- scanner version
- result SHA-256
- scanned file／byte、binary skip、symlink skip、finding count
- detector ID別count
- detector ID、escaped relative display path、content findingだけの1-based line／column

source本文、snippet、absolute path、checkout path、HOME、Runner state path、environment、credential候補を返さない。control／Bidiを含むpath componentはraw characterを返さず`U+XXXX`表記へescapeする。

`displayPath`はcheckout rootからのrelative pathだけとし、separatorを`/`へ固定する。absolute path、空component、`.`、`..`は拒否する。C0／C1 control、Bidi、invisible format code pointはASCIIの`<U+XXXX>`、non-BMPでは`<U+XXXXXX>`へ置換し、1,024 byte上限はescape後のUTF-8へ適用する。high-confidence token prefixなどcredential文字列を含むpathはraw値を公開せず、display path全体を`<redacted-path>`とする。error／logにもraw unsafe pathを含めない。

`line`と`column`はcontent位置を特定できるfindingで両方を必須とし、1以上とする。`CANONICAL_PATH_COLLISION`と`TEXT_ENCODING_UNSUPPORTED`のようなpath／file単位findingでは両方を省略する。片方だけの存在、0、負値は不正である。public schema v1と`reprodroid-static-v1`では未知detector IDを拒否し、既知IDへ丸めない。

scan resultはschema、scanner version、commit、summary、detector count、stable-sorted findingを含むcanonical UTF-8 JSONとして保存し、そのbyte列のSHA-256をreview digestとする。canonical schema v1はBOM／insignificant whitespaceなし、property order固定、optional field省略、lowercase hex、detector countとfindingのstable sortを要求する。Runnerはcanonical byte列そのものとdigestを保存する。Androidはcanonical JSONを再生成せず、Job summaryとdetailのdigest、Job ID、commit、counts、findingsの整合を検証する。

redaction後に同一`displayPath`となる別entry／occurrenceもfinding cardinalityから除外しない。stable sortは同一findingを保持し、永続ordinalで区別する。RunnerはSQLiteからresultを読むたびに、header／count／findingからcanonical byte列を再構成し、保存byte列と保存SHA-256の両方へ一致することを確認する。不一致は`SOURCE_SCAN_INVALID`であり、review済みJobも再開しない。

### 7. Runner SQLite v7とAndroid Room v13へ永続化する

Runner SQLite v7はscan header、detector count、finding、review state、reviewed digest／timestampをJobに関連付けてatomicに保存する。旧v1〜v6 Jobはscan evidenceなしとして保持する。Runner再起動後に`AWAITING_SCAN_REVIEW`とexact resultをregistry再scanなしで復元する。

findingありのJobはresultと`AWAITING_SCAN_REVIEW`を1 transactionで保存した後にworkerを解放する。Continueはreviewed digest／timestampと再開可能stateを1 transactionで保存してJobを再queueする。再開経路は既存workspaceを使用し、exact HEAD、clean tracked tree、untracked entryなし、保存済みresult digestを再確認する。sourceが変化していれば`SOURCE_SCAN_INVALID`とし、変化がなければ再scanせずWrapper検証から再開する。`SCANNING_SOURCE`中のRunner停止は`INTERRUPTED`とし、自動再scanしない。`AWAITING_SCAN_REVIEW`だけは同じresultで復元する。

result／review／stateのtransactionが失敗した場合はbuildを開始せず、別transactionで`SOURCE_SCAN_PERSISTENCE_FAILED`のterminal state保存を試みる。SQLite全面障害によりそれも失敗した場合は安全な固定errorだけを返し、memory上でも自動再開しない。durable terminal stateを書けないストレージ障害と、buildを開始しないfail-closed保証は区別する。

Android Room v13はJob単位scan header、detector count、finding、retrieved timestamp、review stateを保存する。`MIGRATION_12_13`は既存Job、Manifest、dependency、pinning、determinism、comparison、raw outcome、trust、install evidenceを変更せずscan tableを空で追加する。

正常fetchはheaderとdetailを一transactionで置換する。fetch／decode／integrity／storage failureは以前の正常scan evidenceを保持し、session-only warningとする。

findingありの未review evidenceは通常`AWAITING_SCAN_REVIEW`だけに存在する。利用者がそのJobをcancelした`CANCELLED`と、review永続化をterminal failureへ移した`FAILED`は`requiresReview = true`／`reviewed = false`を保持できる。`QUEUED`以降のbuild経路または`SUCCEEDED`が同じ未review shapeを返した場合、Androidは不正responseとして拒否する。

### 8. UI claimを制限する

Job detailはscanner version、scan対象数、finding総数、detector別count、path／line／column、review状態を表示する。comparison detailはBuild A／Build Bを別々に表示する。scanner versionまたはfinding差異からbuild原因を推測しない。

UIは`No configured detector findings`と表示できるが、`Safe`、`Malware free`、`Verified source`と表示しない。findingがある場合も`Malicious`と断定しない。

### 9. raw comparison、trust、update、installへ接続しない

scan evidenceはAPK comparator、protocol v2、Official-vs-A、Official-vs-B、A-vs-B raw outcome、`Reproducible` derivation、update relation、signer relation、install source、PackageInstaller gateを変更しない。

scanner operational failureはJobがbuildへ進めないためcomparisonが完了しないが、過去の完了済みcomparisonや保存済みtrustを別状態へ昇格・降格させない。

## Alternatives considered

### findingを表示するだけでbuildを直ちに続行する

Android polling前にGradleが開始される可能性があり、日常運用でreviewが形骸化する。findingがある場合だけ停止する条件付きgateを採用する。

### findingを自動的にJob failureとする

regex false positiveと正当なprocess API利用を区別できない。scanner operational failureだけをterminalにし、content findingは利用者reviewへ渡す。

### source本文やsnippetをAndroidへ返す

response肥大化、credential漏えい、control／Bidi再表示の危険がある。locationとdetector IDだけを返す。

### scan evidenceをRoomへ保存しない

cold start、Runner停止後の監査、Phase 4履歴／exportを満たせない。Room v13を採用する。

### external scanner／AI／LLMを呼び出す

binary trust、network、model version、privacy、resource capが別契約になる。Phase 3Dではin-process fixed detectorだけを使用する。

## Consequences

- Runner SQLiteはv7、Android Roomはv13になる。
- API v1へ2状態、compact summary、detail endpoint、continue endpointをadditiveに追加する。
- findingがあるreal Jobは利用者reviewまでGradleを開始しない。
- Build A／Build Bで追加操作が発生し得る。
- scanは永続的な補助証跡になるが、安全性判定にはならない。
- Phase 3E Docker feasibilityとPhase 4 operational historyはこのscan evidenceを再利用できる。

## Verification

実装前test名、API例、migration、product-path acceptanceと生の検証結果は非公開の実装記録へ保持する。Runner SQLite v7、Android Room v13、自動test、Android 16 migration、MicroG-RE Build A／B、Runner restart、cold start、closeout cleanupを2026-08-29に確認した。公開時点の検証状態は[Current status](../status/current.md)で管理する。
