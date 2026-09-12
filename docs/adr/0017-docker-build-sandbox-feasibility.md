# ADR-0017: Docker build sandbox feasibility

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted; opt-in adoption and implementation contract approved; production implementation in progress
- Date: 2026-08-30
- Scope: Phase 3E / Task 20 decision and Task 21 contract
- Evidence and implementation contract: 非公開の実装記録で管理
- Public verification status: [Current status](../status/current.md)

## Context

Phase 3DまでのRunnerは、allowlist、Job単位RCE確認、detached checkout、static scanとdigest-bound review、Wrapper checksum検査を行った後、固定Gradle taskをWSL2 host上で実行する。build script、plugin、compilerの任意コード実行をこれらの検査だけでは隔離できない。

3EはRunner全体のcontainer化ではなく、Gradleとその子processをJob専用containerへ分離する実用性を調べる。利用者は2026-08-30に以下のprobe方針を承認した。方針承認はDockerの本採用、production code実装、APIやmigrationの完了を意味しない。

## Approved probe policy

### Ownership and execution boundary

- Runner本体、API、DB、allowlist、ref解決、RCE確認、clone、detached checkout、static scan／review、Wrapper検証、成果物検査・配信と監査保存はWSL2 host側へ残す。
- containerは検証済み`org.gradle.wrapper.GradleWrapperMain`とGradle／plugin／compiler等の子processだけを実行する。repository内の`gradlew`／`gradlew.bat`は実行しない。
- Androidの公式APK取得、comparison、trust、update、signer、install、Windows emulator／ADBは移さない。
- build container内でDocker daemonを起動せず、Docker socketもmountしない。Runner自体のcontainer化も今回は対象外とする。Runnerをcontainer化すると必ずDinDになる、という理由での選択ではない。
- Docker engine操作権限はhostのRunnerに残る。clone等もhost実行なのでJob全体の隔離とは呼ばず、未信頼の任意repositoryを安全にbuildできるとは主張しない。

### Mode and fail-closed behavior

- 採用時もopt-in、既定OFF。初期版の選択はRunner起動設定とし、各Jobに実効modeを固定・保存する。
- Dockerを選択したJobはengine／image／mount／container起動に失敗してもhost実行へ自動fallbackしない。
- Android requestに任意image、mount、Docker引数を追加しない。`SIMULATED`からDockerへ到達させない。
- Build A／Bは独立Job、container、source、HOME、Gradle user homeとする。RCE同意／scan reviewは同一commit・同一digestでも共有しない。

### Image, mounts, and runtime controls

- Linux imageをdigestで固定し、実際のimage identity、architecture、engine versionを記録する。未解決tagだけで採用しない。
- non-root、read-only root filesystem、capability制限、default seccomp、no-new-privileges、CPU／memory／PID／temporary storage上限を候補として実測する。値は実測記録に残し、指定しただけで有効と断定しない。
- 書込可能領域をJob専用source、HOME、Gradle user home、出力と必要な一時領域に限定する。SDKはread-onlyを第一候補とする。JDK／SDKをimageへ含めるかread-only mountにするかはprobeで決める。
- 環境変数`HOME`だけでなくJavaの`user.home`もJob専用mountを指すことを検証する。UIDに対応するimage内homeと一致させ、AGP等の`.android`書込のためにroot filesystem全体をrwへ緩めない。
- user HOME、Runner DB／state全体、他Job、Docker socket、device、credentialは渡さない。Job専用mountもconfinement、symlink、ownershipを検証する。
- 共有Gradle cache、host cache copy、cache seedを導入しない。base image／read-only toolchainの共有と、Jobの可変cache共有は区別する。
- MicroG-RE `6.1.4`のfixed release profile、JDK 18、Gradle distribution 8.14.3、Wrapper JAR origin 8.11.1、SDK API 36、Build Tools 36.0.0を維持する。pinning `NONE`とdeterminism未設定を勝手に変更しない。
- hostで取得したJava／OS情報をcontainerの観測値として記録しない。container内toolchainをhost側の検証値と照合し、実際の実行条件・artifact・dependency evidenceを結び付ける。
- container停止後のhost側importも境界の一部とする。可変cache／artifactのsymlink、祖先directory差替え、non-regular file、resource上限を検査してから読込む。現行host executorのdependency captureを無検査でcontainer outputへ流用しない。

### Network

- build中の完全通信遮断は必須条件ではなく、依存取得の通信を許可する候補も評価する。既存の空のJob cache境界を維持する。
- 通常のbridgeを「依存取得先だけに通信可能」「host／LANに到達不能」とは扱わない。network mode、DNS／外向き通信、host到達性を記録する。
- 接続確認は既知の依存配布先と、このprobeが所有する一時listenerに限定する。LANを走査したり、無関係なserviceへ接続したりしない。
- host network、port公開、privileged実行を既定候補にしない。network制限とdependency pinningを同一視しない。
- 完全遮断に別のdependency供給方式が必要なら、その追加設計を採用条件と混同せず代替案として記録する。

