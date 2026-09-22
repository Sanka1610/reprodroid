# Releasing

## Release input

- immutable ソースコミットとtag
- Android versionName/versionCode
- compatible Runner tag/commit/version
- pinned JDK、Gradle、Android SDK/Build Tools
- public signing 証明書 identity

## Unsigned artifact

CIまたは隔離ビルド環境は次を生成します。

- unsigned リリース APK
- CycloneDX JSON SBOM
- unsigned provenance
- source/toolchain/artifact ハッシュ

期限切れまたは内容変更済みのな`UP-TO-DATE`出力をリリース evidenceへ使用せず、最終ソースから必要taskを再実行します。CIとRunnerへ製品版 keyを渡しません。

## Manual signing

リポジトリ、CI、Runnerから分離した署名環境で[sign-release-apk.sh](../scripts/sign-release-apk.sh)を使用します。引数は`usage`、環境変数はスクリプト冒頭、生成する署名記録のフィールドは同スクリプトの`format=reprodroid-apk-signing-provenance-v1`以降で定義しています。

1. ソースコミットとunsigned APK/SBOM ハッシュをprovenanceと照合します。
2. passphraseをファイル経由で渡します。
3. expected 署名者 SHA-256を設定し、別証明書を拒否します。
4. 署名前後ハッシュとsigned provenanceを保存します。
5. `apksigner verify --verbose --print-certs`を独立して実行します。
6. package、バージョン、権限、ネットワーク policy、upgrade、self-update gateを確認します。

keystore、passphrase、秘密鍵、recovery 秘密情報、private path/private-file hashをpublic 成果物へ含めません。

## Release manifest

`release-manifest.json`には少なくとも次を対応付けます。

- スキーマバージョン、product、リポジトリ、リリース、tag、ソースコミット
- package、versionName/versionCode、SDK、Room スキーマ
- ビルド JDK、Gradle、Build Tools、unsigned APK ハッシュ
- signature scheme、署名者 count、key type/size、証明書 SHA-256
- APK/SBOM asset名、バイト数、SHA-256
- compatible Runner release/tag/commit/SQLite/API
- verification scopeと現在の制限

Runner manifestはRunner ZIP/SBOM、SQLite、API、compatible Androidを記録します。

## Checksumとasset

同じGitHub Releaseへ次を置きます。

```text
reprodroid-<version>.apk
reprodroid-<version>.cyclonedx.json
SHA256SUMS
release-manifest.json
release notes
```

チェックサム manifestは公開assetのexact filenameとハッシュを使用します。公開用に確定したファイルのサイズとSHA-256をmanifestへ記録し、公開後に取得し直した成果物と照合します。

## Product verification

- 製品版 APKを対象Android バージョンへinstall/update
- expected 署名者、package、バージョン、権限、cleartext/backup policy
- installed `base.apk`と公開APKのバイト identity
- paired Runner、registration、official APK取得、Build A/B、comparison
- Android標準installerの確認、history、cleanup/exportの主要journey

各結果には対象コミット、成果物ハッシュ、端末・実行条件を記録します。実装済みの項目、実測した項目、公開した成果物をそれぞれ記録してください。

## 生成元と配布物の同期

| 出力 | 生成元・確認先 |
|---|---|
| APK、SBOM、CIの署名前provenance | [ci.yml](../.github/workflows/ci.yml)のビルド・記録・アップロードstep |
| 署名済みAPKと署名記録 | [sign-release-apk.sh](../scripts/sign-release-apk.sh) |
| アプリ内ライセンス | [licenses](../app/src/main/assets/licenses/)の原文・帰属ファイル |
| Runner ZIPの同梱物 | [Runner build.gradle.kts](https://github.com/Sanka1610/reprodroid-runner/blob/main/build.gradle.kts)の`distributions`と`verifyReleaseBundle` |

このチェックアウトにはRelease notesと`release-manifest.json`を作るテンプレート／自動生成処理はありません。対象版の実測値から作成し、公開する全ファイルを確定してから`SHA256SUMS`を計算します。公開済みの成果物とチェックサムは当時の版として保持します。
