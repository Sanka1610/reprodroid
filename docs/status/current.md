# Current status

- Updated: 2026-09-14
- Packaged application identity in source: `0.1.0-alpha03` / `versionCode 3`
- Integrated development state: Phase 5.6 documentation synchronized locally
- Final Phase 5 release candidate: Not generated
- Publication: Not authorized

## Compatibility baseline

| Area | Current value |
|---|---|
| Android application ID | `com.sanka1610.reprodroid` |
| Debug application ID | `com.sanka1610.reprodroid.debug` |
| Android database | Room24 |
| Runner version | `0.1.0-alpha02` |
| Runner database | SQLite12 |
| Stable API | v1 |
| Development／paired API | v2 |
| Required v2 capabilities | `foundation@1`, `storage-retention@1`, `toolchain-install@1`, `generic-build@1`, `apk-comparison@1`, `runner-authentication@1`, `codeberg-source@1` |

Phase 5でAndroid UIとRoom schemaが変更されたため、Phase 4の`0.1.0-alpha03` artifactを現在のsourceから生成した候補として扱うことはできません。次のAndroid releaseは少なくとも新しいversion name／version codeを割り当て、最終統合sourceからartifact、SBOM、checksum、provenance、署名結果を再生成する必要があります。

## Implemented source baseline

Phase 4のprovider、download、build／comparison、安全境界に加え、現在のlocal development sourceには次が統合されています。

- public GitHub／Codeberg providerと、release／APK候補の明示選択
- independent Build A／BとOfficial-vs-A、Official-vs-B、A-vs-Bのraw三軸比較
- history、storage budget、manual cleanup、audit／bounded log export
- managed toolchain installationとDocker限定generic build
- metadata-only release checks、通知permissionと通知policyの分離、1〜24時間intervalまたは指定時刻
- manual pairing、authenticated HTTPS、端末別credential、失効、ownership adoption
- Apps／Add app／Settingsのroot pager、compact app list、group accordionとdrag ordering
- feature別screen package、typed route／back policy、activity-result coordinator
- `ManagedAppsViewModel` facadeの8 delegate／immutable feature state／owner付きresult event
- fresh databaseでのoffline self-registration、Room14からRoom24へのmigration経路
- English／Japanese resources、pure black、font scale 2.0を含むaccessibility polish

## Verification ledger

| Boundary | Current result | Limit |
|---|---|---|
| Phase 5.5+ final unit／build／lint | `PASS` | local integrated sourceで完了。lintは0 errors、33 warnings |
| Phase 5.5+ targeted Android instrumentation | `PASS` | root pageとcontent同期の2 tests |
| Phase 5.5+ Android 16 product UI audit | `PASS` | 日英UI、self-registration、root pager、Settings、slider、app detail等の対象経路 |
| Phase 5.5+ full connected suite | `NOT_RUN` | 直前のfull runは単独再実行で通ったisolated failureを含み、最終follow-upではfull rerunしていない |
| Phase 5.6 documentation hygiene | `PASS` | offline local link、UTF-8、fence、private-reference、host-path、secret-pattern checks |
| Final Phase 5 regression／product journeys | `NOT_RUN` | version更新後の最終統合sourceとproduction-signed candidateでPhase 5.7に再実行が必要 |
| Final artifact／SBOM／checksum／provenance／signer binding | `NOT_RUN` | Phase 4 artifactやPhase 5 debug handoffを流用しない |
| `main` integration／push／tag／GitHub Release／asset upload | `NOT_RUN` | 個別の明示承認が必要 |

この要約は生のlog、端末識別子、host path、Job ID、private evidenceを公開しません。過去の契約や検証記録にある当時の`PARTIAL`／`NOT_RUN`は、後続の結果で遡及変更しません。

## Known limitations

- 公開済みのPhase 5 APKまたはproduction-signed release candidateはありません。source checkoutからのdebug buildはproduction releaseや通常upgradeの代替ではありません。
- public providerはGitHubとCodebergに限定され、private repository token、GitLab、任意Forgejo／Giteaには対応しません。
- split APK、APKS、XAPK、APKM、AAB、silent／privileged installには対応しません。
- generic buildは任意コード実行を伴います。Docker profileにも固定egress allowlistとhard disk／inode quotaはなく、安全なsourceの証明にはなりません。
- backup／restore、端末間移行、旧debug data移行は提供しません。audit exportとlog exportはbackupではありません。
- scheduled release checkはmetadataと通知だけであり、APK download、toolchain導入、build、comparison、trust変更、installを自動開始しません。
- build、scan、comparison、trust、signer、update relation、installabilityは独立した状態であり、一つの「安全」判定には統合しません。

利用開始は[Getting started](../guides/getting-started.md)、通常操作と復旧は[Operations and recovery](../guides/operations.md)、次releaseの変更は[Unreleased notes](../releases/unreleased.md)を参照してください。
