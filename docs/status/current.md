# Current status

- Updated: 2026-09-13
- Candidate: `0.1.0-alpha03` / `versionCode 3`
- Status: Phase 4 complete; local release candidate accepted
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

Exact release commitとartifact digestは、最終統合sourceから再生成・照合した内部release recordで固定します。作業branchのHEADや、以前生成したartifactをrelease identityとして扱いません。

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

Phase 4.8は、retained Phase 4 gap、独立したGitHub generic Build A／B、raw三軸比較、migration、log export、署名・配布物、license・privacy、互換性のローカル受入まで完了しました。大規模repositoryの未認証source discoveryはprovider quotaへ到達し得るため、上限到達時はfail closedで停止し、provider reset後の明示的な再試行を必要とします。

公開前には、公開対象payloadだけを対象とした別の監査と明示承認が必要です。公開、push、`main`統合、GitHub Release作成はPhase 4完了に含めず、実施していません。

この文書は公開可能な状態要約です。host path、Job ID、ADB serial、秘密情報、生log、検証archiveを含む内部証跡は非公開領域で管理します。
