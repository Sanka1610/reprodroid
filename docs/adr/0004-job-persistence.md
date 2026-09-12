# ADR-0004: RunnerとAndroidの両方でJobを永続化する

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-08-20

## Context

Android buildは数分以上かかり、RunnerやAndroidアプリの再起動をまたぐ。メモリ内Jobだけでは、設計上必要な「アプリを閉じても後から確認する」を満たさない。一方、実行中だった任意コードをRunner再起動後に自動再開するのは安全ではない。

## Decision

RunnerはSQLiteへJob、request、resolved commit、状態、進捗、確認、エラー、ログ索引、artifactメタデータ、checksum、Wrapper検査結果を保存する。APK、ログ本体、cloneしたソースは専用state directoryへ保存し、SQLite BLOBにはしない。

Runner起動時、実行途中のJobは`INTERRUPTED`へ遷移させ、自動再実行しない。

AndroidはRoomへJob、状態、ログカーソル、APK候補、ダウンロード、インストール結果を保存する。画面表示中はCoroutineで短周期ポーリングし、バックグラウンドと再起動後の同期はWorkManagerと再表示時の即時同期を使用する。

## Consequences

- app/Runner再起動後も履歴と結果を参照できる
- 実ビルドの再試行は利用者の明示操作になる
- source、APK、ログの保存期間と容量上限が必要になる
- APIログはシーケンス番号を持ち、差分取得できる必要がある
