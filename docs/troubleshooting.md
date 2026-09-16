# Troubleshooting

## APK checksumが一致しない

APK、`SHA256SUMS`、release manifestを同じGitHub Releaseから再取得します。filenameが同じでもinstallしません。browser/proxy/cacheを変えた場合も再計算します。

## Signerが一致しない

`apksigner verify --verbose --print-certs`のcertificate SHA-256を[Security](security.md#公開releaseの確認)と比較します。debug APK、第三者rebuild、別release assetはproduction signerと一致しません。

既存appのsigner lineageと新APKが互換でない場合、Androidはupdateを拒否します。uninstallはapp dataを失う可能性があるため、回避策として自動実行しません。

## Runnerへ接続できない

- Settings → Runnerで保存endpoint、Runner identity、接続stateを確認します。
- ADB reverseでは`adb reverse --list`を確認します。
- LAN/WSL2ではAndroidからhost/portへ到達できるか確認します。
- certificate SAN、advertised endpoint、Androidのoriginが一致するか確認します。
- PCで`principals-list`を実行し、principalが失効していないか確認します。

pin/identity不一致時は意図したRunnerかを確認して再pairします。HTTP fallbackやcertificate検証無効化は使用しません。

## Pairing requestが表示されない

- Runnerが`PAIRED_HTTPS`で起動していることを確認します。
- `pairing-open`のinvitationを5分以内に1回だけ使用します。
- Androidが表示するrequest/fingerprintと`pairing-list`を比較します。
- endpoint変更後は`security-change-endpoint`と新しいinvitationを使用します。

## Releaseが見つからない

- repository URLとproviderを確認します。
- provider rate limit、pagination、asset count/size上限を確認します。
- private repository、GitLab、任意Forgejo/Giteaは対象外です。
- 複数APK assetがある場合はOpen technical detailsでexact assetを選択します。

## Buildが開始しない

- Runnerのreal buildとDocker設定を確認します。
- resolved full commit、configuration digest、toolchain inventoryを確認します。
- RCE確認とsource scan reviewがBuild A/Bそれぞれに完了しているか確認します。
- stale preview/configurationの場合は新しいJobを作成します。

## Toolchain不足

Settings → Runner → Managed build toolchainsでcatalogを再取得し、planを解決します。license、download size、必要容量を確認し、stale planを再利用しません。

## ComparisonがIncomparableになる

次を確認します。

- Official、Build A、Build Bが同じrelease observationに属する
- package/version、artifact size/digestが取得済み
- resolved commitとconfiguration digestが一致
- Jobとartifactが同じRunner identity/principalに属する
- raw comparisonに必要なentryが上限内で取得できた

欠けたevidenceを`MATCH`として補完しません。

## Installが拒否される

- package名とversionCodeを確認します。
- installed appとのsigner relationを確認します。
- Settingsで「不明なアプリのインストール」の許可状態を確認します。
- Android installerのresultを確認します。

official APKとlocal buildのsignerが異なる場合は通常updateできません。

## Storage不足

Settings → Data managementまたはRunner storageからsummaryとcleanup previewを確認します。current comparison、pending review、hold、参照中resourceは保護されます。databaseやartifact directoryを手動で部分削除しません。
