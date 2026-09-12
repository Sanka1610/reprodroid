# ADR-0009: Phase 2の参照APK取得と更新メタデータの境界

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-24
- Scope: Phase 2Aの仕様

## Context

Phase 1は、allowlist登録済みリポジトリをRunnerでビルドし、生成APKをAndroidへ転送して検査・インストールする縦切りを完成させた。RunnerとAndroidの転送SHA-256が一致しても、これは`Buildable`を意味するだけであり、開発者が公開した配布APKとの再現性一致は確認していない。

Phase 2では、Phase 1で保留した公式APKまたは開発者公開APKとの比較を行う。同時に、ReproDroidをObtainiumに近い用途でも利用できるようにし、配布元から取得したAPKを更新候補として扱う。

この2つの用途は同じAPK取得・検査経路を利用できる一方、目的が異なる。

- 検証モード: 配布APKを参照APKとして、ローカルビルドAPKとの再現性を検証する
- 取得モード: 配布APKを更新候補として、端末内アプリの更新可否を判定する

両者を「APKを取得できた」「versionが新しい」「内容が一致した」という単一の成功状態へまとめると、更新機能の結果を再現性の証拠と誤認しやすい。

## Decision

### 1. 公式APK／配布APKはAndroidアプリ側で取得する

Phase 2Aでは、利用者が登録したURLに対応する配布元から、Androidアプリが公式APKまたは配布APKと更新情報を取得する。比較処理もAndroid側で行うという既存設計を維持する。

Runnerは引き続きソース取得、allowlist済み実ビルド、Build Environment Manifest、ビルドartifact配信を担当する。Phase 2Aのために、Runnerが公式APKを取得するAPIや、Runnerのbuild workspaceを配布元として使う機能は追加しない。

Phase 2Aの初期providerはpublic GitHub Releasesだけとする。入力は`https://github.com/{owner}/{repository}[.git]`形式に限定し、private repository、GitHub以外のprovider、HTML scraping、任意の直接APK URLは扱わない。draftとprereleaseを除くlatest published releaseを手動で確認し、APIのETagを用いた条件付きrequestは行うが、定期pollingとGitHub tokenはPhase 2Aに含めない。

release responseの`target_commitish`はbranch名の場合があるため、比較対象commitとして信頼しない。`tag_name`から`refs/tags/{tag}`を解決し、annotated tagの場合は最大深度を制限してcommit objectまでpeelしたfull SHAを保存する。動的な`latest`参照だけを証跡として保存せず、release ID、tag、解決済みcommit SHA、asset ID、stableな`browser_download_url`、size、provider digest、Androidが計算したraw SHA-256を関連付ける。

uploaded APK assetが1件ならそのassetを採用する。複数の場合は、アプリ別に保存したrelease variantとABIでfile nameを区切り文字単位に照合し、候補が厳密に1件の場合だけ採用する。初期値は`Release`／`arm64-v8a`とし、variantは`Release`、`Preview`、`Debug`、ABIは`arm64-v8a`、`armeabi-v7a`、`x86_64`、`universal`を選択できる。`Preview`／`Debug`は対応する明示tokenを要求し、`Release`は`preview`／`debug` tokenを含まないassetとして扱う。該当0件または複数件なら推測で選ばず、理由を保存してfail closedとする。任意assetの直接選択は後続Phaseで追加する。

### 2. 参照APKと更新候補は共通の検査境界を使う

Android側で取得したAPKは、Phase 1で確立したアプリ専用領域、部分ファイル、MIME、Content-Length、受信byte数、SHA-256、APK解析の境界を再利用する。検査に失敗したAPKは参照APKまたは更新候補として確定しない。

GitHub asset取得はHTTPSだけを許可し、redirectは最大5回、HTTPSからHTTPへのdowngrade、資格情報付きURL、非許可hostへの遷移を拒否する。stableなGitHub asset URLは保存するが、期限付きqueryを含む最終CDN URLは保存しない。初期の単一APK上限は512 MiBとし、metadata、Content-Length、実受信byte数とstreaming中の上限をすべて検査する。

Runner artifactのETagはSHA-256であるが、GitHub release assetのETagはcontent hashとは限らない。外部assetではresponse ETagをcache情報、GitHub APIのdigestをprovider申告値、Androidが計算したraw SHA-256を受信bytesの確定値として分離する。provider digestがSHA-256として提示された場合はcomputed raw SHA-256との一致を要求する。

