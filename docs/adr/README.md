# Architecture Decision Records

ADRは、ReproDroidの公開アーキテクチャとセキュリティ境界に影響する判断を記録します。ADRの`Accepted`は設計判断の採用を意味し、実装完了、製品受入、配布、公開を意味しません。

## Records

| ADR | Subject |
|---|---|
| [0001](0001-repository-boundaries.md) | 3リポジトリの責務境界。一部をADR-0027で置換 |
| [0002](0002-execution-modes.md) | 模擬ビルドと信頼済み実ビルドの分離 |
| [0003](0003-real-build-security-model.md) | 実ビルドのallowlist、RCE確認、固定recipe |
| [0004](0004-job-persistence.md) | Android／RunnerのJob永続化 |
| [0005](0005-single-worker-simulated-queue.md) | 単一worker queue |
| [0006](0006-direct-wrapper-main-and-per-job-build-home.md) | Wrapper実行とJob単位build home |
| [0007](0007-phase-1d-artifact-transfer-and-installation.md) | artifact transferと標準installer |
| [0008](0008-query-all-packages-for-url-registered-apps.md) | URL登録アプリのpackage visibility |
| [0009](0009-phase-2-reference-apk-and-update-boundary.md) | 参照APKと更新メタデータの境界 |
| [0010](0010-phase-2b-executable-apk-content-comparison.md) | executable APK content comparison |
| [0011](0011-phase-2c-trust-update-and-install-policy.md) | trust、update、install source |
| [0012](0012-phase-2d-repeat-build-and-advanced-comparison.md) | 独立再ビルドと高度比較 |
| [0013](0013-build-environment-manifest-public-api.md) | Build Environment Manifest公開APIと機密境界 |
| [0014](0014-dependency-pinning-recipe-contract.md) | dependency pinning、lockfile integrity、offline境界 |
| [0015](0015-recipe-determinism-options.md) | recipe determinism options |
| [0016](0016-pre-build-static-source-scan.md) | pre-build static source scanとreview gate |
| [0017](0017-docker-build-sandbox-feasibility.md) | opt-in Docker build sandbox |
| [0018](0018-phase-4-operational-foundation.md) | Phase 4 operational foundation |
| [0019](0019-phase-4-history-storage-and-audit.md) | history、storage、manual cleanup、audit export |
| [0020](0020-phase-4-trusted-toolchain-installation.md) | trusted toolchain installation |
| [0021](0021-phase-4-generic-build-sandbox-and-comparison.md) | generic Docker buildとcomparison |
| [0022](0022-ui-r-ui-ux-reorganization.md) | UI-R UI／UX責務境界 |
| [0023](0023-phase-4-scheduled-release-discovery-and-notifications.md) | metadata-only定期確認と通知 |
| [0024](0024-phase-4-secure-runner-connectivity.md) | HTTPS、manual pairing、認証、失効 |
| [0025](0025-phase-4-codeberg-provider-and-apk-selection.md) | Codeberg providerとAPK選択 |
| [0026](0026-phase-4-release-operational-hardening.md) | release／operational hardening |
| [0027](0027-public-documentation-and-private-development-boundary.md) | 公開文書と非公開開発領域の分離 |

各ADR本文に記録された過去時点のstatusは履歴です。現在の実装・検証・公開状態は[Current status](../status/current.md)を参照してください。
