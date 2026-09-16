# ReproDroid documentation

ReproDroid `0.1.0-alpha05`の導入、操作、比較、安全境界、互換性を説明します。

## 利用者向け

- [Getting started](getting-started.md): Release APK/ZIPの取得から最初のrepository登録まで
- [User guide](user-guide.md): 登録、release追跡、Jobs、比較、install、削除、export
- [Reproducibility](reproducibility.md): Official／Build A／Build Bと判定条件
- [Security](security.md): Android権限、署名、Runner認証、source build、現在の制限
- [Compatibility](compatibility.md): Android、Runner、API、provider、APK形式
- [Troubleshooting](troubleshooting.md): 署名、接続、build、toolchain、install

## 開発・release

- [Development](development.md): debug APK、test、development Runner接続
- [Releasing](releasing.md): unsigned artifact、署名、SBOM、checksum、release manifest
- [Architecture overview](architecture/overview.md): Android、Runner、provider、dataの責務
- [UI architecture](architecture/ui.md): screen、route、state/event ownership
- [Changelog](../CHANGELOG.md): 利用者に影響するversion別変更

Runner固有のinstallation、configuration、CLI、API、Docker、state recoveryは[Runner documentation](https://github.com/Sanka1610/reprodroid-runner/tree/main/docs)を参照してください。
