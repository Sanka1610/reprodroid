# Getting started

- Status: Current development guide
- Updated: 2026-09-14

ReproDroid currently has no published Phase 5 APK or production-signed release candidate. Phase 5.7 work has assigned `0.1.0-alpha04`／`versionCode 4` to the candidate source, but the frozen Phase 4 alpha03 artifact remains the latest locally accepted production-signed artifact until the new candidate is rebuilt and accepted. Do not replace an installed production build with an arbitrary debug APK or treat a locally built APK as a published release.

## 1. Requirements

- Android 8.0 or later; the current target and product-check platform is Android 16
- JDK 21 and Android SDK 36 to build the Android application
- Linux or WSL2, JDK 21, Git, and the matching [ReproDroid Runner](https://github.com/Sanka1610/reprodroid-runner)
- Docker Engine only when using generic source builds

The release path uses paired HTTPS. Unauthenticated HTTP is restricted to exact loopback development use and is not a release connection mode.

## 2. Build a development APK

Set the Android SDK through an untracked `local.properties` file or `ANDROID_SDK_ROOT`, then run:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk` with application ID `com.sanka1610.reprodroid.debug`. It is signed with a development key and has separate Android app data. This confirms only the executed developer checks; it is not the production-signed candidate described by the [release signing policy](../../release/README.md).

## 3. Start the Runner

Runner-specific configuration and command details are authoritative in the [Runner README](https://github.com/Sanka1610/reprodroid-runner#build-and-run). For a paired connection:

1. Select a dedicated Runner state directory and the exact HTTPS endpoint the phone will reach.
2. Set `REPRODROID_TRANSPORT_MODE=PAIRED_HTTPS`, the bind host／port, and `REPRODROID_ADVERTISED_ENDPOINT`.
3. Run `./gradlew installDist` in the Runner repository.
4. Run `security-init` once against an uninitialized state directory.
5. Start the installed Runner with the same state and transport configuration.

Do not expose a wildcard bind directly to the public internet. A failed paired configuration does not fall back to HTTP.

## 4. Pair the Android application

Pairing is manual and binds the Android client to one Runner identity and root public-key pin.

1. In a local terminal using the running Runner's exact configuration, run `pairing-open`.
2. Treat the returned invitation as a secret. Enter it only in ReproDroid's Settings -> Runner -> Runner settings and authentication flow.
3. ReproDroid submits a pending request and displays a confirmation fingerprint.
4. Run `pairing-list` on the Runner and compare the request identity and fingerprint with the phone.
5. If they match, run `pairing-approve <requestId>`. Otherwise run `pairing-reject <requestId>`.
6. Wait for ReproDroid to report the authenticated Runner identity and connection state.

An invitation is single-use and expires after five minutes. Pairing does not approve source execution, scan findings, comparison promotion, trust, or installation. Never paste an invitation payload, token, credential, private key, or unredacted pairing output into an Issue, log, screenshot, or chat.

## 5. First launch

On a fresh Room database, ReproDroid creates exactly one active self-registration for the public ReproDroid GitHub repository. Bootstrap is offline: it does not query the network, download an APK, install a toolchain, create a Runner Job, compare artifacts, or install an app. Existing databases are not rewritten, and removing that registration does not recreate it.

Android 13 and later may show the system notification-permission prompt on the first foreground launch. Denial is not retried automatically. You can retry explicitly from Settings -> Notifications. The separate Release-check notifications switch controls only whether release-check notifications are posted.

## 6. Add a public repository

1. Open the Add app root page.
2. Enter a public `https://github.com/...` or `https://codeberg.org/...` repository URL.
3. Review the bounded provider analysis. Analysis does not register, download, build, compare, or install.
4. Select the management mode, installation source, release variant, and preferred ABI as applicable.
5. Review the final repository and policy identity, accept any explicit risk acknowledgement, and confirm registration.

If repository identity is ambiguous, the provider response is incomplete, a limit is reached, or multiple APK candidates cannot be selected uniquely, the flow stops for explicit review. Registration itself starts no download or build.

## 7. Next steps

- [Operations and recovery](operations.md) explains release checks, official APK selection, Build A／B, comparison, installation handoff, cleanup, recovery, and checksum verification.
- [UI architecture and navigation](../architecture/ui.md) describes the screen map and fail-closed route behavior.
- [Current status](../status/current.md) separates implemented source, test evidence, final artifact work, and publication.
