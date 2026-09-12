# ADR-0006: 検証済みWrapper mainを直接起動しbuild homeをJob単位に分離する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-20

## Context

Phase 1Cではallowlist対象のGradle buildをWSL2ホスト上で実行する。`gradle-wrapper.jar`とdistributionを公式checksumで検査しても、リポジトリ内の`gradlew` shell scriptを実行すれば、そのscript自体が検査済みJARより先に任意コードを実行できる。また共有`GRADLE_USER_HOME`を使うと、あるJobが後続Jobのcacheへファイルを残し、Build Environment Manifestへ当該Jobと無関係な依存ファイルが混入する。

## Decision

- リポジトリ内の`gradlew`/`gradlew.bat`は実行しない
- `distributionUrl`、distribution SHA-256、`gradle-wrapper.jar` SHA-256の検査後、RunnerのJDKで`org.gradle.wrapper.GradleWrapperMain`を直接起動する
- distribution versionとWrapper JAR生成versionが異なる既知の上流では、両versionをrecipeへ別々に固定し、それぞれのGradle公式checksumへ照合する。公式JARという理由だけでrecipe未登録versionを受理しない
- `distributionSha256Sum`を補完する場合は既存fileへ文字列追記せず、解析済みPropertiesへ設定して全体を再保存し、再読込後も独立propertyとして同じ値になることを確認する
- 外部入力をshell文字列へ連結せず、Java実行ファイル、classpath、固定option、recipe内taskを引数配列で渡す
- build processの環境変数は`PATH`、locale、Android SDK等の必要項目に限定し、Runner processが保持する任意のcredentialを継承しない
- `HOME`と`GRADLE_USER_HOME`はJob workspace内へ分離する
- Job専用Gradle cacheの`modules-2/files-2.1`を走査し、取得された依存ファイルのpath、size、SHA-256をBuild Environment Manifestへ記録する
- distribution/Wrapper検証結果とManifestの相対path/SHA-256をRunner SQLiteにも保存し、Phase 1B schemaからtransactionalに移行する

## Consequences

- 改変されたWrapper shell scriptを実行する経路を除去できる
- Job間のcache poisoningとManifestへの過去Job混入を抑制できる
- Gradle distributionはcacheを共有しないため、実ビルドごとのdownload量と所要時間が増える
- build scriptとpluginは検証後も任意コードであり、workspace外のAndroid SDKやネットワークへアクセスできる。これはサンドボックスではなく、ADR-0003の明示同意モデルを置き換えない

## Rejected alternatives

### リポジトリ内の`./gradlew`を実行する

一般的な実行方法だが、Wrapper JARのchecksumが一致していてもshell script改変を防げないため採用しない。

### Runner全体でGradle cacheを共有する

高速だが、Job間の汚染境界と依存ファイル監査が曖昧になる。Phase 1Cでは速度より境界の明確さを優先する。

### Gradle distributionをRunnerが展開してsystem Gradleとして実行する

Wrapperと対象リポジトリの想定経路から外れ、追加の配布・展開・version管理が必要になるため採用しない。
