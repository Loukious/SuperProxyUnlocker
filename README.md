# SuperProxyUnlocker

Headless Xposed module for Super Proxy (`com.scheler.superproxy`), using the modern libxposed API and DexKit discovery. No launcher entry or activity.

[Download the signed APK](https://github.com/Loukious/SuperProxyUnlocker/releases/latest).

## Installation

1. Install the signed APK.
2. Enable SuperProxyUnlocker in Vector / LSPosed and select Super Proxy in its scope.
3. Force-stop and restart Super Proxy.

Requires Android 8.1+ and a framework supporting modern libxposed API 101 or newer. Application ID: `tn.loukious.superproxyunlocker.modern`.

## Update resilience

`org.luckypray:dexkit:2.2.0` discovers Flutter's value serializer using string anchors and its method signature. No obfuscated class or method names are hardcoded. Discovery scans the installed base and split APKs and requires exactly one matching method.

The hook recognizes RevenueCat customer-info maps before Flutter serializes them, covering method replies and listener updates. It activates existing entitlements and supplies `pro`, `premium`, `vip`, and `lifetime` fallback IDs, while preserving account metadata and existing product IDs. Relevant maps are copied before editing. Codec methods are deoptimized to prevent ART inlining from bypassing the hook.

Class and method renaming should not require changes. Changes to Flutter's codec, RevenueCat's payload schema, or server-side enforcement can still require an update. Unsupported discovery or payloads are left unmodified and diagnostic errors are logged.

## Build

Use JDK 21 and Android SDK platform 36.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/SuperProxyUnlocker-v2.0-debug.apk`.

```bash
./gradlew :app:assembleRelease
```

Sign the resulting unsigned release APK with your Android signing key. Published release assets are named `SuperProxyUnlocker-v2.0-release.apk`, with an accompanying SHA-256 checksum. Signing keys are not included in this repository.

## Validation

Tested against Super Proxy 3.2.3 (build 959) on Android API 37 with Vector 1.2. Four unit tests, the debug build, and Android lint passed. Live device checks confirmed hook installation and customer-info patching.

```bash
adb logcat -s SuperProxyUnlocker:I
```

`Hook installed` confirms installation; `CustomerInfo patched #...` confirms the hook processed a payload.
