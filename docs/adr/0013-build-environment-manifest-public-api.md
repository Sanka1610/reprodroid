# ADR-0013: Build Environment Manifest 公開 API と機密境界

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-27
- Scope: Phase 3A

## Context

Runner は `REAL_TRUSTED` Job の成功時、wrapper 検証、Java、Gradle、SDK root、dependency cache file、artifact などを含む private な `reprodroid-build.json` を state directory に保存している。これは監査用の真実の源であり、現行 API では Android に公開しない。

Phase 2D は APK 内部の差異を説明する境界であり、Build Environment Manifest public API を意図的に追加しなかった。Phase 3A では、同一 source commit からの build が異なる場合に dependency hash の差分を補助証跡として示す必要がある。

private Manifest には SDK root、workspace / cache 相対 path、OS detail、environment 由来の値を含み得る。これらを Android API にそのまま返すと、利用者の PC 構成や token 様文字列を不要に露出する。さらに、現行 `androidSdk` は path であり、公開に必要な semantic な Android SDK API level と Build Tools version はまだ独立 field として記録されていない。

## Decision

### 1. Manifest は private audit record と public projection に分離する

Runner state directory の `reprodroid-build.json` は private audit record のまま保持する。Android は raw file を download せず、Runner が integrity 検査と redaction を完了した public projection だけを受け取る。

public projection の field は次に限定する。

| Field | 公開値 |
|---|---|
| `commit` | full resolved commit SHA |
| `java` | build Java version と vendor |
| `gradle` | verified Gradle version |
| `androidSdk` | semantic Android SDK API level。SDK root path ではない |
| `buildTools` | semantic Build Tools version |
| `dependencies` | file name と lowercase SHA-256 の組 |
| `apkHash` | fixed recipe が発見した APK の SHA-256 |

次は public projection、Android Room、Android UI のいずれにも入れない。

- filesystem path、workspace、HOME、Gradle user home、state directory
- environment variable value、token 様文字列、repository credential
- OS detail、Gradle task、build root、Wrapper URL、raw artifact path
- dependency cache 内の relative path

### 2. internal Manifest schema を v2 に拡張する

Runner は private Manifest に `androidSdkApiLevel` と `buildToolsVersion` を追加する。これらは recipe に固定した値とし、Runner は Gradle 開始前に SDK root 配下の対応する platform / Build Tools package を path confinement、non-symlink regular file / directory 条件で検査する。値の意味は「recipe 実行条件として Runner が検証した SDK package」であり、AGP が内部で選択したすべての tool version を推測した値ではない。SDK root path や、単に install 済みの最大 version を semantic version の代替として使わない。

private Manifest は internal schema v2、public projection は独立した public schema v1 とする。既存 internal schema v1 の Manifest は audit record として残し、public endpoint は必要な public field を安全に導出できない旧 record を公開可能と推測しない。

内部 JSON の schema version 変更は Runner SQLite schema v4 の変更を要求しない。SQLite は引き続き manifest relative path と file SHA-256 だけを監査値として持つ。

### 3. API v1 へ additive endpoint を追加する

Phase 3A 実装後、Runner は次を提供する。

```text
GET /v1/jobs/{jobId}/build-environment-manifest
```

これは API v1 の additive endpoint であり、既存 Job create / get / confirm / log / artifact endpoint の request / response を変更しない。詳細な JSON と error contract は [Runner API v1](../api/runner-api.md) に記録する。

Runner は response 前に、少なくとも次を検査する。

1. Job が存在すること。
2. Job が `SUCCEEDED` であり、manifest audit record があること。
3. manifest path が state directory 配下の non-symlink regular file であること。
4. manifest の current SHA-256 が SQLite に保存した SHA-256 と一致すること。
5. private Manifest が 32 MiB 以下であり、JSON が strict decode できること。
6. internal schema v2、full lowercase commit SHA、lowercase SHA-256、recipe 検証済み SDK 値、単一 APK artifact など public field の integrity が成立すること。
7. public projection が size / entry limit 内であり、redaction 後に public data が残ること。

path escape、symlink、SHA-256 不一致、decode failure、公開上限超過は raw file を返さない。Android はこれらを warning として扱い、trust を変更しない。

### 4. dependency projection は path を返さず、曖昧な変更を推測しない

dependency は file name と SHA-256 の multiset として public projection に含める。public API は dependency path、Gradle group / module path、cache directory を返さない。projection は path 順を引き継がず、`fileName`、`sha256` の順で安定 sort する。

file name は internal relative path の basename から導出し、path separator、NUL、ASCII / Unicode control、Bidi control を許可しない。credential を示す明示語または既知 token prefix を含むなど、安全な public file name と判定できない record が一つでもある場合、その record だけを黙って削除しない。dependency 集合が完全であるという誤解を避けるため Manifest 全体を `410 BUILD_MANIFEST_REDACTION_REQUIRED` として公開不能にする。error response と log に該当 file name を含めない。

