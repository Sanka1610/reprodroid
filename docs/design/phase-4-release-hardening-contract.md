# Phase 4.8: release and operational hardening contract

> 公開仕様の正本です。実装状況と検証状態は[`docs/status/current.md`](../status/current.md)で別に管理します。

- Status: Accepted contract; implementation and acceptance tracked separately
- Date: 2026-09-09
- Decision: [ADR-0026](../adr/0026-phase-4-release-operational-hardening.md)
- Baseline: Android Room22／Runner SQLite12、development API v2。製品version、DB、API、capability、export schemaは独立管理する。

## Scope and authority

Phase 4.8はlog export、全migration chainと失敗復旧、debug ID分離、CI／手動署名／配布準備、license／privacy、Phase 4全体の残受入を扱う。`.rdbak`、暗号化論理backup／restore、別端末restore、旧debugデータ移行は対象外へ変更した。監査exportは継続し、復元機能とは表示しない。公開・push・main統合、実データのuninstall／削除、共有JDK／SDK／network設定変更はこの契約から許可を導かない。

## Log export

Androidはアプリ自身の動作・errorのみをUTF-8 textへ手動SAF保存する。OS全体logcat、他アプリlog、診断archive、自動upload／Issue送信を追加しない。Runnerは自身の動作・errorと利用者が選択したJobの保存済みbuild logを出力する。paired modeの通常API認証・Job owner認可を維持し、log取得を理由とする匿名endpointや任意filesystem path受付を追加しない。既存bounded log APIを再利用できる範囲を優先する。

logは保存済み範囲だけを示し、生成時刻、対象、取得開始／終了、欠損／打切り、取得不能の理由を本文へ記載する。時刻やoffsetを取得できない場合は不明と示す。core loggerへtoken／key／passphrase／pairing code／credential envelope／秘密のHTTP headerを書かない。任意build出力はsecretやpathを含み得るため、保存前に内容確認と共有時の注意を示し、匿名化保証をしない。

安全な初期推奨はAndroid運用log保存4 MiB、単一export32 MiB、stream処理、一時領域の容量確認、上限到達時の明示的打切りである。既存Runner log retentionやAPIの専用上限は縮小・無制限化せず維持する。実装時に採用値と既存専用上限の対応をreportへ固定する。全体を無制限にmemoryへ載せない。失敗／cancelを成功と表示せず、既存正常fileを無断上書きしない。SAF providerがatomic publishを保証しない場合は未完了fileが残り得ることと実際の削除結果を示す。

## Upgrade and data compatibility

Phase 3のRoom14／SQLite8から採用最終schemaまで、全migration chainと途中versionからの経路を検証する。登録、current release／comparison、履歴、trust、設定を保持し、未知値を安全な既知値へ読み替えない。destructive fallback、未知schemaへの書込み、APK／DB強制downgradeは禁止する。

migration前にprivate領域で整合snapshotを予算内に確保する。稼働中SQLite fileの単純copyではなく、接続を閉じてWALを含む整合性を保証する手順またはSQLite backup機構を使う。容量不足／snapshot失敗／migration失敗では通常起動を止め、元データとsnapshotを保持して復旧案内を表示する。新規書込み後の任意rollbackは禁止。Runner owner／資源／失効情報を照合し、snapshotからcredentialや失効済み権限を復活させない。各失敗cut pointで元DBが読めること、未承認Job／通知／installが開始されないことを検証する。

release applicationIdは`com.sanka1610.reprodroid`、debugは`com.sanka1610.reprodroid.debug`。旧debugがreleaseと同じIDで別署名なら上書き不可。旧debug uninstallで登録・設定・履歴・pairingが失われ、releaseは新規登録・再pairingが必要になる。これはREADME、設定の移行案内、配布手順へ明記する。自動uninstall、移行専用旧debug artifact、exportのimportは提供しない。同一ID／同一署名の通常release upgradeは別経路としてデータ保持を検証する。

## CI, signing and distribution

初期配布先はGitHub Releases向け準備のみ。alphaも署名release APK、RunnerはWSL／Linux用実行物＋library archiveとし、DB／鍵／履歴／SDK／Docker／JDKを含めない。Runner前提環境と手動更新を文書化し、active Job中の自動差替え／再起動を行わない。

CIはtoolchain版とActions full commit SHAを固定し、PRの権限はread-only、secret・公開権限なしでunsigned成果物を生成する。source full SHA、dependency解決結果、toolchain、CI実行識別子、unsigned digestを記録する。次にRCE Runnerと別の署名環境で運用者がdigest／由来を確認し、手動署名する。検証済みunsigned artifactだけを署名対象にする。

長期署名鍵の新規生成は運用者が専用環境で行う。推奨はAndroid signing toolと互換なRSA 4096-bit鍵、十分な有効期間、強い別管理passphrase、owner-onlyのkeystore権限と暗号化した別媒体backupである。実際のalgorithm／validity／fingerprint／custodian／復旧手順は秘密を除いて運用記録へ固定する。鍵やpassphraseをコマンド引数、shell history、CI、repository、RCE Runner、build container、通常artifact、logへ入れない。TLS鍵と兼用しない。task-local受入鍵とproduction鍵を区別し、前者でproduction署名を受入済みとしない。

署名後にAPK署名検証、package／version／signer fingerprint、HTTPS・cleartext・permission、起動と通常gateでのself-updateを確認する。alpha／stableは同一ID・同一鍵、versionCodeは全channelで単調増加、同version asset差替えなし。復旧は大きいversionCodeの修正版を基本とする。

