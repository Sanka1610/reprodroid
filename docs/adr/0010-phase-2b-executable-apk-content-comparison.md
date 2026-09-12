# ADR-0010: Phase 2Bの実行コード比較と対象同一性境界

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-24
- Scope: Phase 2B

## Context

Phase 2Aは、public GitHub Releasesから参照APKをAndroidアプリ専用領域へ取得し、release tagをfull commit SHAへ解決して保存した。ただし、MicroG-REの既存Runner recipeは`main`の`defaultDebug`をJDK 21でビルドするものであり、tag `6.1.4`の公式release workflowが使う`defaultRelease`とTemurin 18には対応していなかった。この状態は比較対象が揃っていないため`INCOMPARABLE`であり、内容差異を意味する`Different`ではない。

Phase 2Bでは、署名方式、ZIP metadata、resource差異をAPK全体のraw hashへ混ぜず、同一releaseを対象にローカル生成APKの実行コードを比較する必要がある。同時に、悪意ある、または壊れたZIPによるpath traversal、重複entry、展開量攻撃をAndroid側でfail closedに扱う必要がある。

## Decision

### 1. 初期comparison profileをMicroG-RE `6.1.4`へ固定する

Phase 2Bで許可するcomparison profileは、次の1件だけとする。

- repository: `https://github.com/MorpheApp/MicroG-RE`
- requested revision: `TAG 6.1.4`
- expected full commit SHA: Phase 2Aがtag refから解決・保存した値
- recipe ID: `morpheapp-microg-re-6.1.4-default-release`
- variant: `defaultRelease`
- build root: `.`
- Java: Temurin 18
- tasks: `clean`、`:play-services-core:assembleDefaultRelease`
- artifact: `play-services-core/build/outputs/apk/default/release/*.apk`の厳密な1件

Runner本体はJDK 21で起動する。外部buildだけを`REPRODROID_JDK_18_HOME`で指定したJDK 18へ切り替え、実行前に`release` metadataのJava major、`bin/java`のregular file／実行可能性、実pathを検査する。環境変数がない、pathが不正、Java majorが異なる場合はbuildを開始せず失敗させる。

上流workflowはGradle assemble後にGitHub Secretsの鍵でAPKを署名するため、Runnerが生成するrelease APKはunsignedである。comparison artifactの転送はMIME、Content-Length、ETag、受信byte数、raw SHA-256、package／version解析を要求するが、署名証明書を必須にしない専用経路とする。通常のinstall用artifact取得は署名必須のまま維持し、`installArtifact`もsigner metadataがないartifactを拒否する。Job UIはunsigned artifactをcomparison-onlyとして表示し、installer導線を出さない。

`6.1.4`より新しいtagや任意のrelease taskを暗黙に許可しない。profileの追加は、上流workflowと対象identityを確認した後の明示的なallowlist変更とする。

### 2. 対象同一性を内容比較より先にfail closedで確認する

AndroidはRunner Jobの次の値を照合し、1つでも不一致または必要値不明なら`INCOMPARABLE`とする。

- canonical repository URL
- revision typeとtag value
- Phase 2A保存full SHAとRunner解決full SHA
- recipe ID、variant、Java major、build root、task列
- 参照APKとローカルAPKのpackage name、`versionName`、`longVersionCode`
- 両APKの保存path、size、保存済みraw SHA-256

利用者のRCE確認は、Runnerがtagを独立に解決し、そのfull SHAがPhase 2A保存値と一致した後だけ許可する。Runnerのbuild成功は従来どおり`SUCCEEDED`であり、Androidの`MATCH`／`DIFFERENT`／`INCOMPARABLE`をRunner Job状態へ混ぜない。

### 3. APKを展開せず、選択entryの非圧縮bytesを比較する

Androidは`ZipFile`から次のentryだけをstreamingで読み、非圧縮bytesのsizeとSHA-256をentry name単位で比較する。filesystemへのZIP展開は行わない。

- rootの`classes.dex`、`classes2.dex`、`classes3.dex`以降
- `lib/<abi>/*.so`

`META-INF/`だけを除外リストとして扱うのではなく、上記の実行コードentryだけをallowlistで選ぶ。これによりv1署名entry、v2／v3署名block、ZIP entry順、圧縮方式、timestamp、resourcesはPhase 2B初期比較へ入らない。

