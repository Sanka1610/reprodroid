# ReproDroid

ReproDroidは、公開されているAndroidアプリの公式APKと、対応する公開ソースからPC側Runnerが生成したAPKを比較し、利用者が差異と来歴を確認できるようにするAndroidアプリです。

> [!IMPORTANT]
> 現在のsourceは`0.1.0-alpha03`／`versionCode 3`のapplication identityを維持したまま、Phase 5.6までローカル統合されています。Phase 5の最終release候補はまだ生成・署名・受入されていません。公開、push、`main`統合も別の承認対象です。ビルド成功、静的scan、意味比較の一致だけを、公式APKとの再現性や安全性の証明として扱いません。

## Repository roles

- `reprodroid`（本リポジトリ）: Androidアプリ、公開ドキュメント、公開release metadata
- [`reprodroid-runner`](https://github.com/Sanka1610/reprodroid-runner): source取得、scan、隔離build、artifact提供を行うPC側Runner
- `reprodroid-project`: 非公開の開発計画、生の検証証跡、handoff、ローカル互換性管理

公開仕様の正本は本リポジトリの[`docs/`](docs/README.md)です。公開文書は、非公開リポジトリがなくても読めることを要件とします。

## Capabilities

- public GitHub／Codeberg repositoryの登録と、上限付きsource・release metadata取得
- 複数APK候補からの明示選択と、size、SHA-256、package、version、signerの検査
- 同じsource revisionから独立したBuild A／Bを作成し、公式APK対A、公式APK対B、A対Bを別々に比較
- detached checkoutを解決済みcommitへ固定した`docker-generic-v3`でのgeneric build
- DEX、native library、Manifest、resource等の差異を、raw結果と補助的な意味比較に分けて表示
- metadata-onlyの定期release確認と通知
- 履歴、保存容量、手動cleanup、監査export
- Runnerとのmanual pairing、root pin付きHTTPS、端末別credentialと失効
- Android自身のbounded log export

ReproDroidは、silent install、自動アンインストール、root／Shizuku、署名検証回避、private repository token、analytics、広告、tracking、自動crash送信を提供しません。

## Security model

- Runnerによるsource buildは任意コード実行を伴います。実行前の明示確認と、対応するRunner側の安全境界が必要です。
- scheduled release checkはmetadataと通知だけを扱い、APK取得、toolchain導入、build、comparison、trust変更、installを開始しません。
- `Reproducible`は、同じrelease observationに結び付いた公式APK、Build A、Build Bの定義済みraw三軸がすべて一致した場合だけ表示します。
- APKのinstall／updateはAndroid標準`PackageInstaller`と利用者確認を使用します。
- package情報とsigner情報は端末内判定に使い、analyticsや広告へ送信しません。

詳細は[Architecture overview](docs/architecture/overview.md)を参照してください。

## Requirements

| 項目 | 値 |
|---|---|
| Android | minSdk 26、targetSdk 36、compileSdk 36 |
| JDK | 21 |
| Build | Gradle Wrapper、Kotlin DSL |
| Runner | 対応する`reprodroid-runner`。release経路はpaired HTTPSを使用 |
| Current Android schema | Room24 |
| Current Runner schema | SQLite12 |

## Build

Android SDKの場所は、Git管理外の`local.properties`または`ANDROID_SDK_ROOT`で指定します。

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

release候補の生成・署名・公開は通常のdeveloper buildと分離します。署名鍵をrepository、CI、Runner、build containerへ渡してはいけません。公開署名ポリシーは[`release/README.md`](release/README.md)を参照してください。

## Documentation

- [Documentation index](docs/README.md)
- [Current status](docs/status/current.md)
- [Architecture overview](docs/architecture/overview.md)
- [UI architecture and navigation](docs/architecture/ui.md)
- [Getting started](docs/guides/getting-started.md)
- [Operations and recovery](docs/guides/operations.md)
- [Unreleased notes](docs/releases/unreleased.md)
- [ADR index](docs/adr/README.md)
- [Runner API](docs/api/README.md)
- [Feature contracts](docs/design/README.md)
- [Public/private documentation boundary](docs/adr/0027-public-documentation-and-private-development-boundary.md)
- [Release signing policy](release/README.md)

## License

ReproDroid自身のcode、設定、script、文書、resourceは[Apache License 2.0](LICENSE)です。第三者componentとassetには、それぞれのライセンスが適用されます。Androidアプリには利用中componentのライセンス表示を同梱します。
