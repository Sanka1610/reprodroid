# ReproDroid

ReproDroidは、公開Androidアプリの公式APKと、公開sourceからPC側Runnerが生成した2つのAPKを比較し、差異、build条件、署名、来歴を利用者が確認できるようにするAndroidアプリです。

## 主な機能

- public GitHub／Codeberg repositoryの登録
- release metadataの手動／定期確認
- 複数APK assetからの明示選択、download、package/version/signer検査
- 同じsource revisionから独立したBuild A／Bを生成
- Official vs A、Official vs B、A vs Bのraw三軸比較
- DEX、native library、Manifest、resource、dependency、build環境の補助証拠
- Runnerとのmanual pairing、root pin付きHTTPS、端末別credentialと失効
- history、storage summary、preview-first cleanup、audit/log export
- Android標準`PackageInstaller`によるinstall確認

## 対応環境

| 項目 | 現在の値 |
|---|---|
| Android app | `0.1.0-alpha05` / `versionCode 5` |
| Android OS | Android 8.0（API 26）以上 |
| target / compile SDK | 36 / 36 |
| Android database | Room24 |
| Runner | `0.1.0-alpha02` |
| Runner database | SQLite12 |
| Runner API | v1、v2、pairing v1 |
| Provider | public GitHub、public Codeberg |
| Package format | 単一APK |

詳細は[Compatibility](docs/compatibility.md)を参照してください。

## Androidアプリのインストール

1. [v0.1.0-alpha05 release](https://github.com/Sanka1610/reprodroid/releases/tag/v0.1.0-alpha05)から次を取得します。
   - `reprodroid-0.1.0-alpha05.apk`
   - `reprodroid-0.1.0-alpha05.cyclonedx.json`
   - `SHA256SUMS`
   - `release-manifest.json`
2. checksumを確認します。

   ```bash
   sha256sum -c SHA256SUMS
   ```

3. 必要に応じてAndroid SDKの`apksigner`で署名証明書を確認します。

   ```bash
   apksigner verify --verbose --print-certs reprodroid-0.1.0-alpha05.apk
   ```

4. signer SHA-256が[公開署名identity](docs/security.md#公開releaseの確認)と一致することを確認し、Android標準installerでAPKを開きます。

filenameだけを根拠にinstallしません。APK、checksum、release manifestを同じGitHub Releaseから取得します。

## クイックスタート

1. 対応する[ReproDroid Runner v0.1.0-alpha02](https://github.com/Sanka1610/reprodroid-runner/releases/tag/v0.1.0-alpha02)を導入します。
2. Runnerでpaired HTTPSを初期化し、`pairing-open`を実行します。
3. ReproDroidのSettings → Runner → Runner settings and authenticationでmanual pairingします。
4. Add appでpublic GitHubまたはCodeberg repository URLを登録します。
5. releaseを確認し、必要な場合は公式APK assetを明示選択します。
6. build configurationとtoolchainを確認し、Build AとBuild Bを個別に承認します。
7. Comparison evidenceでraw三軸と補助証拠を確認します。

詳しい導入は[Getting started](docs/getting-started.md)、画面操作は[User guide](docs/user-guide.md)を参照してください。

## 比較結果の読み方

| 表示 | 条件 |
|---|---|
| `Reproducible` | 同じrelease observationのOfficial vs A、Official vs B、A vs Bがすべて`MATCH`し、必要なidentity/trust/install条件も成立 |
| `Different` | 定義済みraw軸の少なくとも1つが`DIFFERENT` |
| `Incomparable` | artifact、identity、configuration、evidenceの不足または不整合により比較条件が成立しない |

build成功、scan findingなし、Build AとBの一致、意味比較の一致のいずれか1つだけでは`Reproducible`になりません。詳しくは[Reproducibility](docs/reproducibility.md)を参照してください。

## セキュリティと既知の制限

- generic buildはRunnerのDocker内で任意コードを実行します。現在のprofileには固定egress allowlistとJob単位のhard disk／inode quotaがありません。
- release接続はmanual pairingとroot pin付きHTTPSを使用します。
- APK install/updateではAndroid標準`PackageInstaller`が開き、利用者の確認とplatformのsigner-lineage判定が必要です。
- scheduled release checkはmetadataを取得して通知します。buildはJobs画面、installはAndroid標準installerから開始します。
- private repository、GitLab、任意Forgejo/Gitea、split APK/APKS/AAB、silent install、backup/restoreには対応しません。
- analytics、広告、tracking、自動crash uploadはありません。

詳細は[Security](docs/security.md)を参照してください。

## ドキュメント

- [Documentation index](docs/README.md)
- [Getting started](docs/getting-started.md)
- [User guide](docs/user-guide.md)
- [Reproducibility](docs/reproducibility.md)
- [Security](docs/security.md)
- [Compatibility](docs/compatibility.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Development](docs/development.md)
- [Releasing](docs/releasing.md)
- [Architecture overview](docs/architecture/overview.md)
- [UI architecture](docs/architecture/ui.md)
- [Changelog](CHANGELOG.md)

Runnerの設定、CLI、API、Docker、state管理は[Runner documentation](https://github.com/Sanka1610/reprodroid-runner/tree/main/docs)を参照してください。

## ソースからビルドする

debug APKのbuildとtestは[Development](docs/development.md)を参照してください。debug APKはapplication ID、署名、dataがproduction APKと異なります。

## ライセンス

ReproDroidは[Apache License 2.0](LICENSE)です。Androidアプリ内でthird-party noticesを表示できます。
