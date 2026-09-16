# Getting started

## 1. Android APKを取得する

[ReproDroid v0.1.0-alpha05](https://github.com/Sanka1610/reprodroid/releases/tag/v0.1.0-alpha05)からAPK、CycloneDX JSON SBOM、`SHA256SUMS`、`release-manifest.json`を取得します。

```bash
sha256sum -c SHA256SUMS
apksigner verify --verbose --print-certs reprodroid-0.1.0-alpha05.apk
```

確認する値:

- package: `com.sanka1610.reprodroid`
- versionName/versionCode: `0.1.0-alpha05` / `5`
- APK SHA-256: release manifestの`assets` entry
- signer certificate SHA-256: `42:E0:38:28:88:F6:EB:BD:22:A2:55:32:AD:64:95:CD:38:5D:54:CD:86:B0:0E:21:4A:D1:EA:CC:A4:D1:9A:BD`

確認後、Android標準installerでAPKを開きます。

## 2. Runnerを導入する

[Runner v0.1.0-alpha02](https://github.com/Sanka1610/reprodroid-runner/releases/tag/v0.1.0-alpha02)からZIP、CycloneDX JSON SBOM、`SHA256SUMS`、`release-manifest.json`を取得します。

```bash
sha256sum -c SHA256SUMS
mkdir -p "$HOME/.local/opt" "$HOME/.local/state/reprodroid-runner"
unzip reprodroid-runner-0.1.0-alpha02.zip -d "$HOME/.local/opt"
```

詳細は[Runner installation](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/installation.md)を参照してください。

## 3. Paired HTTPSを初期化する

ADB reverseを使用するloopback例:

```bash
export REPRODROID_STATE_DIR="$HOME/.local/state/reprodroid-runner"
export REPRODROID_TRANSPORT_MODE=PAIRED_HTTPS
export REPRODROID_HOST=127.0.0.1
export REPRODROID_PORT=8443
export REPRODROID_ADVERTISED_ENDPOINT=https://127.0.0.1:8443
RUNNER="$HOME/.local/opt/reprodroid-runner-0.1.0-alpha02/bin/reprodroid-runner"
"$RUNNER" security-init
"$RUNNER"
```

別terminalで次を実行します。

```bash
adb reverse tcp:8443 tcp:8443
"$RUNNER" pairing-open
```

LAN/WSL2接続ではAndroidから到達できるhostをbind、advertised endpoint、certificate SANへ使用します。

## 4. AndroidとRunnerをペアリングする

1. ReproDroidを開きます。
2. Settings → Runner → Runner settings and authenticationを開きます。
3. `pairing-open`のinvitationを入力します。
4. Android画面とPCの`pairing-list`に表示されるfingerprintを比較します。
5. 一致するrequestを承認します。

   ```bash
   "$RUNNER" pairing-list
   "$RUNNER" pairing-approve <requestId>
   ```

6. Android画面でRunner identityと接続状態を確認します。

## 5. Repositoryを登録する

1. Add appを開きます。
2. public `https://github.com/...`または`https://codeberg.org/...` URLを入力します。
3. provider、owner、repository、release候補を確認します。
4. management mode、installation source、release variant、ABIを選択します。
5. repository identityとpolicyを確認して登録します。

候補が一意でない場合、上限へ達した場合、不明なprovider/schemaの場合は確認画面で停止します。登録操作だけではAPK download、toolchain install、build、comparison、installを開始しません。

## 6. Toolchainを準備する

Settings → Runner → Managed build toolchainsでcatalogを取得し、build configurationに必要なJDK、Gradle、Android SDKを計画します。license、download size、expanded size、digestを確認してinstallします。

## 7. Build A／Bを実行する

1. InformationからOpen technical detailsを選び、releaseと公式APKを確認します。
2. 複数APK候補がある場合はexact assetを選択してdownloadします。
3. build configurationを保存します。
4. Runner jobsでBuild Aのcommit、configuration、Docker、RCE warningを確認します。
5. source scan findingがある場合はdigest-bound findingを確認します。
6. Build Bでも独立して同じ確認を行います。

## 8. 比較結果を確認する

Comparison evidenceでOfficial vs A、Official vs B、A vs Bのraw resultを確認します。DEX、native library、Manifest、resource、dependency、sandbox evidenceは理由を調べるために使用し、raw resultを上書きしません。

判定条件は[Reproducibility](reproducibility.md)、通常操作は[User guide](user-guide.md)を参照してください。
