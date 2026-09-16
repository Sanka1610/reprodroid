# Releasing

## Release input

- immutable source commitとtag
- Android versionName/versionCode
- compatible Runner tag/commit/version
- pinned JDK、Gradle、Android SDK/Build Tools
- public signing certificate identity

## Unsigned artifact

CIまたは隔離build環境は次を生成します。

- unsigned release APK
- CycloneDX JSON SBOM
- unsigned provenance
- source/toolchain/artifact digest

staleな`UP-TO-DATE`出力をrelease evidenceへ使用せず、最終sourceから必要taskを再実行します。CIとRunnerへproduction keyを渡しません。

## Manual signing

repository、CI、Runnerから分離した署名環境で`scripts/sign-release-apk.sh`を使用します。

1. source commitとunsigned APK/SBOM digestをprovenanceと照合します。
2. passphraseをfile経由で渡します。
3. expected signer SHA-256を設定し、別certificateを拒否します。
4. 署名前後digestとsigned provenanceを保存します。
5. `apksigner verify --verbose --print-certs`を独立して実行します。
6. package、version、permission、network policy、upgrade、self-update gateを確認します。

keystore、passphrase、private key、recovery secret、private path/private-file hashをpublic artifactへ含めません。

## Release manifest

`release-manifest.json`には少なくとも次を対応付けます。

- schema version、product、repository、release、tag、source commit
- package、versionName/versionCode、SDK、Room schema
- build JDK、Gradle、Build Tools、unsigned APK digest
- signature scheme、signer count、key type/size、certificate SHA-256
- APK/SBOM asset名、byte数、SHA-256
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

checksum manifestは公開assetのexact filenameとdigestを使用します。公開前にdownloadし直したassetのbyte数とSHA-256をrelease manifestへ照合します。

## Product verification

- production APKを対象Android versionへinstall/update
- expected signer、package、version、permission、cleartext/backup policy
- installed `base.apk`と公開APKのbyte identity
- paired Runner、registration、official APK取得、Build A/B、comparison
- Android標準installerの確認、history、cleanup/exportの主要journey

source build、文書check、tag作成のいずれかだけでrelease assetの検証を代替しません。
