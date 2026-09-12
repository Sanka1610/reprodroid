# ADR-0027: Public documentation and private development boundary

- Status: Accepted
- Date: 2026-09-12

## Context

従来は、Android、Runner、横断設計をそれぞれ`reprodroid`、`reprodroid-runner`、`reprodroid-project`で管理していた。`reprodroid-project`には公開可能なADR／API／設計と、生の作業report、handoff、host固有path、実行証跡が同居していた。そのため、製品sourceを読む利用者が非公開repositoryを必要とし、資料をそのまま公開すると内部開発情報まで到達可能になる。

ReproDroidの中心はAndroid製品repositoryであり、公開文書は製品sourceと同じversion、review、release境界で管理する方が分かりやすい。Runnerは技術スタック、実行環境、release lifecycleが異なるため、独立repositoryを維持する。

## Decision

1. `reprodroid`を本命の公開repositoryとし、Android製品codeに加えて、公開README、architecture、ADR、cross-repository API／contract、公開status、公開release metadataを所有させる。
2. `reprodroid-runner`は独立repositoryのまま維持し、Runner code、Runner固有のsetup／operation、Runner配布物を所有させる。横断仕様は`reprodroid`の公開文書を参照する。
3. `reprodroid-project`は非公開開発repositoryとし、内部計画、生の検証report、evidence、handoff、prompt、ローカルworkspace compatibilityを所有させる。
4. 公開文書は非公開`reprodroid-project`へのlinkや、その存在を必要とする手順を持たない。非公開資料から公開source／文書を参照することは許可する。
5. 公開検証結果は、秘密、host識別情報、Job ID、生logを除いた`PASS`／`PARTIAL`／`NOT_RUN`要約として`reprodroid`に置く。詳細な証拠は非公開領域に保持する。
6. private key、keystore、credential、runtime database、APK、log、source checkout、検証archiveは、公開／非公開を問わずGit repositoryへ保存しない。
7. 移行はcopy先先行とする。公開版を作成し、内容・link・秘密情報を検証した後でだけ旧正本をhistoricalへ変更する。一括移動や履歴書換えで原証拠を失わない。

## Consequences

- `reprodroid`単独cloneで、製品概要と公開仕様を確認できる。
- Android codeと公開契約の変更を同じreviewで同期できる。
- Runnerは独立してbuild／releaseできるが、cross-repository linkは公開URLまたは公開版に固定した参照へ変更する必要がある。
- ADR-0001〜0026、Runner API v1／v2、現在参照される機能契約は、サニタイズとlink確認を終えて`reprodroid`へ移管した。旧相対リンクのためにProject側へ残す同名ファイルは、公開正本への案内だけとする。
- 公開statusには検証の限界を残すが、生の環境情報や秘密情報は含めない。
- 従来の「`reprodroid-project`が横断設計の正本」というADR-0001の判断は、このADRにより該当部分だけsupersededされる。3つの独立Git repositoryを使用する判断自体は維持する。

## Verification

- `reprodroid`の公開文書内に`reprodroid-project`へのlinkがないこと
- 公開対象にabsolute user path、runtime ID、private key、keystore、credential、DB、APK、生logがないこと
- public Markdown linkが単独cloneで解決すること
- 公開statusが実装、automated verification、product verification、publicationを混同しないこと
- `reprodroid-runner`が非公開repositoryなしでbuildでき、READMEから非公開資料への参照がないこと