最終APK／Runner archiveのSHA-256、signer fingerprint、CycloneDX JSON SBOM、source full SHA／toolchain／CI／署名前後digestの対応と署名工程の来歴、検証済み互換表を生成する。SBOMはcoverageと未収録範囲を明記し、安全証明／license履行の代替にしない。unsigned CI attestationを署名後APKのattestationと表現しない。公開checklistには全受入、license、鍵分離、秘密非混入、互換表、最終digest照合を含める。生成完了は公開許可ではない。

## License and privacy

自ら権利を持つ3repoのcode／設定／script／文書／resourceをApache-2.0とし、各repoに本文と適用範囲を配置する。第三者のcode／assetは再licenseせず、実採用versionの直接／間接依存・同梱binary・画像のcopyright／NOTICE／license／source提供条件を確認する。現行APKのAL2.0／LGPL2.1除外、Logback 1.5.18の当該版条件を監査する。必要文書をAPK／Runnerへ同梱し、Android offline license画面から読めることを確認する。未確認の権利は公開releaseを止める。

merged Manifestとruntime依存を監査し、permissionの用途とproviderへ見えるIP／時刻／要求対象を説明する。Google Play services、analytics、広告、tracking、自動crash送信を追加しない。AndroidX／Google Maven利用自体は禁止しない。SAFを維持し広範storage権限、QR／camera、root／Shizuku／Device Owner、exact alarm等を追加しない。Play Store／F-Droid配布・適合性評価は対象外。

## Acceptance ledger

各項目は実装reportで`PASS`／`PARTIAL`／`NOT_RUN`、実行日、exact commit／artifact digest、環境、証拠path、失敗理由を記録する。契約作成時点では未実行であり、この表は成功報告ではない。

| ID | 必須受入とnegative境界 |
|---|---|
| RC48-01 | Android自身logの実SAF export、UTF-8、保存範囲、empty／missing／rotation／上限、容量不足／cancel／provider失敗、core秘密非混入 |
| RC48-02 | Runner自身／選択Job log、paired認証、別owner拒否、欠損／打切り、停止／再起動、任意path拒否、共有前注意 |
| RC48-03 | Room14→最終schemaおよび各中間version、登録／current／history／trust／settings保持 |
| RC48-04 | SQLite8→最終schema、owner／resource／revocation／active Job照合、二重実行なし |
| RC48-05 | snapshot整合、容量不足、未知schema、migration失敗、process death各cut point、元データ保持とfail-closed再起動 |
| RC48-06 | debug suffix、旧同一ID debug別署名の更新拒否、uninstall時data loss案内、通常同一署名upgrade保持、自動uninstallなし |
| RC48-07 | 固定CIとunsigned artifact、PR最小権限、secret非混入、隔離手動署名、production／受入鍵の区別 |
| RC48-08 | 最終APK署名／fingerprint／hash、HTTPS、日常操作、self-updateのidentity／signer／install gate、署名不一致拒否 |
| RC48-09 | Runner archive内容、clean環境の前提手順／起動、手動更新、active Job保持、互換性不明時新Job停止 |
| RC48-10 | 3repo LICENSE／第三者監査／NOTICE・必要条件／APK・Runner同梱／offline表示 |
| RC48-11 | merged permission inventory、runtime依存、Play services非依存、非収集、telemetry／自動送信なし |
| RC48-12 | CycloneDX SBOM coverage、署名前後digest・source／CI／toolchain来歴、互換表、公開前checklist |
| RC48-13 | retained gap台帳とroadmap最終受入全項目の証拠照合、文書同期、3repo clean、commitとintegration／公開境界 |

## Phase 4 retained gaps

4.8完了は以下を含むPhase 4全体のgap closeoutを要求する。古いreportの観測を改変せず、新しい受入recordから旧IDへ参照を付ける。実施不能をscope除外へ読み替えない。

| 元工程 | 引き継ぐ証拠／実施範囲 |
|---|---|
| 4.1 | 実public GitHub登録・取得・cold start経路のPARTIAL。未認証rate limitは原因別記録し、tokenによる迂回を受入代替にしない |
| 4.2 A4.2-03 | 実Room15 archive fixtureの不足を解消し、generated fixtureと実archiveを区別 |
| 4.2 A4.2-23 | 20,000 records／32 MiB実bundle負荷と上限・超過時挙動 |
| 4.2 A4.2-27 | 明示承認済みdisposable登録データで選択cleanup／History画面／参照整合と副作用検証 |
| 4.4 | MicroG以外のGitHub Android project少なくとも2件のAndroid製品経路から独立A／B・raw比較。Codeberg 1件は4.7証拠を照合 |
| UI-R | 端末migration／accessibility／navigation／日英／uninstall・local deletion／Android 16製品証拠の未実施範囲 |
| 4.5 | Android 16で定期確認2回以上、既定6時間・1時間刻み・時刻指定、304とtag／asset、通知dedup・tap・mute・拒否、metering／battery20・21%／充電、reboot・force-stop再開、metadata-only副作用なし |
| 4.6／4.7 | 既存PASSのexact artifact／contractを照合し、4.8変更影響に必要なHTTPS／revocation／provider回帰を実行 |

roadmap最終受入1〜27を最終checklistの正本として、この台帳との対応を記録する。hard disk／inode quota、固定egress allowlist、今回除外したbackupは未達gapへ再追加しない。公開／push／main統合は完了条件としない。