取得したAPKには、可能な範囲で次の情報を保存する。

- 登録元URL、配布元、releaseまたはtag、asset URL
- 配布元が示すsource commitまたはrelease commit
- APKのsize、raw SHA-256
- package name
- `versionName`
- Androidの`longVersionCode`
- signing certificate fingerprint

### 3. version情報の用途を分ける

- `package name`は、比較対象の同一性確認と更新対象の特定に必須とする
- `longVersionCode`は、端末内installed packageとの新旧判定に使う
- `versionName`は、利用者向け表示と補助的な確認に使う
- signing certificateは、既存packageとの更新可否および標準`PackageInstaller`の結果に関係するため、比較結果とは別に扱う

versionが一致または新しいこと、signerが一致することだけでは、公式APKとの再現性一致を意味しない。取得成功、更新可能性、比較結果、インストール結果は別状態として保存する。

### 4. 比較対象の同一性を内容比較より先に確認する

検証モードでは、少なくとも次の対応関係を確認してから内容比較を行う。

- ローカルビルドのsource commitと、参照APKのreleaseまたはsource commit
- package name
- versionNameと`longVersionCode`
- flavorおよびbuild type
- 参照APKの実体と取得時のSHA-256

MicroG-REの現在のローカルレシピは`:play-services-core:assembleDefaultDebug`であり、上流のrelease workflowは`:play-services-core:assembleDefaultRelease`である。release APKは上流の署名鍵を使用して後処理されるため、Phase 2Aではdebug／releaseの対応と署名差異を明示的に確認する。対応を確定できない組み合わせを`Reproducible`と表示しない。

### 5. 比較不能と`Different`を分離する

次の事象は`Different`ではなく`INCOMPARABLE`（UI表示は「比較不能」）として扱う。

- 参照APKを取得できない
- package、version、`longVersionCode`、flavorまたはbuild typeが一致しない
- 参照APKとsource commitの対応を確認できない
- 比較対象の一方に必要なファイルがない

`INCOMPARABLE`には取得不能、asset選択不能、source commit不明／不一致、package／version／flavor／build type不一致などの理由codeを保存する。`Different`は対象同一性を確認できた組についてPhase 2Bの定義済み比較範囲に内容差異がある場合だけ使用する。内容比較の対象同一性が確認でき、定義済みの比較範囲で内容が一致した場合だけ`Reproducible`候補とする。

Phase 2Aの内部状態はrelease discovery、APK download、comparison eligibilityを分離する。comparison eligibilityは`NOT_EVALUATED`、`READY_FOR_COMPARISON`、`INCOMPARABLE`とし、`MATCH`と`DIFFERENT`はPhase 2Bで追加する。

Phase 2Bでこの「必要なファイル」をrootの`classes.dex`と定義した。両APKにroot `classes.dex`が存在して比較が成立した後、追加の`classesN.dex`または`lib/<abi>/*.so`が片方だけにある場合は、比較可能な実行コード集合の追加／欠落として`Different`にする。詳細は[ADR-0010](0010-phase-2b-executable-apk-content-comparison.md)で本節を具体化する。

### 6. Phase 2AのAndroid UI

Phase 2Aはdata/network層だけで終わらせず、実際のAndroid取得経路を検証できる最小UIを含める。Material 3の下部navigationは、左から登録アプリ一覧、アプリ登録、設定の3項目とする。

- 登録アプリ一覧: 検索、APKから保存したapplication icon、release／version、取得状態、比較可否、詳細画面とアプリ別設定への導線
- アプリ登録: GitHub URL、管理モード、release／tag／commit／選択assetのpreview、登録と取得
- アプリ別設定: release variantとABI。保存後のRefreshでasset選択へ反映する
- 全体設定: Phase 1のRunner Job UIへの導線とPhase 2A provider方針。theme、download上限、rate-limit／診断情報の操作UIは後続Phaseで追加する
- アプリ詳細: release、full commit SHA、asset、hash、package／version／signer、比較不能理由。既存Runner Job UIは設定画面から引き続き利用できる

取得モードと検証モードは同じdownload／inspection境界を利用するが、更新判定と再現性判定は統合しない。Phase 2Aでは取得APKの新しいinstall導線、定期更新、通知を追加しない。

## Alternatives considered

### Runnerで公式APKを取得してAndroidへ配信する

