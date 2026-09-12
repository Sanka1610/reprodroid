# ADR-0012: Phase 2Dの独立再ビルドと高度比較境界

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-24
- Scope: Phase 2D

## Context

Phase 2Bは、MicroG-RE `6.1.4`の公式APKと、同じtag／full commit SHA／固定release recipeからRunnerが1回生成したBuild Aについて、`classes*.dex`と`lib/<abi>/*.so`の非圧縮bytesを比較した。Phase 2Cはこの結果を現在のrelease identityへ限定してtrust、update、install policyへ統合した。

しかし、公式APKと1回のlocal buildが一致しても、同じsourceとrecipeからもう一度同じartifactを生成できるとは限らない。dynamic dependency、外部repositoryの変化、ビルド時刻、環境入力、D8／R8等の非決定性により、Build Aだけが偶然一致する可能性がある。また、byte不一致の位置と意味を分類する契約がなければ、将来のDEX構造比較がraw差異を暗黙に隠す危険がある。

Phase 2Dは一括実装せず、独立再ビルド、APK内部差異分類、DEX構造比較、Manifest／resources正規化の順に小フェーズへ分割する。

## Decision

### 1. 自己再ビルドを独立したBuild A／Build Bとして定義する

Phase 2Dの「自己再ビルド」は、公式APKと1回のlocal buildを比較することではない。Runnerへ同じrepository、tag、full commit SHA、recipe、variant、Java major、build root、tasksを持つ独立Jobを2件作成し、それぞれBuild A、Build Bを生成することと定義する。

各Jobは既存の安全境界を維持する。

- tagをRunnerが独立に再解決し、Android保存full SHAと一致しなければ続行しない
- Jobごとに解決済みcommitとhost RCE riskの明示確認を要求する
- clone、HOME、Gradle user home、artifact、Build Environment ManifestをJob単位で分離する
- Runnerの`SUCCEEDED`は各buildの成功だけを表し、再現性結果をRunner stateへ混ぜない
- 初期profileはMicroG-RE `6.1.4`だけに固定する

Phase 2D-1では既存Runner API v1を使って2件のJobを作成する。batch build APIや確認継承は追加しない。

### 2. 3つの比較軸を別々に保存する

Androidは次を同じcomparison runへ関連付けるが、結果を単一のbooleanへ潰さない。

| 軸 | 意味 |
|---|---|
| 公式APK vs Build A | 配布APKと1回目のsource buildの対応 |
| 公式APK vs Build B | 配布APKと独立した2回目のsource buildの対応 |
| Build A vs Build B | local buildの反復可能性 |

Phase 2D-1の比較範囲はPhase 2Bと同じ`classes*.dex`と`lib/<abi>/*.so`であり、raw APK全体、ZIP metadata、Manifest、resources、assets、signerは含まない。

新規comparison runはprotocol v2として保存する。Room v7以前から引き継ぐ1回比較はprotocol v1へbackfillし、過去の`MATCH`を遡及的に無効化しない。

### 3. Exact Reproducibleは3軸すべてのraw一致を要求する

protocol v2の`Reproducible`は次をすべて満たす場合だけ成立する。

- 対象同一性gateがBuild A、Build Bの両方で成立
- 公式APK vs Build Aが`MATCH`
- 公式APK vs Build Bが`MATCH`
- Build A vs Build Bが`MATCH`

1軸でも比較成立後の追加、欠落、size／SHA-256不一致があれば`Different`とする。すでに同一対象の差異が確定している場合、別軸の取得不能やbuild失敗で既知の`Different`を`Incomparable`へ弱めない。

Build Aが`MATCH`でもBuild Bの対象同一性、artifact、ZIP安全性等を確認できなければ`Incomparable`とする。Build BのRunner Job自体が失敗し、ほかの軸で差異が確定していない場合は`Failed`とする。Build A完了後、Build Bの証跡が揃うまでは`Buildable`とする。

### 4. DEX構造一致はraw差異を上書きしない

Phase 2D-3ではraw DEXが異なる場合に限り、class、field、method、instruction等の構造比較を補助証跡として実行する。初期の構造一致は`Structurally equivalent`に相当する説明情報であり、raw比較の`Different`を現在の`Reproducible`へ昇格させない。

構造比較は少なくとも次を明示的に定義してから実装する。

