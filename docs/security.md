# Security

## Android権限

| Permission | 使用目的 |
|---|---|
| `INTERNET` | public provider metadata、APK、paired Runner APIへ接続 |
| `POST_NOTIFICATIONS` | Android 13以降でrelease-check notificationを表示 |
| `QUERY_ALL_PACKAGES` | URL登録後に判明するtarget packageのinstalled versionとsignerを照会 |
| `REQUEST_INSTALL_PACKAGES` | eligible APKをAndroid標準`PackageInstaller`へ渡す |

notification permissionを拒否した場合、Settings → Notificationsから再度system promptを要求できます。release-check policyとOS permissionは別設定です。

### Package visibility

登録時点ではtarget packageが未確定なpublic repositoryを扱うため、固定`<queries>`一覧ではinstalled packageのversion/signerを照会できません。`QUERY_ALL_PACKAGES`は、download後にAPKから確定したpackageと、登録済みappのinstalled state照合に使用します。照会結果をanalyticsや広告へ送信しません。

## Network

production appはcleartext trafficを無効にし、Runner release接続にpaired HTTPSを使用します。Androidはexact origin、Runner identity、root public-key pin、端末別credentialを保存し、不一致時は接続を停止します。

debug appだけがloopback development HTTPを利用できます。paired HTTPS失敗時にHTTPへfallbackしません。

## Source build

Gradle build scriptとpluginは任意コードを実行できます。generic buildはRunnerのDocker内で実行され、Docker socket、privileged、host network、任意mount、Runner key、Android credential、production signing keyをcontainerへ渡しません。

現在のprofileには固定egress allowlist、Job単位hard disk/inode quota、remote attestationがありません。source scan findingなし、build成功、Build A/B一致はsource safetyの証明ではありません。

## APKの取得と検査

provider URL、redirect、MIME、size、byte countを上限付きで扱い、download後にSHA-256を計算します。APK bytesからpackage、version、signerを解析し、provider metadataだけでidentityを確定しません。

install/update時はAndroid標準`PackageInstaller`が最終確認とsigner-lineage判定を行います。ReproDroidはsilent install、root/Shizuku、署名検証回避を使用しません。

## 公開releaseの確認

Android `0.1.0-alpha05`の公開signer certificate SHA-256:

```text
42:E0:38:28:88:F6:EB:BD:22:A2:55:32:AD:64:95:CD:38:5D:54:CD:86:B0:0E:21:4A:D1:EA:CC:A4:D1:9A:BD
```

public certificateは[`release/reprodroid-release-certificate.pem`](../release/reprodroid-release-certificate.pem)です。

```bash
sha256sum -c SHA256SUMS
apksigner verify --verbose --print-certs reprodroid-0.1.0-alpha05.apk
```

次を同じGitHub Release内で照合します。

- immutable tagとsource commit
- APK asset名、byte数、SHA-256
- package、versionName、versionCode
- signer fingerprintとsignature scheme
- CycloneDX SBOM
- compatible Runner release
- release manifest schemaと内容

private key、keystore、passphraseはrepository、CI、Runner、build containerへ保存しません。

## Dataとprivacy

- application backupを無効化しています。
- Room24に登録、設定、history、comparison、install resultを保存します。
- Android log exportはReproDroid自身のbounded eventだけを含みます。
- OS全体のlogcat、他appのlog、Runner build logをAndroid exportへ含めません。
- analytics、広告、tracking、自動crash upload、自動diagnostic送信はありません。

audit/log exportはrestorable backupではありません。外部共有前に内容を確認します。
