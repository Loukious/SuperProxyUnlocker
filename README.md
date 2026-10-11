# Super Proxy Unlocker

Headless modern Xposed module for `com.scheler.superproxy`, rebuilt from the legacy unlocker using [FacebookAppAdsRemover](https://github.com/Loukious/FacebookAppAdsRemover) as the Gradle/libxposed template. No launcher entry, activity, settings screen, or module permissions.

[Download the signed APK](https://github.com/Loukious/SuperProxyUnlockerModern/releases/latest).

## Requirements and installation

- Vector / LSPosed implementing modern libxposed API 101 or newer. The compile-only dependency is `io.github.libxposed:api:102.0.0`, matching the template; module metadata targets API 101 and uses its compatible API surface. See the [official libxposed API documentation](https://github.com/libxposed/api/blob/102.0.0/README.md).
- Enable **Super Proxy Unlocker** with scope **Super Proxy** (`com.scheler.superproxy`).
- Disable the old `tn.loukious.superproxyunlocker` module, then force-stop and restart Super Proxy.

The new application ID is `tn.loukious.superproxyunlocker.modern`, allowing installation alongside the original `tn.loukious.superproxyunlocker` module. If you previously installed a debug-signed modern build, uninstall that build before installing the release-signed APK: Android requires matching signing certificates for in-place upgrades.

## Update resilience

The old module referenced unobfuscated RevenueCat class names. Super Proxy 3.2.3 obfuscates these classes, causing every old hook to fail with `ClassNotFoundException` even though the old module logged success.

This rebuild uses **`org.luckypray:dexkit:2.2.0`** to discover Flutter's `StandardMessageCodec.writeValue` in the currently installed base/split APKs. Discovery requires two codec error strings, the `ByteArrayOutputStream, Object -> void` signature, and a non-static method. It accepts exactly one matching method and never stores obfuscated names or uses a version-specific class-name fallback. The current obfuscated name `yq4.k` appears only in runtime logs, not in source matching.

The hook recognizes the RevenueCat customer-info map schema immediately before Flutter serializes it. This covers both method replies and customer-info listener updates, including nested payloads through the codec's recursive value writer. Other values pass through. It copies the relevant maps, activates existing entitlement IDs, and supplies the original module's `pro`, `premium`, `vip`, and `lifetime` fallbacks. Existing product IDs and account metadata are preserved. Expiration becomes 2100-01-01; synthetic purchase dates are in the past. It also discovers the method-envelope codec and deoptimizes codec methods so ART inlining does not bypass the hook.

Obfuscated class/member renaming should not require a module update. Flutter codec changes, removal of the discovery strings, changes to RevenueCat's map schema, or new server-side enforcement can still require changes. Missing/ambiguous discovery installs no hook and logs an error; map-patching failures leave the original payload available.

## Build

Use JDK 21 and Android SDK platform 36. The checked-in Gradle wrapper and plugin versions follow the supplied template.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Installable debug APK: `app/build/outputs/apk/debug/SuperProxyUnlocker-v2.0-debug.apk`.

Build an unsigned release with `./gradlew :app:assembleRelease`, then sign it using your own Android signing key. Published release assets use the name `SuperProxyUnlocker-v2.0-release.apk`. Signing keys are not part of this repository.

## Validation

On 2026-10-11, tested against Super Proxy **3.2.3**, versionCode **959**, Flutter **3.47.5**, on the connected Android API 37 device with Vector 1.2:

- Four unit tests passed: unrelated payload exclusion, immutable-map handling, existing entitlement/product preservation, and patch idempotence.
- Debug build and Android lint passed. Lint reports advisory warnings about SDK/tool/dependency versions and Android backup metadata, with no errors.
- Device discovery selected `yq4.k`, deoptimized 17 codec methods, and patched two customer-info payloads immediately after startup.
- The device owner confirmed the unlocker works again.
- The final headless APK was installed and verified to expose no launcher activities. A fresh process again installed the discovered hook and patched two customer-info updates.

Runtime diagnostics:

```bash
adb.exe logcat -s SuperProxyUnlocker:I
```

`Hook installed` confirms installation. `CustomerInfo patched #...` confirms that a payload actually passed through the hook.

## Legacy source and recovery

The original module source and v1.0 release are preserved separately in [SuperProxyUnlocker](https://github.com/Loukious/SuperProxyUnlocker). Repository access requires permission.

Local development analysis artifacts are preserved under the git-ignored `analysis/` directory and are not distributed with this repository:

- `apks/original-unlocker.apk`: original installed module.
- `original/sources/tn/loukious/superproxyunlocker/`: recovered legacy Java hook sources.
- `apks/superproxy*.apk`: installed target APK and splits.
- `target/`: JADX output for the target; some methods failed decompilation, so this is reference material rather than a buildable source tree.

The new buildable source is under `app/src/main/java/tn/loukious/superproxyunlocker/`.
