# ADR-0003: 初期実ビルドをallowlist付きホスト実行とする

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-20

## Context

Gradle buildは`build.gradle(.kts)`、plugin、annotation processor等を通してホスト上で任意コードを実行できる。Wrapper検査だけではこの性質を除去できない。一方、初期のE2Eには選定済みリポジトリの実ビルドが必要であり、Docker隔離はWSL2、Android SDK、cache、ADBを含めて別の設計課題になる。

## Decision

初期の`REAL_TRUSTED`は開発者向けアルファとしてWSL2ホスト上で実行する。ただし次を必須とする。

- GitHub HTTPS URLだけを許可
- リポジトリallowlist
- branch/tagを解決したcommit SHAの確認とdetached checkout
- build root、Gradle task、artifact pathをレシピで固定
- submoduleとGit LFSをデフォルト無効
- 外部入力をshell文字列へ連結しない
- timeout、cancel、監査ログ
- Gradle distributionとWrapper JARの公式checksum検証
- 検査不能、不一致、未対応バージョンは拒否

リポジトリに`distributionSha256Sum`がない場合、レシピが明示的に許可した対象だけ、RunnerがGradle公式checksumを取得して配布ZIPを検証する。結果は`SUPPLIED_BY_RUNNER`として記録する。公式値を検証できない場合に続行するoverrideは設けない。

## Explicit limitations

- allowlistはサンドボックスではない
- 開発者アカウント、commit、依存関係、pluginが侵害された場合はホストも侵害されうる
- Wrapper検証後もbuild scriptは任意コードである
- Runnerは未信頼の任意リポジトリを安全にビルドできるとは主張しない

## Future

未登録リポジトリの実ビルドを許可する前に、コンテナ等の隔離、read-only mount、network policy、resource limit、cache分離を設計する。
