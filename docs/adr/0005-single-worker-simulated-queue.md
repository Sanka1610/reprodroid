# ADR-0005: Phase 1BのJob実行は単一workerキューとする

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-20

## Context

Phase 1Bでは、複数の`SIMULATED` Jobを非同期に受け付け、進捗・ログ・結果を永続化する必要がある。後続の`REAL_TRUSTED`はGradle、Android SDK、ディスク、メモリを大きく消費するため、Phase 1Bだけ無制限の並列実行を前提にすると1Cで実行モデルを変更することになる。

キュー自体をRunner再起動後に自動再開すると、ADR-0004の「実行途中のJobを自動再実行しない」という安全条件にも反する。

## Decision

- Job requestと状態は受付時にSQLiteへ保存し、HTTP responseとは分離して非同期実行する
- 実行キューはFIFOの単一workerとし、同時実行数は1とする
- キューの待機・実行中にcancelされたJobは`CANCELLED`を終端状態とし、workerが上書きしないよう状態取得を原子的に行う
- Runner再起動時、`CREATED`、`QUEUED`、`BUILDING`を含む実行対象状態はキューへ戻さず`INTERRUPTED`とする
- Phase 1Bの`SimulatedBuildExecutor`にはprocess実行の依存関係を与えない
- artifactはAPKメタデータだけを生成し、ファイル本体の生成・配信はPhase 1Dまで行わない
- Androidの画面ポーリングとWorkManager同期は同じmutexで直列化し、古いresponseが新しい状態を上書きしないようにする
- `REQUEST_INSTALL_PACKAGES`は標準インストーラを実装するPhase 1Dまで宣言しない

## Rejected alternatives

### Jobごとの無制限並列実行

模擬Jobだけなら短時間で完了するが、1Cの実ビルドへ同じモデルを延長するとホスト資源を制御できない。並列度を設定可能にする要件が生じるまでは採用しない。

### 再起動後の自動再キュー

利用者が認識しない状態で後続Phaseの任意コード実行を再開する危険がある。retry APIで新しいJob IDを発行し、明示操作として扱う。

### 模擬APKファイルの生成

Phase 1Bの受け入れ条件はAPKメタデータまでであり、APKらしい任意byte列を配ると1DのSHA-256照合やpackage解析と混同される。content endpointは明示的に利用不可とする。

## Consequences

- 状態遷移、cancel、再起動処理を実ビルド導入前に同じ制御面で検証できる
- 長時間Jobが後続Jobを待たせるため、将来並列度が必要になった場合は資源上限とともに別ADRで決定する
- SQLiteはJobの履歴を保持するが、実行キューの自動復元機構にはしない
