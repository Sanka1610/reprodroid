# UIアーキテクチャ

## 画面と状態の担当

[MainActivity.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/MainActivity.kt)の`MainActivity`が起動時と`onNewIntent`の通知入力を受け、[AppHost.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/app/AppHost.kt)の`ReproDroidApp`が画面・状態・操作結果を接続します。

| パッケージ | 担当 |
|---|---|
| `ui/app` | 画面の組み立て、通知・アンインストール・文書出力の結果受信 |
| `ui/navigation` | 保存済みルートの解析、戻り先、不正・欠落入力の処理 |
| `ui/apps` | アプリ一覧、検索、グループ管理、追跡履歴 |
| `ui/add` | URL入力、解析結果、登録 |
| `ui/appdetail` | アプリ概要、取得、検証、設定、技術情報 |
| `ui/comparison` | 比較結果の表示 |
| `ui/settings` | 設定カテゴリと各設定画面 |
| `ui/runner`、`ui/jobs` | Runner接続、認証、Job、保存容量、ツールチェーン |
| `ui/shared` | 共通部品、ラベル、表示形式 |
| `ui/state`、`ui/delegate` | 機能別の状態、操作、単発の結果通知 |

各パッケージは[uiソース](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/)を参照してください。利用者向けの画面操作は[利用ガイド](../user-guide.md)に集約しています。

## ルートと戻る操作

保存するルート文字列と互換入力は[ReproDroidRoute.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/navigation/ReproDroidRoute.kt)の`ReproDroidRoute`が定義します。通知入力も同じ解析を通り、レコードIDには正規形のUUIDを要求します。

戻り先は[ReproDroidNavigationPolicy.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/navigation/ReproDroidNavigationPolicy.kt)の`backDestination`、レコード欠落時の遷移は`missingAppDestination`、通知先は`releaseNotificationRoute`が決定します。検証中に前提設定へ移った場合は元アプリの検証へ戻り、一覧では複数選択、検索の順に閉じた後、通常のAndroid終了操作へ進みます。

## 状態と操作結果

[ManagedAppsDelegates.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/delegate/ManagedAppsDelegates.kt)の`ManagedAppsDelegates`は、共有のイベントストアとアプリID単位の操作制御を使って機能別delegateを組み立てます。ViewModelが公開する状態を画面側で購読し、遷移時には現在のルートと対象IDに合う操作結果だけを消費します。

ルート・入力選択・検索表示・検証の戻り先は画面の保存可能状態として保持します。インストールやスキャンの確認は対象に結び付いた一時状態です。永続設定・履歴は[Roomの定義](../compatibility.md#配布版とチェックアウトを識別する)を参照してください。

選択中のアプリIDは画面の保存可能状態で保持し、一覧から消えたIDは除きます。一括グループ変更はRepositoryのトランザクション内で全対象の更新日時とグループの存在を確認してから適用します。成功時だけ選択を解除し、失敗時は選択を保持します。

一覧のグループ表示は`ReproDroidApp`内の`displayPreferences`が所有します。保存先`app_list_display`の`grouped`は既定`true`で、表示切替に使用します。

一覧・詳細のプルダウンと追跡欄のリロードは`ReleaseDelegate.checkNow`へ集約します。一覧の確認はアプリごとの操作制御を通して順番に待ち、APKをダウンロードしません。

## 主操作と実行条件

[AppActionPolicy.kt](../../app/src/main/kotlin/com/sanka1610/reprodroid/ui/appdetail/AppActionPolicy.kt)は、候補の検査状態・導入済みバージョンとの関係・進行中の比較から表示する操作を決定します。取得済みAPKのインストールは[AppInstallationCoordinator](overview.md#コンポーネントの責務)が実行直前に再検証します。

比較、インストール、データ削除などの保存処理と検証はRepository／Coordinatorが担当します。画面には許可された操作とその結果を表示します。
