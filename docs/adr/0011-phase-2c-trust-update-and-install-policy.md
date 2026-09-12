# ADR-0011: Phase 2Cのtrust表示、更新候補、設定継承、install source

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-24
- Scope: Phase 2C

## Context

Phase 2Aはpublic GitHub Releasesから公式APKを取得・検査・保存し、Phase 2BはMicroG-RE `6.1.4`を同じtag、full commit SHA、release variantで再ビルドして、DEXとnative libraryを比較した。比較runは`MATCH`、`DIFFERENT`、`INCOMPARABLE`をAndroidのRoomへ保存するが、Phase 2B時点ではアプリ一覧の最終trust表示、端末内versionとの更新関係、公式APKのinstall attemptへ統合していない。

Phase 2Bのローカルrelease artifactは上流workflowの署名処理前なのでunsignedである。比較結果が`MATCH`でも、unsigned artifactをAndroid標準`PackageInstaller`へ渡すことはできない。また、公式署名アプリと別署名のlocal buildを同じpackageへ上書きできるとは限らない。

全般設定はPhase 2Bまで固定表示であり、release variantとABIだけがアプリ個別値として保存されていた。Phase 2Cでは、全般設定を実際に変更可能にし、既存アプリが追従するdefaultと、登録時にだけコピーするsecurity-sensitiveなdefaultを区別する。

## Decision

### 1. build、comparison、update、installを別状態として保持する

Runner Job、comparison outcome、update relation、PackageInstaller attemptを単一の成功状態へ統合しない。UIのterminal headlineだけを元状態から導出する。

| 表示 | 条件 |
|---|---|
| `Reproducible` | 現在選択中release／assetに対する完了runが`MATCH` |
| `Buildable` | build成功は確認できるが、現在の対象に有効な比較結果がない |
| `Different` | 対象同一性確認後の比較が`DIFFERENT` |
| `Incomparable` | 対象同一性、必要entry、ZIP安全性等を確立できない |
| `Failed` | build自体が失敗 |

過去releaseの`MATCH`を新しいlatest releaseへ引き継がない。表示対象runは少なくとも`releaseSnapshotId`、`referenceAssetId`、expected full commit SHAが現在選択中の対象と一致しなければならない。variant／ABI変更後は現在のtrustを`NOT_EVALUATED`へ戻し、Refreshと新しいcomparisonを要求する。

`Reproducible`はPhase 2Bで定義したDEX／native libraryの一致に限定する。resources、manifest、assets、signerの信頼性、source自体の安全性を証明しないことをUIへ併記する。

### 2. 更新関係とsigner関係を分離する

verified公式APKの`longVersionCode`と端末内packageを比較し、update relationを次の状態で保存する。

- `NOT_INSTALLED`
- `UPDATE_AVAILABLE`
- `UP_TO_DATE`
- `OLDER_THAN_INSTALLED`
- `UNKNOWN`

candidate signerとinstalled signerの関係は既存の`SIGNER_MATCH`、`SIGNER_MISMATCH`、`NOT_INSTALLED_OR_NOT_VISIBLE`として別に保持する。新しいversionやsigner一致だけで`Reproducible`にしない。current signer不一致は警告するが、署名lineageの最終受理はAndroidの`PackageInstaller`に委ね、ReproDroidは署名検証を回避しない。

GitHubが`304 Not Modified`を返した場合も、端末内packageのversionとsignerは再照会する。詳細画面表示、install直前、terminal callback後、起動時の孤児session回収後にも再評価する。

### 3. 公式APKを既定のinstall sourceとする

アプリごとに次の具体値を保存する。

- `OFFICIAL_RELEASE`
- `LOCAL_BUILD`

`OFFICIAL_RELEASE`を既定値とする。検証モードで`MATCH`した場合も、既定ではAndroid側で取得・検査した署名済み公式APKを標準`PackageInstaller`へ渡す。

公式APKのinstall attemptはRunner Jobの`install_attempts`へ混ぜず、登録アプリとrelease assetへ従属する`release_install_attempts`へ保存する。install直前にapp-private許可root、non-symlink regular file、size、raw SHA-256、package、signerを再検査する。

通常のprimary actionは未インストール時のInstallと、candidateが新しい場合のUpdateだけとする。同version再インストールとdowngradeはPhase 2Cの通常操作に含めない。

### 4. local buildは明示選択かつ既署名artifactだけを許可する

`LOCAL_BUILD`は`VERIFICATION`モードだけで選択できる。対象packageが未インストールであることをAndroidが確認できる初回登録時、またはアンインストール後にだけ変更できる。保存直前にもinstalled packageを再照会し、設定画面表示後に外部installされた場合をfail closedで拒否する。

packageがインストールされている間は、公式からlocal、localから公式のどちらの変更も許可しない。自動アンインストール、root／Shizuku、署名検証回避は行わない。`LOCAL_BUILD`を保持したまま`ACQUISITION`へ変更することも拒否する。

