# ADR-0002: 模擬ビルドと信頼済み実ビルドを分離する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-20

## Context

UI/APIの高速なテストには外部処理のない模擬Jobが必要である。一方、初期縦切りでは実際のGit clone、Gradle build、APK転送、標準インストールまで確認する。1つの曖昧なmode flagで両者を切り替えると、テストが意図せず任意コードを実行する危険がある。

## Decision

実行モードを次の2つに限定する。

- `SIMULATED`: Git、Java、Gradleその他の外部プロセスを起動しない
- `REAL_TRUSTED`: allowlistとリポジトリ別レシピに従って実行する

`SimulatedBuildExecutor`と`TrustedRealBuildExecutor`を別実装とし、模擬側へprocess executorを依存注入しない。

`REAL_TRUSTED`はRunner起動時にデフォルト無効とする。明示的な起動設定とallowlistの二重ゲートを満たし、解決済みcommit SHAと実行内容を利用者が確認した場合だけ開始する。

## Consequences

- 第三者はホスト上で外部コードを実行せず模擬経路をテストできる
- 実ビルドを有効化しても未登録リポジトリは拒否される
- Android APIから任意Gradle taskや任意引数を渡せない
- 未登録の任意リポジトリを実行するには、将来のサンドボックス設計が必要になる
