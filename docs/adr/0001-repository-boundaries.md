# ADR-0001: 3つの独立リポジトリで管理する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Superseded in part by [ADR-0027](0027-public-documentation-and-private-development-boundary.md)
- Date: 2026-08-20

## Context

AndroidアプリとRunnerは技術スタック、リリース、CIが異なる。一方、2リポジトリだけでは、全体設計、Runner API、schema、WSL2環境構築、互換性、Codexタスク単位の作業レポートの正本が定まらない。

## Considered options

1. app/runnerの2リポジトリへ横断文書とsetup scriptを複製する
2. app/runner/projectの3リポジトリを兄弟として配置する
3. projectをsuperprojectとし、app/runnerをGit submoduleにする

## Decision

`reprodroid-project`、`reprodroid`、`reprodroid-runner`を独立したGitリポジトリとして管理する。Git管理外のローカルワークスペース直下へ兄弟として配置し、現段階ではsubmoduleを使用しない。

`reprodroid-project`は実装コードを持たず、全体設計、ADR、API・schema、環境構築、互換性、作業レポートを管理する。

2026-09-12以降、公開設計・ADR・API・release metadataの正本を`reprodroid`へ段階移行し、`reprodroid-project`は非公開の開発計画・生の検証証跡・handoff・ローカル互換性を管理する。この変更はADR-0027を正本とし、本ADRの3つの独立Git repositoryを維持する判断は変更しない。

## Consequences

- appとrunnerを単独でclone、build、releaseできる
- 横断仕様とsetup scriptの複製を避けられる
- 横断変更は複数コミットになるため、projectの作業レポートと互換性情報で関連付ける
- 単独cloneではprojectのsetup scriptが付属しないため、各READMEに手動要件とprojectへの参照を記載する
- remoteとリリース運用が安定した段階でsubmoduleまたはmanifest方式を再評価する