同名entryはentry単位で比較し、結果を`MATCH`、`ADDED`、`MISSING`、`HASH_MISMATCH`として永続化する。root `classes.dex`が両方に存在し、対象同一性とZIP安全性を確認できた後に、追加・欠落・hash不一致が1件でもあれば全体を`DIFFERENT`とする。全entryが一致した場合だけ`MATCH`とする。

root `classes.dex`が片方にない場合は、比較成立の最低条件を欠くため`INCOMPARABLE`とする。これは、比較可能なentry集合に対する追加・欠落を示す`DIFFERENT`とは区別する。

### 4. ZIP入力へ上限と構造検査を適用する

比較前に両APKがアプリ専用の許可root配下にあるregular fileであり、symbolic linkではなく、保存済みsize／SHA-256から変化していないことを再検査する。

ZIP全entryのpathを走査し、absolute path、`.`／`..` segment、backslash、空segment、制御文字、過長name、重複entryを拒否する。対応methodは`STORED`と`DEFLATED`だけとし、宣言sizeと実読込sizeの一致も要求する。初期限界は次のとおりとする。

- archive entry数: 100,000
- 比較対象entry数: 4,096
- 比較対象1 entryの非圧縮size: 512 MiB
- 比較対象entryの非圧縮合計: 1 GiB
- archive全体の宣言非圧縮合計: 4 GiB

上限超過、整数overflow、malformed ZIP、未知size、unsupported method、読込失敗は理由code付き`INCOMPARABLE`とする。

### 5. comparison runとentry結果をRoomへ永続化する

Room schema v6に`comparison_runs`と`comparison_entries`を追加する。runは参照release／asset、Runner Job、期待値と実測のcommit／recipe／variant、進行状態、結果、比較不能理由を保持する。entryはname、両size、両SHA-256、entry結果を保持する。

Phase 2Bの最小UIは、検証モードのアプリ詳細に次を表示する。

- `Build and compare`開始
- Runner解決commitとhost RCE riskの明示確認
- comparison status、outcome、期待SHA、Runner SHA、比較不能理由
- 手動refreshと再実行

Phase 2Cの最終trust-level統合、更新判定、install policy変更は本ADRの範囲外とする。

## Alternatives considered

### APK全体のraw SHA-256を比較する

公式署名とローカル署名、v2／v3署名block、ZIP metadataが必ず混ざり、実行コード一致を判定できないため採用しない。

### APKを一時directoryへ展開してから比較する

filesystem path、symlink、cleanup、容量枯渇の境界を増やす。Phase 2Bの対象はstreaming hashだけで足りるため採用しない。

### Runnerで比較してJobを`REPRODUCIBLE`へする

参照APK取得と比較状態のownerがAndroidであるPhase 2境界を崩し、build成功と比較結果を混同するため採用しない。

### 最新tagを同じrecipeで自動許可する

上流workflow、Java、task、variant、versioningは将来変更され得る。未確認releaseを信頼済みhost実行へ自動昇格させるため採用しない。

## Consequences

- 公式署名を再現できなくても、DEXとnative libraryの非圧縮bytesを比較できる
- `MATCH`はPhase 2Bで定義した限定範囲の一致であり、APK全内容、sourceの安全性、signerの信頼性を証明しない
- resource、manifest、assetの差異はPhase 2B初期比較では検出しない
- Java 18を追加導入するが、Runnerサービス自体のJDK 21要件は維持する
- 新releaseごとにallowlist profileとidentity条件の明示更新が必要になる
- 比較不能理由とentry差異を再起動後も監査できる

## Verification requirements

- Runnerが`TAG 6.1.4`を期待full SHAへ独立解決し、固定profileをAPIへ返す
- JDK 18未設定、unsafe path、major不一致をbuild開始前に拒否する
- local release APKをJDK 18と固定taskで生成し、Manifestへbuild Javaとrecipeを記録する
- package、version、commit、recipe、variant、Java、taskの各不一致を`INCOMPARABLE`へする
- signer／ZIP metadataだけが異なり選択entryが同一のfixtureを`MATCH`へする
- DEX／native libraryの変更、追加、欠落をentry単位で保存し`DIFFERENT`へする
- root `classes.dex`欠落、path traversal、重複entry、malformed ZIP、展開量上限超過、保存後改ざんを`INCOMPARABLE`へする
- Room `5 -> 6` migrationで既存の登録、release asset、選択設定、保存済み参照APKを維持する
- Android 16 Emulatorでbuild開始、SHA確認、RCE確認、artifact取得、比較結果、再起動後復元を確認する