### Cleanup and performance

- 一時directory、container、network、volumeはprobe固有の識別子で追跡する。既存Docker資源を変更せず、global pruneを実行しない。
- 正常終了、cancel、timeout、controller強制停止と再起動後のorphan回収を検証する。Docker CLIのprocess終了だけでcontainer停止を断定しない。
- 回収前にcontainerを停止し、mutable outputを安全なhost-side検査へ渡す。検査不能なartifactを成功扱いにしない。
- 同一commit／recipe／toolchainと空のJob cacheでhostとcontainerを比較する。初回image取得はbuild時間から分離し、時間・memory・disk・CPU制限の差を記録する。異なるresource条件の差を純粋なcontainer overheadと呼ばない。
- 利用者指定の性能閾値はない。実測と残存リスクを提示し、採用決定時に許容性を判断する。

## Probe and production evidence are separate

使い捨てharnessは既存Runnerのgateやexecutor接点を再利用してよいが、production APIへのDocker対応、Androidのsandbox evidence表示、migration完了の証拠にはしない。production sourceを変更せず一時領域で実行する。harness固有の観測値を既存Job／Manifestの正式なsandbox fieldとして偽装しない。

Task 20でのMicroG-RE独立buildとhost比較はfeasibility evidenceであり、Task 22の製品経路E2Eとは分ける。既存APK comparatorを変更せず、raw `Different`／`Incomparable`を補助証跡で`Reproducible`へ昇格させない。

## Decision (2026-08-30)

既定OFFのopt-inとして採用する。container A2/Bとhostのbuild・raw比較・監査復元が成功し、測定値と残存制約を提示した後、利用者が契約策定を承認した。Task 20の採否はAccepted。この2026-08-30の採否判断時点ではTask 21契約確定であり、production実装やTask 22 E2Eは未完了だった。後続の実装結果は上記final acceptanceを参照する。

3 buildのAPK全体、raw実行entry 6件、dependency evidence 1,031件は一致した。host性能baselineは具体的host RCEリスクへの追加承認と再審査通過後に実行した。hostは296.394秒、container A2/Bは1,293.671／759.612秒。通信・resource条件差を含むため、この比を純粋なDocker overheadとしない。host全体memory比較とimage pull時間の精密測定は未実施。

- 初期profileは`docker-microg-v1`。pinned Ubuntu linux/amd64、8 CPU／8 GiB memory+swap／PID 1024／tmpfs 1 GiB、non-root／read-only root／SDK・JDK read-only、Job別cache、bridgeとする。imageはoperatorが事前取得し、Jobではpullしない。詳細値と採用時の保護は実装契約に固定する。
- bridgeのhost/LAN隔離は未証明。Job bind mountのhard disk quotaは初期版に含めず、`jobDiskQuotaEnforced=false`と残存DoSリスクを明示する。tmpfs／終了後import上限を実行中のdisk quotaと偽らない。
- 新設定`REPRODROID_BUILD_SANDBOX`はHOST既定、厳密なHOST/DOCKERのみ。Job作成時に固定し、旧HOST／新規Jobのorigin、cleanup状態、所有resource intentを永続化する。
- missing mountのhost事前拒否、Java home整合、停止後のbounded safe import、実container観測と監査の結合、cleanup未確認時の実ビルド抑止をTask 21必須条件とする。probeの一時guardをproduction完成扱いしない。
- 予定はRunner SQLite v8、Android Room v14、private Manifest v4／public v3。旧証跡と新規Jobのdowngradeを区別し、実装前にschemaだけを追加しない。
- Task 21／22はCancelledにしない。Phase 4 entryには本実装と製品経路E2Eの完了が必要。今後実装上の新たな制約で採用を覆す場合は別の明示判断を残す。

## Alternatives not selected for this phase

- Runner全体のcontainer化／Docker socket共有: 管理planeの配布・mount・権限設計が追加される。今回の目的はbuild処理の限定的な隔離。
- Docker in Docker: build containerがengineを必要とする理由がなく、daemonと権限の境界が増える。
- 共有cacheによる高速化: Job間汚染と依存監査を曖昧にするため採用しない。
- Docker障害時のhost fallback: 選択した実行境界を利用者に知らせず弱めるため採用しない。
- sandboxを根拠とするtrust昇格: protocol v2のraw比較とは別の証跡なので採用しない。

## References

- [Architecture overview](../architecture/overview.md)
- [Wrapper and per-job home boundary](0006-direct-wrapper-main-and-per-job-build-home.md)
- [Docker run](https://docs.docker.com/engine/containers/run/)
- [Docker bind mounts](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker bridge networking](https://docs.docker.com/engine/network/drivers/bridge/)
- [Docker group privileges and session refresh](https://docs.docker.com/engine/install/linux-postinstall/)