Phase 2CはReproDroid鍵を生成・保存・使用しない。local installは、現在のcomparison runに結び付いたartifactが既に有効なsigner metadataを持つ場合だけ許可する。unsigned artifactは理由を表示して拒否する。既存のMicroG-RE `6.1.4` comparison artifactはunsignedなので、Phase 2Cではlocal install不能である。

将来、ReproDroid鍵でcomparison artifactを署名する案は候補として維持する。ただしPhase 2への採用を確約しない。採用時は別ADRで、少なくとも次を決める。

- Android側署名かRunner側署名か
- 任意コードを実行するRunner hostからprivate keyをどう分離するか
- app別鍵か共通鍵か
- Android Keystore、backup、recovery、rotation
- APK Signature Scheme v2／v3
- signer continuityと将来update
- 署名前comparison artifactと署名後artifactの実行entry再検査

Phase 1のdebug APKをPhase 2Bのrelease comparison結果と結び付けてinstallする案は、build typeとartifact identityが異なるため採用しない。

### 5. 全般設定を追従defaultと登録時defaultへ分ける

全般設定をRoomのsingleton rowへ保存する。

既存アプリも追従するdefault:

- release variant
- preferred ABI
- APK download limit

アプリ個別設定は`Use global default`または明示overrideを保持する。global default変更時はdefault使用中のアプリだけ実効値が変わる。variant／ABI変更時はrelease metadata cacheと現在のtrustを無効化する。設定可能なAPK上限は64、128、256、512 MiBとし、512 MiBのsecurity hard limitを超えられない。

登録時に具体値をコピーし、既存アプリが追従しないdefault:

- management mode
- installation source

全般defaultが`LOCAL_BUILD`でも、各登録でsigning／update riskの明示確認を要求する。

Themeはアプリ全体の設定として`System`、`Light`、`Dark`を選択できる。provider、GitHub token、redirect policy、hash検査、signer必須等のsecurity boundaryを設定で無効化できない。

### 6. Phase 2C UI

登録済みアプリ一覧はicon横を二段にする。

- 上段: アプリ名、APK version、update relation
- 下段: `Verification`／`Acquisition`、trustまたはsigner関係、local build明示時だけ`Local build`

カードtapはアプリ詳細へ遷移する。アプリ個別設定は詳細TopAppBarの設定buttonから開く。個別設定と全般設定の複数値はradio一覧ではなくdropdownを使用する。

## Alternatives considered

### `INCOMPARABLE`を`Buildable`へ含める

比較未実施と、比較を試みたが対象同一性または安全性を確立できなかった状態を区別できないため採用しない。

### trust levelをRoomへ重複保存する

元のJob、release、asset、comparisonが更新された際にstaleなtrustが残るため採用しない。監査用の元状態を保存し、現在表示だけを導出する。

### unsigned local artifactをそのままinstallerへ渡す

Androidがinstallable APKとして受理せず、既存のsigner必須境界にも違反するため採用しない。

### Phase 2CでReproDroid鍵を導入する

鍵配置、Runner RCE、backup、rotation、signer continuityという独立したsecurity boundaryが未設計であるため採用しない。将来候補として本ADRへ記録し、採用Phaseは固定しない。

### management modeとinstallation sourceをglobalへ動的追従させる

全般設定変更だけで実行経路や署名方針が既存アプリについて変わるため採用しない。登録時の具体値として保存する。

## Consequences

- Phase 2Bの`MATCH`を限定範囲の`Reproducible`として一覧と詳細へ表示できる
- update、signer、comparison、install結果を混同せず復元できる
- 公式APKをObtainium的なInstall／Updateへ安全に接続できる
- local build選択は保存できるが、unsigned MicroG-RE release artifactはinstallできない
- global default変更の影響範囲が設定画面から判別できる
- Room schema v7、公式APK用install attempt、callback routingが必要になる
- Runner API v1の変更は不要である

## Verification requirements

- Room `6 -> 7` migrationが既存Job、artifact、install attempt、登録アプリ、release、asset、comparisonを維持する
- migration後の既存アプリは`OFFICIAL_RELEASE`、既存variant／ABIは明示値として維持する
- update relationのversion truth tableをunit testする
- stale release／assetの過去`MATCH`を現在の`Reproducible`へ使わない
- `ACQUISITION + LOCAL_BUILD`をUIとrepositoryで拒否する
- installed packageがある場合のinstall source変更を保存直前再照会で拒否する
- official APK install直前にpath、symlink、size、SHA-256、package、signerを再検査する
- local artifactがunsignedならinstaller session作成前に拒否する
- signer mismatch、非`Reproducible`、unknown-sources、PackageInstaller callbackを既存security boundaryのまま扱う
- `304 Not Modified`でもinstalled package情報を再評価する
- global variant／ABI変更がdefault使用アプリだけを再評価待ちへ戻す
- force-stop／再起動後にsettings、override、update relation、trust元状態、install attemptを復元する
