# Architecture overview

- Status: Current public overview
- Updated: 2026-09-12

## Purpose

ReproDroidは、配布元が公開するAPKと、対応する公開sourceからローカルに生成したAPKを、同じrelease observationに結び付けて比較するためのシステムです。比較結果と補助証拠を提示しますが、sourceやAPKの安全性を自動的に証明しません。

## Components

### Android application

`reprodroid`が所有します。

- provider repository、release、APK候補の取得と表示
- 公式APKのdownload、検査、保存
- Runner Jobの作成・確認・状態同期
- Build A／Bと公式APKのraw比較
- 補助的なDEX／Manifest／resource差異表示
- 履歴、設定、保存容量、cleanup、監査export
- scheduled metadata checkと通知
- Android標準installerへのhandoff
- Android自身のlog export

Androidは、Runnerのfilesystem path、秘密情報、toolchain inventory truthを正本として複製しません。

### Runner

独立した`reprodroid-runner`が所有します。

- 許可されたpublic repositoryからのsource取得
- refからfull commit SHAへの解決
- build前source scanと必要なreview gate
- recipe、toolchain、sandbox、resource policyの固定
- Job、log、artifact、private build evidenceの永続化
- Androidへ公開するbounded API projection
- Runner自身と選択Jobのbounded log export

Runnerが`SUCCEEDED`になっても、公式APKとの一致や`Reproducible`を意味しません。最終的な三軸比較はAndroidが所有します。

### Providers

現在のprovider registryはpublic `github.com`とpublic `codeberg.org`に限定します。private token、任意Forgejo／Gitea、HTML scraping、外部assetの自動追跡は行いません。

## Main flow

```text
Public repository URL
  -> bounded metadata and source discovery
  -> release and APK candidate selection
  -> official APK download and inspection
  -> explicit build approval
  -> independent Runner Build A and Build B
  -> Official-vs-A, Official-vs-B, A-vs-B raw comparison
  -> bounded explanatory evidence
  -> user decision and Android standard installer
```

複数のAPK候補を一意に選べない場合は、利用者の明示選択まで停止します。未知schema、未知capability、別Runner、identity不一致、上限超過ではfail closedに停止します。

## Trust and comparison

- build成功は`Buildable`の証拠であり、公式APKとの一致ではありません。
- raw三軸の不一致は、意味比較で一致しても`Reproducible`へ昇格しません。
- dependency、determinism、source scan、sandbox evidenceは補助証拠であり、raw outcomeやinstall policyを上書きしません。
- signer一致とversion関係は、再現性判定とは別に扱います。

## Connectivity

release経路はmanual pairingとroot pin付きHTTPSを使用します。Androidが生成するcredentialはAndroid private storageで保護し、Runnerは端末別の失効可能なprincipalとして扱います。無認証HTTPは明示的なloopback development modeだけに限定し、release buildはcleartext Runner endpointを拒否します。

## Data and privacy

- 登録、履歴、設定、比較結果はAndroidのRoomへ保存します。
- RunnerはJob、resource、log、artifact、owner情報をSQLiteと専用state directoryへ保存します。
- metadata-only scheduled checkはbuildやinstallを開始しません。
- AndroidはOS全体のlogcatや他アプリのlogをexportしません。
- analytics、広告、tracking、自動crash uploadは使用しません。
- audit exportは読取用証跡であり、backup／restore機能ではありません。

## Repository boundary

公開製品codeと公開文書は`reprodroid`、Runner codeとRunner固有文書は`reprodroid-runner`、生の開発証跡と内部運用情報は非公開`reprodroid-project`に置きます。秘密鍵、credential、runtime DB、APK、log、build workspaceは、いずれのGit repositoryにも保存しません。
