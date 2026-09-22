# 再現可能性の判定

## 比較対象

1つのリリース観測へ公式APK（Official）と独立したBuild A／Bを関連付け、Official vs A、Official vs B、A vs Bを比較します。Build A／Bは別Job、別チェックアウト、別ビルド保存先、別成果物として実行します。

## Raw三軸

| 結果 | 意味 |
|---|---|
| `MATCH` | 比較対象エントリの名前、展開後サイズ、SHA-256が一致 |
| `DIFFERENT` | 比較対象エントリの追加・欠落・ハッシュ差異がある |
| `INCOMPARABLE` | 識別情報、構成、成果物の不足・不整合、または検査上限超過で比較できない |

[ApkContentComparator.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/artifact/ApkContentComparator.kt)の`compare`は入力APK全体のサイズとSHA-256を保存済み情報と照合した後、`isComparedEntry`で対象を選びます。対象はルートの`classes.dex`／後続DEXと`lib/<ABI>/*.so`です。両APKに`classes.dex`が必要です。エントリ数・展開サイズの上限は同クラスのコンストラクタに定義しています。

この結果の比較範囲は実行コードです。署名・ZIP圧縮方式を含むAPK全体のバイト一致を確認する場合は、APK全体のSHA-256を別途照合します。

## Reproducible

Androidの表示は[ManagedAppEntities.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/local/ManagedAppEntities.kt)の`RegisteredAppRecord.currentComparison`、`trustLevel`、`repeatedBuildTrustLevel`が決定します。

繰り返しビルドのプロトコルでは、3軸がすべて`MATCH`なら`Reproducible`、いずれかが`DIFFERENT`なら`Different`です。比較不能の軸がある場合は`Incomparable`となり、Runner Jobの失敗が原因なら`Failed`として扱います。比較が未完了でローカル成果物だけがある場合は`Buildable`です。保存済みの旧プロトコルでは単一の`outcome`から表示を決めます。

インストールの実行条件は[AppInstallationCoordinator.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/repository/AppInstallationCoordinator.kt)の`installManagedApp`で別途確認します。Runner APIの`reproducible`フィールドは、保存するJobと要求の条件も含む[API上の定義](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/api/builds-v2.md#comparisonを作成する)を使います。

## 補助証拠

DEXのクラス・メソッド、ネイティブライブラリ、Manifest、リソース、依存関係、ビルド環境、ソーススキャン、実行環境の情報は差異の調査に使用します。[AdvancedApkComparator.kt](../app/src/main/kotlin/com/sanka1610/reprodroid/data/artifact/AdvancedApkComparator.kt)の`AdvancedApkComparator`が補助比較を行い、rawの判定結果を保持します。

## 結果と次の操作

| 確認する内容 | 参照先 |
|---|---|
| Jobの実行・スキャン待ち・失敗 | [ビルド操作](user-guide.md#jobsとsource-scan) |
| 導入済みAPKとのバージョン関係 | [リリース確認](user-guide.md#リリースを確認する) |
| 署名とインストール条件 | [APKの取得と検査](security.md#apkの取得と検査) |
| Dockerの隔離条件と制限 | [Runnerセキュリティ](https://github.com/Sanka1610/reprodroid-runner/blob/main/docs/security.md#build-isolation) |
| 対応APK形式 | [互換性](compatibility.md#対応する配布元とapk形式) |
