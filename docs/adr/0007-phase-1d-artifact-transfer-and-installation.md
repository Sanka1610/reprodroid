# ADR-0007: APK配信領域と標準インストール結果を分離する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-21
- Superseded in part: package visibilityの決定は[ADR-0008](0008-query-all-packages-for-url-registered-apps.md)で置き換えた

## Context

Phase 1Dでは、Runnerの作業ツリーに生成されたAPKをAndroidへ転送し、転送後の検証結果と標準`PackageInstaller`の結果を再起動後も確認できる必要がある。ビルド作業ツリーのpathをそのままHTTP APIへ露出すると、path traversal対策、ビルド後のファイル変化、作業ツリー削除の影響が配信APIへ混入する。また、インストール結果をJob状態へ上書きすると、ビルド成功と利用者によるインストール操作を区別できない。

Androidの既存packageとの署名比較にはpackage visibilityも関係する。全package可視性を得る`QUERY_ALL_PACKAGES`はPhase 1の固定対象に対して過剰であり、Play配布時のポリシー上の制約も増やす。

## Decision

Runnerは検出したAPKを、ビルド作業ツリーとは別の`artifacts/{jobId}/{artifactId}.apk`へコピーする。SQLiteにはstate directoryからの相対pathを保存し、次をすべて満たす場合だけ配信する。

- Jobが`SUCCEEDED`
- Jobとartifact IDの組がSQLiteへ登録済み
- 実pathが当該Jobのartifact directory内
- 最終pathがsymbolic linkではない
- 配信直前のsizeとSHA-256が登録値と一致

配信レスポンスはAPK MIME type、`Content-Length`、SHA-256由来の`ETag`を返す。Phase 1C以前に作成され、content pathを持たないartifactは自動推測せず`ARTIFACT_CONTENT_UNAVAILABLE`とする。必要ならJobを明示的にretryする。

Androidはserver由来のfile nameやIDを保存pathへ直接使用せず、Job IDとartifact IDのSHA-256からアプリ専用領域内のpathを作る。`.part.apk`へstreaming downloadし、MIME type、`Content-Length`、`ETag`、受信byte数、Android側で再計算したSHA-256を検査する。すべて一致し、`PackageManager`が署名付きAPKとしてpackage/version/signing certificateを解析できた場合だけ、検証済みpathへ移動する。

Room v3ではartifactごとにdownload状態、local path、Android側size/SHA-256、package/version、署名証明書fingerprint、既存packageとのcurrent signer比較を保存する。インストール操作はJobと分離した`install_attempts`へ、`PackageInstaller` session ID、状態、platform status code/message、時刻を保存する。`STATUS_FAILURE_ABORTED`はキャンセルとして記録し、それ以外の拒否理由はplatformのstatus/messageを失わず保存する。

インストールは`PackageInstaller.Session`へ検証済み単一APKを書き込み、常にuser actionを要求する。unknown app sourcesが未許可なら`ACTION_MANAGE_UNKNOWN_APP_SOURCES`へ誘導する。silent install、自動アンインストール、root/Shizuku、署名検証回避は行わない。

package visibilityはPhase 1の固定対象`app.revanced.android.gms`だけをManifestの`<queries>`へ登録する。`QUERY_ALL_PACKAGES`は宣言しない。将来allowlistへ対象を追加する際は対応する限定queryを追加するか、既存packageの可視性がない状態をUIで明示する。

このpackage visibility判断は、Phase 1D着手時点の固定対象を前提とした履歴として残す。URL入力から任意のアプリを登録し、実行時に判明したpackage nameのインストール状態、version、署名情報を照会する製品要件が確定したため、現在の判断はADR-0008に従う。

## Consequences

- build workspaceの内部構造を配信APIへ公開せず、保存後に変化したAPKをfail closedで拒否できる
- 転送検証、APK解析、インストール試行をアプリ再起動後も区別して表示できる
- Phase 1Cの既存artifactはcontent pathがないため、そのままではdownloadできない
- APKはbuild workspaceとartifact storeへ一時的に重複保存される。保存期間と容量上限は後続Phaseで設計する
- package visibilityに関するこのADRの制約はADR-0008で置き換えられ、現在のManifestは`QUERY_ALL_PACKAGES`を宣言する

## Phase 1E validation note

2026-08-24のAndroid 16 E2Eで、`PackageInstaller.Session.commit()`のstatus `PendingIntent`自体は届くものの、creator側のbackground activity launch opt-inがないと標準installer確認UIへの遷移がplatformにblockされることを確認した。明示的かつ非exportedの内部status activityに対し、API 36では`MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`、API 34／35では`MODE_BACKGROUND_ACTIVITY_START_ALLOWED`を設定する。これはuser action必須の標準確認UIを開くためのplatform適合であり、silent install、unknown sources、署名検証を迂回しない。

また、process停止やsession破棄によりterminal callbackを受信できない場合、Roomのattemptを永久に`COMMITTED`へ残さない。更新時刻から30秒を超え、かつ自アプリが所有するactive PackageInstaller sessionにIDが存在しない非terminal attemptだけを起動時に`FAILED`へ回収する。platformから受信していないstatus codeはnullのまま保ち、回収理由を独自messageとして保存する。
