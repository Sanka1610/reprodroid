# Historical feature contracts

Phase 4とUI-Rで採用したscope、安全境界、受入条件を当時の形で保持します。表内の契約はhistorical contractであり、契約の存在や初期受入表は、現在の実装完了、製品受入、配布、公開を意味しません。

現在状態は[Current status](../status/current.md)、現在のcomponent責務は[Architecture overview](../architecture/overview.md)、現在のUI ownerは[UI architecture and navigation](../architecture/ui.md)、API wire contractは[Runner API](../api/README.md)を正本とします。後続phaseで完了した項目があっても、historical contract内の当時の`PARTIAL`／`NOT_RUN`は遡及変更しません。

| Contract | Scope |
|---|---|
| [Phase 4 foundation](phase-4-foundation-contract.md) | identity、authority、capability、durable operation |
| [Registration](phase-4-registration-contract.md) | GitHub登録、source discovery、build設定 |
| [Storage](phase-4-storage-contract.md) | history、budget、hold、manual cleanup、audit export |
| [Toolchain](phase-4-toolchain-contract.md) | catalog、検証、導入、inventory、removal |
| [Generic build](phase-4-generic-build-contract.md) | Docker build、独立A／B、raw comparison、限定retry |
| [UI-R](ui-r-contract.md) | superseded UI構成。navigation、情報設計、group、local deletion、localizationの当時の契約 |
| [Release checks](phase-4-release-check-contract.md) | metadata-only定期確認、cooldown、通知 |
| [Runner connectivity](phase-4-runner-connectivity-contract.md) | paired HTTPS、manual pairing、認証、失効、ownership |
| [Codeberg provider](phase-4-codeberg-provider-contract.md) | provider identity、APK選択、download、source境界 |
| [Release hardening](phase-4-release-hardening-contract.md) | migration、log export、署名・配布準備、release gate |

実装計画、target台帳、生のacceptance evidence、ローカル環境情報は公開契約へ含めません。
