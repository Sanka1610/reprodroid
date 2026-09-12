# ADR-0008: URL登録アプリの状態照会にQUERY_ALL_PACKAGESを使用する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-21
- Supersedes in part: [ADR-0007](0007-phase-1d-artifact-transfer-and-installation.md)のpackage visibility判断

## Context

ReproDroidのアプリ登録は、端末内アプリ一覧からの選択ではなく、GitHub repository等のURL入力を起点とする。登録時点では対象のpackage nameをビルド時に予測できず、Runnerが生成したAPKまたは配布元から取得したAPKをAndroid側で解析して初めて確定する。

ReproDroidは、実行時に判明したpackage nameについて、端末内のインストール状態、`longVersionCode`、`versionName`、署名証明書と署名履歴を取得する。これらは、更新済みか、更新候補か、current signerが一致するかをインストール前に判定するために必要である。最終的な署名lineageの受理判断はAndroidの`PackageInstaller`に委ねる。

Android 11以降のpackage visibilityでは、Manifestから可視でないpackageに対する`PackageManager`照会がfilterされる。`<queries><package>`はビルド時に既知のpackage nameに限られ、URLから任意のアプリを登録する構造には追随できない。Intentまたはproviderによる`<queries>`も、対象アプリが一致する公開componentを持つことを保証できないため、任意の登録対象に対する完全な代替にはならない。

現時点ではGoogle Play版を提供する計画はない。Google Playの審査回避を採用理由にはせず、非Play配布でもinstalled-app情報の機密性を考慮し、利用目的とデータ境界を明示する。

## Decision

AndroidアプリのManifestで`android.permission.QUERY_ALL_PACKAGES`を宣言する。Phase 1Dで使用した固定packageの`<queries>`宣言は削除する。

Android Lintの`QueryAllPackagesPermission`は、限定的な`<queries>`を一般的な推奨として要求する。このADRで代替案を検討した結果、URL登録という要件には適用できないと判断したため、Manifestの当該permissionに限って`tools:ignore="QueryAllPackagesPermission"`を付ける。project全体のLintまたは他の検査は無効化しない。

この権限の目的は、インストール済みアプリの全件一覧を登録UIとして提供することではない。URLから登録され、APK解析によってpackage nameが判明したアプリに対して、次の情報を確実に取得することである。

- インストール済みかどうか
- `longVersionCode`と`versionName`
- 現在の署名証明書
- Androidが提供する署名履歴
- 候補APKと既存アプリのcurrent signer一致／不一致

現在の実装は`getInstalledPackages()`または`getInstalledApplications()`による全件列挙を行わず、ダウンロードしたAPKから得たpackage nameを指定して`getPackageInfo()`を呼ぶ。将来、全件列挙を伴う別機能を追加する場合は、その必要性、保存範囲、UI説明を改めて設計する。

取得したinstalled-package情報には次のデータ境界を適用する。

- package情報、version、署名fingerprintの照合は端末内で完結させる
- installed-package一覧をRunnerまたは配布元へ送信しない
- package情報、version、署名fingerprintをanalytics、広告、telemetry、crash reportingへ送信しない
- URL path、query parameter、request header、request body、logを含め、目的外のネットワーク送信経路へ載せない
- 外部通信は、利用者が入力したrepository URL、更新情報、APK、Jobまたはartifact操作など、明示した機能に必要な情報へ限定する
- 現在のAndroidアプリにはanalytics、広告、crash reporting SDKを導入しない

Google Play向けの別Flavorは現時点で作成しない。将来Google Play配布を検討する場合は、当時のpackage visibility policyと製品要件を再評価し、必要ならManifestと機能をFlavor単位で分離する。

## Alternatives considered

### 固定packageを`<queries>`へ追加する

URLから登録可能なpackage nameは実行時に増減する。アプリ更新のたびにManifestへpackageを追加する方式では製品要件を満たせないため採用しない。

### Intentまたはprovider queryで可視範囲を広げる

launcher Activityを持たないアプリや、宣言したIntent/providerに一致しないアプリを扱えない。URL登録対象を任意のAndroid packageとする要件を満たさないため採用しない。

### インストール前の照会をせずPackageInstallerの結果だけに依存する

最終的な署名検証はAndroidに委ねられるが、更新済み、downgrade、署名不一致を事前に区別できず、ReproDroidが提供するローカル検証情報が減るため採用しない。

### target SDKを下げてpackage visibility filteringを避ける

platform security modelへの追随を失い、将来の互換性も悪化するため採用しない。

## Consequences

- URLから登録した任意packageについて、既存インストール状態、version、current signerを一貫して照会できる
- 固定packageをManifestへ追加し続ける保守が不要になる
- `QUERY_ALL_PACKAGES`はruntime permission dialogを持たないため、READMEと将来のアプリ内説明で用途を明示する必要がある
- 権限は現在のAndroid profileからアクセス可能なpackageの可視性を広げるものであり、別profileの情報や他アプリのprivate dataへのアクセスを与えるものではない
- 将来Google Playへ配布する場合は、権限申告、審査適合性、限定visibility版の要否を再検討する必要がある
- 権限を持っていても、必要のない全件列挙、永続保存、外部送信は行わない

## Verification

- Debug/Releaseのmerged Manifestに`android.permission.QUERY_ALL_PACKAGES`が含まれることをbuild時に確認する
- 固定対象`app.revanced.android.gms`の`<queries>`がmerged Manifestに残っていないことを確認する
- URLから判明した複数のpackage nameに対し、`getPackageInfo()`がインストール状態、version、署名情報を返すことをPhase 1Eの端末E2Eで確認する
- network requestおよびlogへinstalled-package情報を追加しないことを変更レビューで継続確認する
