# ADR-0014: dependency pinning recipe contract

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted; implemented 2026-08-28
- Date: 2026-08-27
- Scope: Phase 3B

## Context

Phase 3A は、Runner が実際に取得した dependency file の name と SHA-256 を redacted public Manifest として返し、Android が Build A / B の差分を補助証跡として表示する境界までを実装した。これは事後の説明可能性を追加するが、build 前の dependency 解決を固定するものではない。

Runner の `REAL_TRUSTED` build は、allowlist、固定 recipe、resolved commit と RCE の Job 単位確認、detached checkout、Wrapper 検査を要求する。さらに、Job ごとに新しい workspace、HOME、`GRADLE_USER_HOME`を作成し、過去 Job の cache 混入を防いでいる。Runner 全体で共有する Gradle cache は ADR-0006 で採用していない。

Gradle の lockfile は dependency artifact 自体を含まない。空の Job 固有 cache で `--offline`を指定した場合、repository 内の入力だけで解決できない外部 dependency は取得できず、build は失敗する。3B で共有 cache、cache seed、署名済み cache bundleまで追加すると、artifact の供給元、完全性、更新単位、plugin cache、Job 間分離を別途設計する必要がある。

また、`gradle.lockfile`が存在するだけでは、すべての project / configuration が dependency locking を有効化していることや、任意コードである build script / plugin が独自通信を行わないことまでは証明できない。3B は lockfile の存在・不変性と Gradle dependency resolution option を固定する小フェーズであり、sandbox や完全な network isolation を導入するフェーズではない。

## Decision

### 1. 固定 recipe に3段階の pinning levelを追加する

Runner 内部の `BuildRecipe` にcode field `dependencyPinning`を追加する。文書上のrecipe keyは`dependency_pinning`と表記し、次の値だけを許可する。

| Recipe value | API value | Runner が要求する条件 |
|---|---|---|
| `none` | `NONE` | Runner 独自の lockfile 検査を行わず、`--offline`も追加しない |
| `lockfile` | `LOCKFILE` | `buildRoot/gradle.lockfile`の事前・事後 integrity を検査する。Gradle dependency resolution の network 使用は許容する |
| `lockfile_offline` | `LOCKFILE_OFFLINE` | `lockfile`と同じ検査に加え、固定 Gradle invocation に`--offline`を追加する |

Android request、Job create request、confirm requestからpinning levelやGradle optionを指定させない。Runnerのallowlistに登録された固定recipeだけが値を所有する。

新規recipeのcode上の既定値は`lockfile_offline`とする。ただし、Phase 3B以前から存在する次の2 recipeは、後方互換とlockfile不在を理由に明示的な`none`とする。

- `morpheapp-microg-re-main-debug`
- `morpheapp-microg-re-6.1.4-default-release`

`none`は「対象projectがGradle lockingを一切使っていない」という観測結果ではなく、「Runner recipeが3Bのlockfile integrity policyを要求しない」という意味に限定する。

### 2. lockfileの検査対象と上限を固定する

`lockfile`または`lockfile_offline`では、対象を`buildRoot/gradle.lockfile`の1 fileに限定する。任意path、subproject別lockfile、legacy lockfile、`gradle/verification-metadata.xml`は3Bの対象に含めない。

RunnerはGradle開始前とGradle process終了後に、少なくとも次を検査する。

1. fixed `buildRoot`から正規化したpathがsource checkout内に留まること。
2. lockfileが存在すること。
3. symbolic linkではないregular fileであること。
4. file sizeが8 MiB以下であること。
5. SHA-256を上限付きで読み取れること。
6. post-build時にも同じpathがnon-symlink regular fileであること。
7. pre-build SHA-256とpost-build SHA-256が一致すること。

空fileをRunner独自に拒否しない。0 dependencyのprojectやGradle固有のlockfile表現をRunnerが独自解釈しないためである。lockfileの内容、module名、repository、versionをRunner側parserで再解釈しない。

post-build検査はGradle exit 0だけでなく、Gradle non-zero、起動失敗、timeout後にもworkspaceが検査可能な限り実行する。Gradle成功後にlockfileの欠落、symlink化、file type変更、size超過、SHA-256相違を検出した場合、artifact discovery、Manifest保存、Job成功登録へ進まない。Gradleが失敗した場合も、lockfile変更を監査ログとprivate audit値へ残す。

pre-buildと同一bytesへ置き換えられたfileは、SHA-256一致条件を満たすものとして受理する。file identityやinodeの同一性までは3Bの契約にしない。