同じ file name が両 Manifest 内で一意に対応する場合だけ、SHA-256 の相違を `changed` と説明できる。同名が複数あり対応が一意でない場合は `changed` を推測せず、`added` と `missing` に分ける。表示は「dependency difference detected」であり、差異の唯一の原因と断定しない。

公開 surface を有限に保つため、private Manifest read は最大 32 MiB、public projection は最大 20,000 dependency record、dependency file name 255 UTF-8 byte、serialized response 8 MiB とする。serialized size は UTF-8 byte 数で判定する。超過時は `409 BUILD_MANIFEST_PUBLICATION_LIMIT_EXCEEDED` とし、Android は取得不能 warning を表示する。build 自体、comparison outcome、trust は変えない。

### 5. Android は Room v10 に Job 単位で保存する

Android は次の新 table を Room v10 に追加する。

- `build_environment_manifests`: `jobId` を主キーかつ `jobs(jobId)` への foreign key とし、public Manifest schema version、commit、Java、Gradle、SDK API level、Build Tools version、APK SHA-256、取得時刻を保存する。
- `build_environment_dependencies`: `jobId + ordinal` を主キーとし、redacted file name と SHA-256 だけを保存する。`ordinal` は同一の public record が複数ある場合の永続化 identity であり、path を復元するために用いない。

取得成功時は `fileName`、`sha256` で安定 sort した dependency に ordinal を付け、header と dependency rows を一つの transaction で置換する。取得失敗は既存の正常保存済み record を削除せず、warning を session UI state として扱う。warning code の履歴は Room に永続化せず、正常取得した Manifest だけを永続証跡とする。cold start 後に record がなければ `Manifest not available` と表示し、refresh で再取得する。`MIGRATION_9_10` は既存 Job、comparison run、protocol v2、raw outcome、trust を変更せず、新 table を空で追加する。

### 6. dependency 差分は補助説明に限定する

Android は現行 protocol v2 comparison に結び付いた Build A / Build B だけを 3A の Manifest 比較候補にする。canonical repository URL と full resolved commit SHA が同じ場合だけ dependency diff を生成する。任意の履歴 Job pair を選択する UI は 3A に追加しない。effective recipe、variant、Java が異なる場合は、その事実を併記して差分を原因と断定しない。

diff は file name ごとの multiset とし、同じ file name / SHA-256 の件数を先に相殺する。両 Manifest で file name が一意な場合だけ残った SHA-256 相違を `changed` とする。同名 record が複数ある場合は `changed` と推測せず、Build A only / Build B only として方向を明示する。

dependency 差分の有無、Manifest の欠落、endpoint failure は raw 3軸 outcome、comparison status、trust、install source、PackageInstaller policy を一切変更しない。

## Alternatives considered

### private `reprodroid-build.json` をそのまま Android へ配信する

SDK root、workspace、cache path、environment 由来の情報を漏らすため採用しない。Android は Runner state directory を真実の源として扱わない。

### Runner SQLite に Manifest 全文を保存する

既存 file audit boundary を変え、SQLite schema v4 の変更と raw secret の複製を招く。private file を真実の源として維持する。

### dependency path を公開して同名 artifact を完全に識別する

path redaction の決定と矛盾し、利用者の filesystem / cache layout を公開する。曖昧な場合は保守的な `added` / `missing` 表示を採る。

### Manifest / dependency 差分で `Different` を説明可能な `Reproducible` へ戻す

環境差分の記録は raw APK comparison の真実を置き換えない。protocol v2 の truth table を保つ。

## Consequences

- Runner は public projection 用の Manifest schema / integrity / redaction test を持つ。
- Android は Room v10、`Migration9To10Test`、Manifest warning UI、dependency diff UI を持つ。
- API v1 の既存 client は endpoint を呼ばない限り互換である。
- Manifest が取得できなくても既存 comparison / install flow は継続できる。
- Phase 2D の「Manifest public API を追加しない」決定はその Phase の境界として維持され、Phase 3A が別 ADR で新しい公開境界を追加する。

## Verification requirements

- Runner unit test: successful redacted projection、決定論的 multiset sort、404、manifest 未生成 / Job 未完了の 409、publication limit の 409、internal schema v1 / redaction required の 410、path / token / environment value 非露出、symlink / path escape / SHA mismatch / malformed JSON / field integrity / size limit 拒否、既存 API v1 response の非変更。
- Android unit test: success transaction 保存、失敗時の既存 record 保持、404 / 409 / 410 / 500 / response integrity failure を warning として扱い、既存 trust を変更しないこと、同名重複を `changed` と推測しないこと。
- Android instrumentation test: `Migration9To10Test` が v9 の protocol v2 run を不変で保持し、新 table を空で追加する。
- E2E: MicroG-RE `6.1.4` の Build A / B から public projection を取得・保存し、dependency diff 表示と raw 3軸 `Reproducible` が独立していることを確認する。
