# ReproDroid release signing policy

このディレクトリには、公開可能なrelease signing identityと運用方針だけを置きます。private key、keystore、passphrase、recovery secret、CI credentialを置いてはいけません。

## Production Android signing identity

- Generated: 2026-09-09
- Purpose: ReproDroid production APK signing only
- Keystore type: PKCS12
- Key algorithm and size: RSA 4096 bit
- Signature algorithm: SHA256withRSA
- Alias: `reprodroid-release`
- Distinguished name: `CN=ReproDroid Release, O=ReproDroid, C=JP`
- Valid from: 2026-09-09
- Valid until: 2051-09-03
- Signer certificate SHA-256: `42:E0:38:28:88:F6:EB:BD:22:A2:55:32:AD:64:95:CD:38:5D:54:CD:86:B0:0E:21:4A:D1:EA:CC:A4:D1:9A:BD`
- Public certificate: [reprodroid-release-certificate.pem](reprodroid-release-certificate.pem)
- Public certificate file SHA-256: `d2466af92dca19bd2329cc2e452dbba70efb9d364961296a969b78ae4c60d2cc`

Encrypted keystore、そのpassphrase、暗号化backupはrepository外で分離管理します。private pathやprivate-file hashは公開しません。identityを復旧できない場合は、明示的なkey-transition計画なしに別鍵へ置き換えず、配布を停止します。

## Manual signing boundary

CIはunsigned APK、CycloneDX JSON SBOM、checksum、unsigned provenanceを生成します。CIとbuild Runnerへproduction keyを渡しません。

Release custodianは次を実施します。

1. source commitとunsigned APK／SBOM digestをCI provenanceと照合する。
2. repositoryの`scripts/sign-release-apk.sh`を、repository・CI・Runnerと分離した署名環境で実行する。
3. passphraseはfile経由で渡し、command lineやshell historyへ含めない。
4. `REPRODROID_EXPECTED_SIGNER_SHA256`に上記fingerprintを設定し、別鍵をfail closedで拒否する。
5. 生成したprovenanceを確認し、`apksigner verify --verbose --print-certs`を独立して実行する。
6. package、version、permission、HTTPS policy、通常upgrade、self-update gateを使い捨て端末で確認する。
7. signed APK、Runner archive、SBOM、source、toolchain、署名前後digest、signer fingerprint、compatibilityをrelease checklistで対応付ける。

成果物の生成・検証は、push、`main`統合、GitHub Release、その他の公開を許可しません。