### 3. `lockfile_offline`はGradle dependency resolution optionに限定する

`lockfile_offline`では、Runnerが直接起動する`org.gradle.wrapper.GradleWrapperMain`の固定optionとして、recipe taskより前に`--offline`を1回だけ追加する。Android入力やrepository内scriptからこの値を組み立てない。

`--offline`は次を意味しない。

- Git clone / ref resolutionのnetwork遮断
- Gradle distribution / Wrapper公式checksum取得のnetwork遮断
- build script、plugin、annotation processor等の任意通信の遮断
- Runner processまたはhost全体のnetwork namespace isolation
- dependency artifactの署名や`verification-metadata.xml`による完全性検証

3Bでは共有cache、host cache参照、cache seed、cache bundle、Job間cache複製を追加しない。新しい空のJob固有`GRADLE_USER_HOME`またはrepository内入力だけでdependencyを解決できない場合、`lockfile_offline` buildは`GRADLE_BUILD_FAILED`としてfail closedになる。

Gradle出力文字列を解析して「offline dependency不足」とコンパイル失敗等を推測しない。Gradle processのnon-zeroは既存の`GRADLE_BUILD_FAILED`を維持する。

### 4. lockfileの存在を完全なdependency coverageとは表示しない

Runnerは3BでGradle init scriptを注入せず、全project / configurationへdependency lockingを強制しない。recipe登録時のreview対象にはできるが、runtimeで完全coverageを証明したとは扱わない。

Android UIの表示は保証範囲に合わせ、次を基準とする。

| API value | 表示 |
|---|---|
| `NONE` | `None` |
| `LOCKFILE` | `Lockfile checked` |
| `LOCKFILE_OFFLINE` | `Lockfile checked · Gradle offline resolution` |

`All dependencies verified`、`Network isolated`、`Supply chain safe`等の断定は使用しない。pinning levelは補助的なbuild条件であり、source、build script、plugin、dependencyの安全性を証明しない。

### 5. Runner SQLite v5にeffective policyとprivate audit値を保存する

Runner SQLite schemaをv4からv5へtransactionalに移行し、Jobに次の値を保存する。

- `effective_dependency_pinning`: fixed recipeから選択したlevel
- `dependency_lock_pre_sha256`: pre-build検査を通過したlockfile SHA-256。非lock modeまたは未到達ではnull
- `dependency_lock_post_sha256`: post-build検査で読めたSHA-256。非lock modeまたは未到達ではnull

既存rowは`effective_dependency_pinning = NONE`としてbackfillする。effective levelはref解決時に他のeffective recipe値と同じtransactionで保存し、Runner再起動後に現在のrecipe registryから推測し直さない。

pre/post hash、lockfile path、lockfile content、Gradle cache pathはpublic APIへ返さない。hashはRunner SQLiteと監査ログに限定する。private Build Environment Manifest internal schema v2とpublic Manifest schema v1は3Bでは変更しない。

audit値の永続化に失敗した場合はfail closedとし、Job成功へ進まない。

### 6. API v1の`effectiveBuild`へadditive fieldを追加する

`GET /v1/jobs/{jobId}`の`effectiveBuild`へ次を追加する。

```json
{
  "effectiveBuild": {
    "recipeId": "morpheapp-microg-re-6.1.4-default-release",
    "variantName": "defaultRelease",
    "buildRoot": ".",
    "javaMajor": 18,
    "tasks": ["clean", ":play-services-core:assembleDefaultRelease"],
    "dependencyPinning": "NONE"
  }
}
```

Runnerは`NONE`を含め、`effectiveBuild`が存在するresponseでは`dependencyPinning`を常に明示する。serialization defaultによるfield欠落を許容しない。

Android clientはPhase 3B以前のRunnerとの互換性のため、field欠落をlegacy `NONE`として受理する。未知の値を`NONE`へ丸めず、Job response不正として扱う。pinning levelはcommit / RCE確認前のfixed build表示、Job detail、comparison detailに使用する。

API versionはv1のままとし、Job create / confirm / retry requestへfieldを追加しない。

### 7. Android Room v11にJob値とcomparison snapshotを保存する

AndroidはRoom v10からv11へmigrationし、次を追加する。

- `jobs.effectiveDependencyPinning`: Job detail用のeffective level
- `comparison_runs.runnerDependencyPinning`: Build Aのrun snapshot
- `comparison_runs.repeatRunnerDependencyPinning`: Build Bのrun snapshot

