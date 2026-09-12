# Runner API

Runner APIの公開契約です。実装は独立した`reprodroid-runner`リポジトリが所有し、横断API仕様は本ディレクトリを正本とします。

- [API v1](runner-api.md): 既定のJob、確認、ログ、artifact、Manifest、source scan API
- [API v2](runner-api-v2.md): capability negotiation、storage、toolchain、generic build、comparison、paired運用の基礎契約

API v2は、設定されたtransport modeとadvertiseされたcapabilityを確認した場合だけ使用します。文書に契約が存在することを、実行中Runnerがそのcapabilityを提供する証拠として扱ってはいけません。

現在のschemaとcapability baselineは[Current status](../status/current.md)を参照してください。
