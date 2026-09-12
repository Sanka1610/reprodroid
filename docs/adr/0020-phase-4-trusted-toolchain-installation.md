# ADR-0020: Phase 4 trusted toolchain installation

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted
- Date: 2026-09-05
- Scope: Phase 4.3
- Verification status: [Current status](../status/current.md)

## Context

Phase 4.1はrepositoryと構造化build設定を保存できるが、固定recipe外のprojectが要求するJDK、Gradle、Android SDKを利用者の共有環境へ事前導入する前提ではdaily-use alphaにならない。一方、repositoryが指定したURL、Wrapper、`sdkmanager`、install scriptをRunner hostで実行すると、4.4のDocker／RCE境界より前に任意コード実行と供給網変更を許してしまう。

既存の`setup-env.sh`は開発環境bootstrapであり、共有JDK／SDK、emulator、system image、platform-toolsと一括license処理を扱う。製品installerへ流用しない。Runner自身を起動するJDK、WSL、Dockerも製品toolchain storeの管理外とする。

## Decision

Runnerにversioned bundled catalogと専用storeを置き、Androidは要求、side-effect-free plan、license本文とdigest、進捗、cancel、inventory、manual removalを表示する。installed truth、filesystem path、license authorityはRunnerだけが所有する。Android Room17は`runnerId`、installation／operation ID、plan／catalog digest、最終観測stateだけを保持する。

catalogは初期版ではruntime更新しない。Linux x86_64上のTemurin JDK、Gradle binary distribution、Android command-line tools、platform、build-toolsをseedとし、NDK／CMakeは要求projectが確定したときだけ同じ契約へ追加する。未知OS／architecture、未知version、任意URLは拒否する。

導入順序を次に固定する。

1. exact requirementをbundled catalogで解決し、catalog／plan SHA-256を返す。
2. `(runnerId, principalId, licenseId, licenseTextSha256)`へ明示同意をbindする。本文変更、新Runner、新principalでは再同意する。
3. 32 GiBのRunner toolchain budgetとfilesystem usable bytesを別々に検査し、download＋展開上限を予約する。
4. operation owner固有stagingへHTTPSで取得する。redirect後も許可hostを再検査し、catalog sizeを超えた時点で停止する。
5. 全archiveのSHA-256を必須とし、Temurinはbundled fingerprint `3B04D753C9050D9A5D343F39843C48A565F8F04B`によるdetached OpenPGP signatureも必須とする。
6. absolute path、control character、`..` escape、device、unsafe link、symlink parent traversal、展開bytes／entry count上限超過を拒否する。取得物やrepository scriptをhostで実行せず、metadataとcontent manifestを静的検査する。
7. fileとdirectoryをfsyncし、同一filesystemのatomic renameだけでfinal storeへpublishする。検証後はwrite bitを除く。
8. publish後にRunner inventoryを`VERIFIED`へする。A／Bは4.4でread-only mountし、writable cacheは共有しない。

cancelはI/O停止、owner staging削除、reservation解放、durable state保存後にだけ`CANCELLED`となる。atomic publish済みならinstallを勝たせ、削除は別のmanual removalとする。restartは同じinstallation IDでowner stagingを捨てて取得から再開する。final／DB／stagingが曖昧なら再配布せず`RECONCILIATION_REQUIRED`とする。

API v2 capabilityは`toolchain-install@1`、RunnerはSQLite10、AndroidはRoom17とする。既存`storage-retention@1`のJOB／ARTIFACT契約を拡張解釈しない。

## Security boundary

catalogとinstallerは信頼済みcontrol planeであり、repository codeへURL代理取得API、host process、writeable toolchain mountを渡さない。ただしread-only bitやmountはhost管理者による改ざんへの防御ではない。利用前にcontent manifestを再検査する4.4契約が必要であり、host adversaryは現行threat model外である。

license同意はtoolchain取得だけを許可し、repository codeのRCE確認、source scan review、Job作成、comparison、installを許可しない。scheduled release確認からtoolchain導入を自動開始しない。

## Alternatives rejected

- `sdkmanager`やrepositoryのinstall scriptをhostで実行する案: mutable metadata、任意処理、一括同意がcontrol plane境界を破る。
- system／共有JDK・SDKをinventoryへ取り込む案: 由来、digest、更新権限、削除対象が曖昧になる。
- runtime remote catalog案: catalog署名、rollback、更新authorityを4.3だけで追加する必要がある。
- SHA-256だけでTemurinを扱う案: 安定したpublisher signatureが提供される対象で第二のprovenance checkを捨てる理由がない。
- cancel時にpublish済みdirectoryを自動削除する案: publishとの競合でinstalled truthを失う。
- toolchainを4.2 cleanupへ混在させる案: 既存contractのresource kindと保護条件を遡及変更する。

## Consequences and verification

4.3の完了条件は空の製品storeからAndroidでplan／同意／導入／inventory／restart確認／manual removalまでである。汎用Docker build、Official-vs-A／Official-vs-B／A-vs-B比較、toolchain read-only mount時の再検証は4.4の別証跡であり、4.3で成功へ数えない。

negative testはdigest／signature不一致、unknown artifact、unknown field、license拒否・stale digest、redirect逸脱、size／展開上限、path escape、unsafe link、容量不足、network断、cancel、restart、publish衝突、inventory改ざん、removal preview expiry、共有環境非干渉を含む。