すべての既存rowは`NONE`としてbackfillする。Build A / Bのsnapshotは、comparison履歴をJobの将来状態やRunner registry変更から独立した証跡として表示するために保持する。

pinning levelをtarget identity gateやprotocol v2の入力に追加しない。Build A / Bでlevelが異なる場合も、その事実を補助説明として表示するだけで、raw outcome、comparison status、trust、update relation、signer relation、install source、PackageInstaller policyを変更しない。

### 8. error codeを固定する

lockfile固有のterminal failure codeは次とする。

| code | 条件 |
|---|---|
| `DEPENDENCY_LOCKFILE_MISSING` | lock modeで`buildRoot/gradle.lockfile`が存在しない |
| `DEPENDENCY_LOCKFILE_INVALID` | path confinement、non-symlink regular-file、read条件が成立しない |
| `DEPENDENCY_LOCKFILE_TOO_LARGE` | 8 MiBを超える |
| `DEPENDENCY_LOCKFILE_CHANGED` | post-buildで欠落、不正化、size超過、またはpre/post SHA-256不一致を検出した |
| `DEPENDENCY_LOCK_AUDIT_PERSISTENCE_FAILED` | effective levelまたはpre/post audit値をSQLiteへ保存できない |

private absolute pathやlockfile contentをAndroid向けerror messageへ含めない。Gradle non-zeroは原因を推測せず`GRADLE_BUILD_FAILED`を使用する。

### 9. verificationはfixtureと既存MicroG-RE product pathを分離する

`lockfile` / `lockfile_offline`のpositive / negative behaviorはRunner unit / integration fixtureで検証する。Phase 3Bのために新しい本番allowlist profileを追加しない。

MicroG-RE `6.1.4`のproduct-path E2Eでは、Build A / Bがそれぞれ`NONE`としてAPI、Room、UIに保存・表示され、既存のraw 3軸と`Reproducible`が変化しないことを確認する。これはlock modeの実Gradle成功を証明する試験ではない。

lock modeを使用したproduct-path E2Eは、lockfileとdependency供給を持つ別の固定target、またはverified cache設計が別途Acceptedになった場合だけ追加する。

## Alternatives considered

### Runner全体のGradle cacheを共有してoffline buildを成立させる

Job間のcache poisoning、dependency Manifestへの過去Job混入、artifactの供給元と更新単位が曖昧になる。ADR-0006の分離境界を3Bで崩さない。

### Host cacheをJobの`GRADLE_USER_HOME`へコピーする

コピー元の完全性、plugin cache、metadata、同時更新、容量上限を定義できない。verified cache bundleを設計する場合は別ADRとする。

### Gradle init scriptで全configurationのlockingを強制する

multi-projectのlockfile配置、plugin解決、configuration作成時期、Gradle version差を含む別の実行意味をRunnerが注入することになる。3Bではrepository側のlocking構成を上書きしない。

### lockfileの内容をRunner独自parserで検証する

Gradle固有の意味を不完全に再実装し、対応versionやmulti-project semanticsを誤判定する。3Bはfile integrityとfixed invocationに限定する。

### pinning level差異でcomparisonを`INCOMPARABLE`にする

protocol v2の真実は3軸raw outcomeであり、pinning levelは補助的なbuild条件である。既知のraw結果をpinning evidenceで暗黙変換しない。

## Consequences

- 新規recipeは明示しない限り`lockfile_offline`となり、dependencyをJob固有cache内で解決できない場合に安全側へ失敗する。
- 既存MicroG-REの2 recipeは`NONE`を明示するため、Phase 3B実装だけで従来build挙動は変わらない。
- Runner SQLiteはv5、Android Roomはv11になる。
- API v1はadditive fieldだけを追加し、Androidからpinning policyを変更できない。
- lockfile変更や削除はartifact生成後であってもJob成功として登録されない。
- UIはlockfile checkとGradle offline resolutionを表示できるが、完全なdependency coverage、network isolation、安全性を断定しない。
- private/public Build Environment Manifest schemaは3Bでは変更しない。
- protocol v2、raw comparator、trust、update、install policy、Build A / Bの独立JobとRCE確認は変わらない。

## Verification result

実装前のnegative test名、実装・自動test・Android 16 migration・MicroG-RE product-path E2Eの生の結果は非公開の実装記録へ保持する。公開時点の検証状態は[Current status](../status/current.md)を正本とする。