- class descriptor、field signature、method prototypeを安定keyとして使う
- table indexを参照先descriptor／signatureへ解決する
- multidex内の配置順、class／methodの格納順、debug line情報を比較意味から分離する
- access flag、annotation、encoded value、try/catch、method implementationを別証跡として保持する
- register、branch target、payloadをcanonical formへ変換する規則を固定する
- malformed DEX、未知version／opcode、上限超過をfail closedで`INCOMPARABLE`にする

### 5. APK正規化対象と順序を固定する

Phase 2Dの最終比較対象は次とする。

| 対象 | 方針 |
|---|---|
| `classes*.dex` | raw比較を維持し、後続で構造比較を補助追加 |
| `lib/**/*.so` | raw bytes比較を維持 |
| `AndroidManifest.xml` | binary XMLの意味比較を後続追加 |
| `resources.arsc` | resource tableの意味比較を後続追加 |
| `res/**` | entry単位raw bytes比較 |
| `assets/**` | entry単位raw bytes比較 |
| 署名情報 | content outcomeと分離した証跡 |
| ZIP timestamp、圧縮方式、entry順 | 比較意味から除外 |

native libraryのbuild ID、debug section等は自動除外しない。意味のある差異を隠す可能性があるため、Phase 2Dではraw comparisonを維持する。

「ZIP展開後の比較」はfilesystemへの展開を意味しない。既存どおり`ZipFile`から非圧縮entry bytesをstreamingで読み、path traversal、重複entry、未知size、unsupported method、宣言／実読込size不一致、展開量上限、保存後改ざんを検査する。

### 6. 差異分類は当面APK内部に限定する

Phase 2D-2の初期分類は次を対象とする。

- signature／archive metadata
- DEX content
- native code
- Manifest
- resource table
- resource file
- asset
- other entry
- entry added／missing
- unsupported／malformed

dependency hash、Java、Gradle、SDK、OS等のbuild原因推定は今回扱わない。RunnerはBuild Environment Manifestを引き続き生成・保存するが、Androidへ公開するAPIは追加しない。原因推定へ進む場合は、Manifestの機密情報、path、完全性、互換性を別ADRとAPI変更で定義する。

### 7. Phase 2Dを小フェーズへ分割する

1. **Phase 2D-0: 判定契約** — 本ADR、状態、比較軸、trust、正規化境界
2. **Phase 2D-1: 独立再ビルド** — Build A／B、3軸raw比較、Room v8、UI統合
3. **Phase 2D-2: APK内部差異inventory／分類**
4. **Phase 2D-3: DEX構造比較**
5. **Phase 2D-4: Manifest／resources正規化**

### 8. Phase 2D-2〜4の実装契約

Androidは3軸ごとに、raw DEX／native比較とは別の高度比較を実行する。APKはfilesystemへ展開せず、アプリ専用領域内の通常fileだけを`ZipFile`でstreaming処理する。全entryを次の優先順で一意分類する。

1. `AndroidManifest.xml`: Manifest
2. `resources.arsc`: resource table
3. `classes*.dex`: DEX
4. `lib/<abi>/*.so`: native code
5. `META-INF`の署名関連entry: signature
6. `res/**`: resource file
7. `assets/**`: asset
8. その他: other

`apk_entry_evidence`は`comparisonRunId + axis + entryName`を主キーとし、分類、added／missing／hash mismatch／match、左右の非圧縮size、CRC、圧縮方式、非圧縮SHA-256を保存する。content outcomeは非圧縮sizeとSHA-256だけで決め、ZIP timestamp、entry順、圧縮方式は一致条件にしない。CRCと圧縮方式の変化はarchive metadata証跡として保持する。

`advanced_comparison_summaries`は軸ごとのinventory、DEX構造、Manifest意味、resource table意味のoutcomeとreason、entry集計を保存する。`semantic_difference_evidence`は`comparisonRunId + axis + component + stableKey`を主キーとし、semantic itemのadded／missing／differentと左右hashを保存する。Room schemaはv9とし、`8 -> 9` migrationは既存protocol v2 runとraw outcomeを変更せず、新tableを空で追加する。

安全上限は次で固定する。超過、整数overflow、timeout、malformed入力は部分的な`MATCH`にせず、理由code付き`INCOMPARABLE`とする。

| 項目 | 上限 |
|---|---:|
| entry名 | 1,024文字 |
| APK内entry数 | 100,000 |
| 1 entryの非圧縮size | 512 MiB |
| 1 APKの実読込総量 | 1 GiB |
| 1 APKの宣言総展開量 | 4 GiB |
| semantic対象1 entry | 64 MiB |
| semantic対象総量 | 64 MiB |
| DEX class | 200,000 |
| DEX field + method | 2,000,000 |
| DEX instruction | 20,000,000 |
| semantic item | 2,000,000 |
| semantic stable key | 8,192文字 |
| 高度比較全体 | 180秒 |

