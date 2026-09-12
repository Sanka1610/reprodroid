# ADR-0021: Phase 4.4 generic build sandbox and comparison

> 公開ADRの正本です。実装状況は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted and locally implemented; quota／egress enforcement remains out of current scope. Two-project product Build A／B／comparison E2E is explicitly `NOT_RUN`
- Date: 2026-09-05
- Scope: 4.4a sandbox, 4.4b dynamic Gradle discovery, 4.4c comparison and limited resource retry
- Contract: [Phase 4.4 generic build contract](../design/phase-4-generic-build-contract.md)
- Evidence and product targets: 非公開の実装記録・target台帳で管理
- Public verification status: [Current status](../status/current.md)

## Decision

Phase 4.4はDocker必須のgeneric build／comparisonとして実装する。2026-09-05のスコープ確定により、Job disk／inode hard quotaと固定egress allowlistは現行4.4の実装・受入対象から除外する。これらの除外は、未実装・未検証の制御を成立済みとみなすものではなく、generic capabilityのnetwork隔離・quota保証を広告しないことを意味する。

採用する境界は次のとおりである。

- Runnerの管理plane、DB、source identity、RCE acknowledgement、clone、static scan、scan review、artifact検査、comparison、trust、install policyはhost側に残す。
- catalog digestで検証済みのGradle distribution内`org.gradle.launcher.GradleMain`と子processだけをJob専用Docker containerで実行する。repositoryのWrapperは実行しない。HOST fallback、Docker socket、DinD、任意image／mount／optionは持たない。
- A／Bはcontainer、source、HOME、Gradle cache、discovery、outputを分離し、直列実行する。
- Dockerのresource flagを指定しただけで受入しない。container内cgroupと停止後auditを照合する。
- Job disk／inode hard quotaと固定egress allowlistは現行4.4のスコープ外とする。tmpfs、bind mount、host空き容量、bridge、DNS、proxy環境変数をhard quotaまたはnetwork隔離の証明に読み替えない。
- host network、port publish、Docker socket、DinD、任意image／mount／optionは禁止する。外部egressのallowlist enforcementとhost／LAN denyは広告しない。
- Official-vs-A、Official-vs-B、A-vs-Bのraw三軸を独立保存し、3軸のraw `MATCH`だけをReproducibleの必要条件とする。
- 自動retryは既定OFF。証拠付きcontainer memory OOMだけ、一回限りの新A／Bへ限定する。

## Evidence-driven environment decision

2026-09-05の環境probeではDocker client/server 29.6.2、Linux amd64、cgroup v2、memory／swap／CPU／PID制限、read-only root、capability削除、no-new-privileges、tmpfsを実効確認できた。一方、bridgeは`172.17.0.0/16`、`internal=false`で、GitHubと無関係なexample.comのDNSも解決した。overlayfs上の`--storage-opt size=16m`は受理されたが、20 MiBのroot writeが成功した。これは現行スコープから除外するquota／egress制御が未成立であることの根拠として記録する。

この結果はDockerの本採用全体を否定しない。既存の固定MicroG用3E profileを変更せず、4.4 generic executionでは現行範囲のcontainer境界だけを扱う。quota／egress enforcementを実装で補完したり、実効済みと表現したりしない。

## Alternatives rejected

| alternative | reason |
|---|---|
| current bridgeをhost／LAN隔離として採用 | `internal=false`と自由なDNS解決を確認しており、要求するdeny境界を証明しない |
| `--storage-opt size`をhard quotaとみなす | 16 MiB指定後も20 MiB書込みが成功し、実効性がない |
| tmpfs 1 GiBをJob disk quotaの代用にする | root writable layer、bind mount、inode、host storageを制限しない |
| quotaなしでresource保証済みとしてgeneric buildを通す | Job単位のdisk／inode上限を提供せず、resource保証を主張できない |
| Docker障害時にHOSTへfallback | 利用者が選んだ実行境界を黙って変更する |
| Aのdiscovery／cacheをBへコピー | 再現性とdependency evidenceの独立性を失う |
| sandbox evidenceでraw Differentを昇格 | sandbox条件とAPK内容のtruthを混同する |

## Consequences

4.4a／b／c、`generic-build@1`、`apk-comparison@1`、Android Room18、Runner SQLite11、private Manifest v5／public Manifest v4をlocal implementationへ反映した。safe relative symlinkとcatalog executable modeを含む4.3 empty-store recoveryも確認した。generic buildはloopback development opt-inで広告・受付するが、Android + Dockerの二project Build A／B・比較E2Eは利用者指示で省略しており、公開sourceでのraw outcomeやReproducibleの根拠は存在しない。quota／egress enforcementは現行スコープ外であり、除外した安全保証を別の制御で代用したとは扱わない。