Runner側で配布元アクセスとキャッシュを集中管理できるが、Phase 2AではAndroid側取得という製品要件から外れ、取得モードと検証モードのネットワーク境界がRunnerへ集約される。provider、redirect、更新情報の扱いが固まる前にAPIを増やすため、初期案として採用しない。必要性が明確になった場合は、別ADRとAPI変更で扱う。

### versionNameだけで更新を判定する

versionNameは表示形式や命名規則が配布元ごとに異なり、大小比較の根拠にならないため採用しない。Androidの`longVersionCode`を新旧判定の基準とし、versionNameは補助情報とする。

### packageとversionが一致すれば再現可能とする

同一package・versionでも、source commit、build variant、DEX、native library、resourcesが異なる可能性がある。再現性判定をversion情報だけで成立させることは採用しない。

### 比較対象不一致を即座に`Different`と表示する

対象が異なる状態と、同じ対象の内容が異なる状態を混同するため、表示仕様を決めるまでは採用しない。少なくとも内部理由は分離して保存する。

## Consequences

- Phase 2Aの中心はAndroid側の配布APK・更新情報取得とメタデータ保存になる
- 検証モードと取得モードでAPK検査・保存境界を共有できる
- Android側の取得だけでは再現性を主張せず、source commit、variant、内容比較を別途要求できる
- Runner APIはPhase 2Aの初期実装では変更しない想定であり、互換性変更が必要になった場合は別途契約を更新する
- 対象同一性を確認できない状態は`INCOMPARABLE`として保存し、`Different`を同一対象の内容差異に限定できる
- MicroG-REのdebug／release対応、公式release assetとsource commitの対応、providerの取得規則が実装前の確認事項として残る
- Room schema v4ではRunner Jobに従属しない登録アプリ、release snapshot、release assetを別tableとして保存する
- Room schema v5ではアプリ別variant／ABI設定と、同一release内で現在選択中のprovider asset IDを保存する
- 取得済みAPKからapplication iconをPNGとしてアプリ専用領域へ保存し、一覧表示に使用する。抽出・読込不能時だけ頭文字へfallbackする

## Phase 2B resolution

2026-08-24、MicroG-RE `6.1.4`の固定release recipeとTemurin 18をRunnerへ追加し、Android側に対象同一性gate、DEX／native library comparator、Room v6、最小Build-and-compare UIを実装した。Phase 2Aで残したdebug／release、source commit、署名差異の不確実性は、同じtag／full SHA／`defaultRelease`を再ビルドし、署名を比較範囲へ含めないことで解消した。Phase 2Bの詳細な境界と上限は[ADR-0010](0010-phase-2b-executable-apk-content-comparison.md)を正本とする。

## Phase 2C resolution

現在対象に限定したtrust表示、`longVersionCode`によるupdate relation、署名済み公式APKを既定とするinstall policy、local build明示選択、全般設定の追従／非追従defaultは[ADR-0011](0011-phase-2c-trust-update-and-install-policy.md)で具体化する。更新関係、signer relation、comparison、install attemptは引き続き別状態として保持する。

## Verification requirements

Phase 2のコード実装時には、少なくとも次を検証する。

- 取得したAPKのHTTP応答、size、受信byte数、SHA-256、package、version、signerを記録できる
- 動的な`latest`参照だけでなく、実際のasset URLとhashを再起動後も復元できる
- tagをfull commit SHAへ解決し、`target_commitish`がbranch名でも比較commitとして誤用しない
- APK assetが1件なら採用し、複数時は保存したvariant／ABI設定で厳密に1件へ絞り、別variantを暗黙選択しない
- variant／ABI設定、現在選択中asset、APKから保存したiconを再起動後も復元できる
- redirect回数、scheme、host、Content-Length、streaming byte上限、provider digest不一致をfail closedで扱う
- package、version、`longVersionCode`、flavor、build typeが一致しない対象を`Reproducible`へ昇格させない
- 同一payloadでsignerだけが異なるfixtureを、署名差異と内容一致として区別できる
- DEXまたはnative libraryを変更したfixtureを、内容差異として検出できる
- malformed APK、split APK、途中取得をPhase 2Aでfail closedにする。ZIP path traversal、重複entry、異常な展開量はAPKをZIPとして読むPhase 2Bで検証する
- 取得モードの更新候補判定と、検証モードの再現性判定を別の永続状態として復元できる
