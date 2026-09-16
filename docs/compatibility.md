# Compatibility

## Release組合せ

| Android | Runner | Room | SQLite | Stable API | Capability API |
|---|---|---|---|---|---|
| `develop`（未公開） | `0.1.0-alpha02` | 25 | 12 | v1 | v2 |
| `0.1.0-alpha05` | `0.1.0-alpha02` | 24 | 12 | v1 | v2 |

Android `0.1.0-alpha05`のapplication IDは`com.sanka1610.reprodroid`、versionCodeは5です。debug buildは`com.sanka1610.reprodroid.debug`です。

## Android環境

| 項目 | 値 |
|---|---|
| minSdk | 26 / Android 8.0 |
| targetSdk | 36 |
| compileSdk | 36 |
| build JDK | 21 |
| database | Room25（`develop`） |
| package | single APK |

## Runner環境

| 項目 | 値 |
|---|---|
| OS | Linux、WSL2 |
| service JDK | 21 |
| fixed comparison JDK | 18（対象recipeのみ） |
| database | SQLite12 |
| default HTTP port | 8080 |
| default HTTPS port | 8443 |
| generic build | Docker必須 |

## APIとcapability

- API v1: health、legacy/fixed recipe Job、log、artifact、manifest、source scan
- API v2: capability、storage、toolchain、generic build、comparison、self-revoke
- pairing v1: manual invitation、request、PC approval

対応capability:

```text
foundation@1
storage-retention@1
toolchain-install@1
generic-build@1
apk-comparison@1
runner-authentication@1
codeberg-source@1
```

Androidは実行中Runnerのcapability応答、runner ID、transport modeを確認します。READMEのversion表だけで機能を有効にしません。

## Provider

| Provider | Repository | Release metadata | APK asset | Source build |
|---|---|---|---|---|
| GitHub.com public repository | 対応 | 対応 | 対応 | 対応 |
| Codeberg.org public repository | 対応 | 対応 | 対応 | 対応 |
| private repository | 非対応 | 非対応 | 非対応 | 非対応 |
| GitLab | 非対応 | 非対応 | 非対応 | 非対応 |
| 任意Forgejo/Gitea | 非対応 | 非対応 | 非対応 | 非対応 |

## APK形式

単一の`.apk`を扱います。split APK、APKS、XAPK、APKM、AABは対象外です。複数のAPK assetがあるreleaseでは、利用者がexact assetを選択します。

## Storage migration

Androidの`develop`は既存databaseをRoom25までmigrationします。公開済み`0.1.0-alpha05`はRoom24です。fresh databaseではReproDroid自身のpublic GitHub repositoryをofflineで1件登録します。RunnerはSQLite12を使用します。backup/restoreと端末間migrationは提供していません。
