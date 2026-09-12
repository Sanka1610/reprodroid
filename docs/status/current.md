# Current status

- Updated: 2026-09-12
- Candidate: `0.1.0-alpha03` / `versionCode 3`
- Status: Phase 4.8 implementation and acceptance in progress
- Publication: Not authorized

## Compatibility baseline

| Area | Current value |
|---|---|
| Android application ID | `com.sanka1610.reprodroid` |
| Debug application ID | `com.sanka1610.reprodroid.debug` |
| Android database | Room22 |
| Runner database | SQLite12 |
| Stable API | v1 |
| Development／paired API | v2 |
| Required v2 capabilities | `foundation@1`, `storage-retention@1`, `toolchain-install@1`, `generic-build@1`, `apk-comparison@1`, `runner-authentication@1`, `codeberg-source@1` |

Exact release commitとartifact digestは、最終統合sourceから再生成・照合した後に固定します。作業branchのHEADや、以前生成したartifactをrelease identityとして扱いません。

## Implemented baseline

- public GitHub／Codeberg provider
- release／APK metadata、明示的な複数APK選択、downloadとAPK検査
- independent Build A／Bとraw三軸比較
- Build Environment Manifest、dependency、determinism、source scan、sandboxの補助証拠
- history、storage budget、manual cleanup、audit export
- managed toolchain installation
- scheduled metadata-only release checkと通知
- manual pairing、authenticated HTTPS、credential失効
- Android／Runnerのbounded log export
- Room14からRoom22、Runner SQLite8からSQLite12へのmigration経路
- unsigned CIと、CI／Runnerから分離した手動署名境界

## Acceptance boundary

Phase 4.7までの個別受入記録は存在しますが、Phase 4.8と`0.1.0-alpha03`の最終release gateは完了していません。少なくとも次は未完了として扱います。

- 最終統合commitの固定
- 最終統合sourceからのAndroid／Runner artifact再生成
- unsigned、signed、SBOM、source、toolchain provenanceのdigest対応付け
- 最終secret scanと配布内容監査
- 残るGitHub product comparison targetの完了
- public repository payloadの監査
- push、`main`統合、GitHub Releaseを含む公開承認

この文書は公開可能な状態要約です。host path、Job ID、ADB serial、秘密情報、生log、検証archiveを含む内部証跡は非公開領域で管理します。
