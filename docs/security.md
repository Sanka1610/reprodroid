# Security

## Android権限

| Permission | 使用目的 |
|---|---|
| `INTERNET` | 公開配布元のメタデータ、APK、paired Runner APIへ接続 |
| `POST_NOTIFICATIONS` | Android 13以降でリリース確認の通知を表示 |
| `QUERY_ALL_PACKAGES` | URL登録後に判明する対象パッケージの導入済みバージョンと署名者を照会 |
| `REQUEST_INSTALL_PACKAGES` | 条件を満たすAPKをAndroid標準`PackageInstaller`へ渡す |
| `moe.shizuku.manager.permission.API_V23` | 利用者がShizuku／Sui経路を選んだ場合だけ、特権`PackageInstaller`セッションを要求 |

通知権限を拒否した場合は、設定 → 更新確認・通知から権限状態を確認します。リリース確認の設定とOSの権限は別設定です。

### Package visibility

登録時点では対象パッケージが未確定な公開リポジトリを扱うため、固定`<queries>`一覧では導入済みパッケージのバージョン・署名者を照会できません。`QUERY_ALL_PACKAGES`は、ダウンロード後にAPKから確定したパッケージ名と、登録済みアプリの導入状態の照合に使用します。照会結果を利用状況解析や広告へ送信しません。

## Network

製品版アプリは平文通信を無効にし、製品版のRunner接続にpaired HTTPSを使用します。Androidは完全一致するorigin、Runner ID、固定したルート公開鍵、端末別認証情報を保存し、不一致時は接続を停止します。

debug アプリだけがループバックの開発用HTTPを利用できます。paired HTTPS失敗時にHTTPへ切り替えしません。

### Experimental provider authentication

設定に保存するGitHub／Codebergトークンは、公開リポジトリに対する配布元APIへの要求を任意に認証する実験的機能です。保存時のオンライン検証、非公開リポジトリ、認証付きのAPK取得、Runnerによるソース取得には使用しません。

トークンは配布元ごとに独立したAndroid Keystore AES-256-GCM鍵で暗号化し、バックアップ対象外の`noBackupFilesDir`へ保存します。Room、ログ、監査エクスポート、URL、リダイレクト、Runner 要求、ビルド作業領域、コンテナへトークンを保存・転送しません。認証ヘッダーは`https://api.github.com`または`https://codeberg.org/api/v1`の完全一致するoriginにだけ付与し、リダイレクトは自動追跡しません。

ローカル削除は配布元側のトークンを失効しません。利用者は用途専用かつ利用可能な最小権限の読み取り専用トークンを選び、不要になったトークンを配布元側でも失効します。保存済み表示は端末内への保存状態を示します。有効性はAPI要求時の応答で確認します。

## Source build

ソースのビルドでは第三者のGradleスクリプトやプラグインを実行します。実行前に[Runnerの実行環境と制限](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/security.md#build-isolation)を確認してください。

## APKの取得と検査

配布元URL、リダイレクト、MIME、サイズ、受信バイト数を上限付きで扱い、ダウンロード後にSHA-256を計算します。APKからパッケージ名、バージョン、署名者を解析して識別情報を確定します。

既定のインストール・更新はAndroid標準`PackageInstaller`の利用者確認と署名者の継承関係の判定を維持します。任意のShizuku／Sui経路は、利用者がReproDroidへ権限を付与し、直前のAPKのバイト列・識別情報・署名者の再検査を通過したうえで、既存インストールと署名者が一致する更新、または`Reproducible`判定済み成果物にだけ使用します。警告確認が必要なAPKは特権経路を使わず、標準インストーラーへ戻ります。低target SDK制限、署名検証、バージョン制約は回避しません。

「Google Playをinstallerとして記録」は既定OFFの互換設定です。特権セッションのinstaller-of-recordを`com.android.vending`にしますが、APKの実取得元や検証結果は変更せず、ReproDroidの監査履歴にも実際の配布元と成果物の識別情報を保持します。

## 公開releaseの確認

Android `0.1.0-alpha05`の署名証明書 SHA-256:

```text
42:E0:38:28:88:F6:EB:BD:22:A2:55:32:AD:64:95:CD:38:5D:54:CD:86:B0:0E:21:4A:D1:EA:CC:A4:D1:9A:BD
```

公開証明書は[`release/reprodroid-release-certificate.pem`](../release/reprodroid-release-certificate.pem)です。

対象ReleaseのAPK、SBOM、`release-manifest.json`、`SHA256SUMS`を同じディレクトリに取得します。`APK`には取得したAPKのパスを指定してください。

```bash
APK=./reprodroid-0.1.0-alpha05.apk # この例を対象版のファイル名へ変更
sha256sum -c SHA256SUMS
apksigner verify --verbose --print-certs "$APK"
```

次を同じGitHub Release内で照合します。

- 固定tagとソースコミット
- APKのファイル名、バイト数、SHA-256
- package、versionName、versionCode
- 署名証明書のフィンガープリントと署名方式
- CycloneDX SBOM
- 対応するRunnerの版
- リリースmanifestのスキーマと内容

秘密鍵、keystore、passphraseはリポジトリ、CI、Runner、ビルドコンテナへ保存しません。

## Dataとprivacy

- アプリのバックアップを無効化しています。
- 配布元トークンはAndroid Keystoreで暗号化したバックアップ対象外の認証情報ファイルにだけ保存します。
- Roomに登録、設定、履歴、比較結果、インストール結果を保存します。
- Android ログ出力はReproDroid自身の件数等を制限した運用イベントだけを含みます。
- OS全体のlogcat、他アプリのログ、Runnerのビルドログは出力対象外です。
- 利用状況解析、広告、トラッキング、クラッシュ情報・診断情報の自動送信はありません。

監査記録・ログの出力に復元機能はありません。外部共有前に内容を確認します。

## 実装参照

| 内容 | 定義 |
|---|---|
| 権限・バックアップ・通信設定 | [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)の`uses-permission`と`application` |
| 特権インストールの条件 | [PrivilegedInstallPolicy.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/PrivilegedInstallPolicy.kt)の`isEligible` |
| データベースの版と移行 | [互換性](compatibility.md#配布版とチェックアウトを識別する) |

配布元認証の保存処理は[ProviderCredentialStore.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/provider/ProviderCredentialStore.kt)の`AndroidKeystoreProviderCredentialStore`を参照してください。
