# Reproducibility

## 比較対象

1つのrelease observationに次の3 artifactを関連付けます。

- Official: provider releaseから取得し、APK bytesを検査した公式artifact
- Build A: resolved commitと保存済みconfigurationから作成した1回目のartifact
- Build B:同じrelease/configurationを独立して解決・buildした2回目のartifact

Build A/Bは別Job、別checkout、別build home、別artifactです。

## Raw三軸

```text
Official ── comparison ── Build A
    │                       │
    └──── comparison ── Build B
              Build A ── comparison ── Build B
```

各軸は次のraw resultを持ちます。

| Result | 意味 |
|---|---|
| `MATCH` | 定義されたraw comparison scopeで一致 |
| `DIFFERENT` | 定義されたraw scopeに差異がある |
| `INCOMPARABLE` | artifact、identity、configuration、scope、evidenceが不足または不整合 |

raw scopeにはAPK entry inventory、DEX、native library等、保存されたcomparison protocolが定義するbyte-level resultが含まれます。

## Reproducible

`Reproducible`は次がすべて成立した場合に表示します。

- Official、Build A、Build Bが同じrelease observationに属する
- repository、resolved commit、configuration digestが一致する
- Official vs Aが`MATCH`
- Official vs Bが`MATCH`
- A vs Bが`MATCH`
- package/version等の必須identityが成立する
- 保存されたtrust/install eligibility gateが成立する

一つでも`DIFFERENT`なら`Different`です。比較の前提が成立しない場合は`Incomparable`です。unknown stateを`MATCH`へ変換しません。

## 補助証拠

次は差異の調査に使用します。

- DEX class/method差異
- native library差異
- Android Manifest差異
- resource差異
- dependency multiset
- Java、Gradle、SDK、Build Tools
- dependency pinning、`SOURCE_DATE_EPOCH`、locale、build cache
- source scan finding
- Docker/sandbox identity

補助証拠が説明可能な一致を示しても、raw `DIFFERENT`を`MATCH`へ変更しません。

## 独立して扱う状態

| 状態 | 判断する内容 |
|---|---|
| Build completion | Runnerがconfigured buildを完了したか |
| Source scan | configured static indicatorが見つかったか |
| Raw comparison | artifact bytesが定義scopeで一致したか |
| Signer relation | APK signerがinstalled/expected signerと一致するか |
| Trust | 利用者がどのartifactを信頼対象として選択したか |
| Update relation | versionCodeとinstalled packageの関係 |
| Install eligibility | Android installerへ渡す前提が成立するか |
| Installer result | Android platformがinstall/updateを完了したか |

これらを一つの「安全」判定へ統合しません。

## 現在の制限

- source scanは静的indicatorであり、malware判定ではありません。
- Docker bridgeには固定egress allowlistがありません。
- Job単位のhard disk／inode quotaがありません。
- upstream dependencyやtoolchainの第三者attestationは提供しません。
- split APK/APKS/AABは比較対象外です。