entry名のabsolute path、空segment、`.`、`..`、backslash、control character、重複、unknown size、`STORED`／`DEFLATED`以外の圧縮方式、宣言size／CRCと実読込の不一致を拒否する。入力APKのsymlink、アプリ専用root外path、期待size／SHA-256不一致も拒否する。

DEXは`smali-dexlib2 3.0.9`で読み、multidex内配置、class／member格納順、table index、source file、debug lineを比較意味から外す。class descriptor、field descriptor、method descriptorを安定keyとし、access flag、interface、annotation、encoded value、parameter annotation、try/catch、method implementationをhash化する。register count／番号は明示的なcanonical値として維持し、branch／handler／try rangeはinstruction ordinalへ、referenceはdescriptor／signatureへ、switch targetはinstruction ordinalへ変換する。array payloadはelement widthと値を維持する。未知opcode、重複class、不正target、orphan／共有switch payload、上限超過は推測せず`INCOMPARABLE`にする。raw DEX一致時は構造parserを起動しない。

Manifestとresource tableは`ARSCLib 1.4.0`でbinary表現をJSON意味表現へ変換する。Manifestはline、comment、prefix、encodingを除外し、object keyと順序非依存arrayをcanonical化する。namespace URI、element、attribute、値は維持する。resource tableはpackage ID／数値IDではなくpackage name、type、entry name、configuration qualifierを安定keyとし、entryの意味hashを保存する。table全体のcanonical document hashも保持し、overlayable、staged alias等のentry外差異を取り落とさない。`res/**`、`assets/**`、native libraryは非圧縮raw bytesを維持し、native build IDやdebug sectionを除外しない。parser失敗や未知表現は意味一致を推測しない。

UIはraw 3軸outcome、inventory集計、DEX／Manifest／resource semantic outcome、reason、安定key差異を分離して表示する。高度比較の`MATCH`は説明用であり、raw `Different`、`Incomparable`、trustを上書きしない。

## Alternatives considered

### Phase 2Bの1回buildを自己再ビルドとみなす

独立した反復可能性を測れず、Phase 2DをPhase 2Bの再実行にするため採用しない。

### Runner内で2回buildする単一batch Jobを追加する

1回の確認で複数回Gradleを実行する新しいRCE／API境界が必要になる。初期実装では既存のJob単位確認と監査を再利用する。

### DEX構造が一致すればraw差異を`MATCH`へ変更する

構造比較が無視するmetadata、annotation、exception処理、未知opcode等の意味を完全には証明できない。raw差異を隠すため採用しない。

### APKを一時directoryへ展開して正規化する

path traversal、symlink、cleanup、disk使用量の攻撃面を増やす。streaming entry処理を維持する。

### Phase 2DでBuild Environment Manifest APIも追加する

今回合意した差異分類はAPK内部に限定される。不要なAPI／情報公開境界を増やすため採用しない。

## Consequences

- Phase 2D protocol v2のgreen判定はPhase 2Cより強くなり、独立2回buildを要求する
- 既存protocol v1の履歴は維持されるが、UIは新旧protocolを区別できる
- 2回目にもhost RCE確認とbuild時間が必要になる
- dynamic dependencyや時刻依存等によるlocal非決定性を、公式APK差異と分離できる
- DEX構造一致は説明力を増すが、raw差異を安全性の高い一致へ変えない
- Runner API v1とSQLite v4はPhase 2D-1で変更しない
- AndroidはPhase 2D-1でRoom v8へ移行し、Phase 2D-2〜4でRoom v9へ移行して全entryとsemantic差異を保存する

## Verification requirements

- Room `7 -> 8` migrationで既存comparisonをprotocol v1として維持する
- 新規runがBuild A完了後に独立したBuild B Jobを作成する
- Build Bもtag/full SHA、recipe、variant、Java、root、taskの対象同一性gateを通る
- Build BもJob単位のcommit／host RCE確認前に開始しない
- 公式対A、公式対B、A対Bのentry結果を軸別に保存する
- 3軸全一致だけをprotocol v2の`Reproducible`へする
- A対B差異、公式対いずれかの差異を`Different`へする
- repeat build／artifact／ZIP検査不能を理由付き`Incomparable`または`Failed`へする
- 既知の`Different`を後続軸の比較不能で弱めない
- force-stop／cold start後もrepeat Job、確認状態、3軸結果、trustを復元する
