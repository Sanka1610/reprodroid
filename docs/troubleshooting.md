# Troubleshooting

## APK checksumが一致しない

APK、`SHA256SUMS`、リリース manifestを同じGitHub Releaseから再取得します。filenameが同じでもインストールしません。browser/proxy/cacheを変えた場合も再計算します。

## Signerが一致しない

`apksigner verify --verbose --print-certs`の証明書 SHA-256を[Security](security.md#公開releaseの確認)と比較します。debug APK、第三者rebuild、別リリース assetは製品版署名者と一致しません。

既存アプリの署名者 lineageと新APKが互換でない場合、Androidは更新を拒否します。uninstallはアプリ dataを失う可能性があるため、回避策として自動実行しません。

## Runnerへ接続できない

- 設定 → 検証環境で保存エンドポイント、Runner identity、接続状態を確認します。
- ADB reverseでは`adb reverse --list`を確認します。
- LAN/WSL2ではAndroidからhost/portへ到達できるか確認します。
- 証明書 SAN、advertised エンドポイント、Androidのoriginが一致するか確認します。
- PCで`principals-list`を実行し、接続主体が失効していないか確認します。

pin/identity不一致時は意図したRunnerかを確認して再pairします。HTTP fallbackや証明書検証無効化は使用しません。

## Pairing requestが表示されない

- Runnerが`PAIRED_HTTPS`で起動していることを確認します。
- invitationの[期限と使用条件](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/api/pairing.md#requestを作成する)を確認します。
- Androidが表示するrequest/fingerprintと`pairing-list`を比較します。
- エンドポイント変更後は`security-change-endpoint`と新しいinvitationを使用します。

## Releaseが見つからない

- リポジトリ URLとproviderを確認します。
- provider rate 上限、pagination、asset count/size上限を確認します。
- 非公開リポジトリ、GitLab、任意Forgejo/Giteaは対象外です。
- 複数APK assetがある場合は取得画面で対象APKを選択します。

## Buildが開始しない

- Runnerの実ビルドとDocker設定を確認します。
- resolved full コミット、構成ハッシュ、ツールチェーン inventoryを確認します。
- RCE確認とソーススキャン reviewがBuild A/Bそれぞれに完了しているか確認します。
- 期限切れまたは内容変更済みの preview/configurationの場合は新しいJobを作成します。

## Toolchain不足

設定 → 検証環境 → Managed ビルド toolchainsでカタログを再取得し、計画を解決します。ライセンス、ダウンロードサイズ、必要容量を確認し、期限切れまたは内容変更済みの計画を再利用しません。

## ComparisonがIncomparableになる

次を確認します。

- Official、Build A、Build Bが同じリリース observationに属する
- package/version、成果物 size/digestが取得済み
- resolved コミットと構成ハッシュが一致
- Jobと成果物が同じRunner identity/principalに属する
- raw比較に必要なentryが上限内で取得できた

欠けたevidenceを`MATCH`として補完しません。

## Installが拒否される

- package名とversionCodeを確認します。
- installed アプリとの署名者 relationを確認します。
- Settingsで「不明なアプリのインストール」の許可状態を確認します。
- Android installerのresultを確認します。

official APKとlocal ビルドの署名者が異なる場合は通常更新できません。

## Storage不足

設定 → データ管理またはRunner ストレージからsummaryとcleanup プレビューを確認します。current comparison、pending review、hold、参照中リソースは保護されます。databaseや成果物ディレクトリを手動で部分削除しません。
