# Super Proxy Unlocker — Xposed Module

An Xposed/LSPosed module that unlocks premium behavior in **Super Proxy** (`com.scheler.superproxy`) for educational/practice purposes.

## Usage

### Steps

1. Install the APK on your device.
2. Enable the module in LSPosed/Xposed:
	 - Open LSPosed Manager.
	 - Enable "Super Proxy Unlocker".
	 - Scope: `com.scheler.superproxy`.
	 - Force stop Super Proxy and reopen.

## What It Does

This module hooks RevenueCat state and mapper output so premium checks and Flutter payloads consistently report an active premium state.

| Feature Gate | Source Gate | Hook |
| --- | --- | --- |
| Entitlement active checks | `EntitlementInfo.isActive()` | -> returns `true` |
| Entitlement renew checks | `EntitlementInfo.getWillRenew()` | -> returns `true` |
| Entitlement expiry checks | `EntitlementInfo.getExpirationDate()` | -> returns year 2100 |
| Subscription active checks | `SubscriptionInfo.isActive()` | -> returns `true` |
| Subscription renew checks | `SubscriptionInfo.getWillRenew()` | -> returns `true` |
| Subscription expiry checks | `SubscriptionInfo.getExpiresDate()` | -> returns year 2100 |
| Grace period checks | `SubscriptionInfo.getGracePeriodExpiresDate()` | -> returns year 2100 |
| Date activity helper | `DateActive.isActive()` / `getInGracePeriod()` | -> returns `true` |
| Customer expiry accessors | `CustomerInfo.getLatestExpirationDate()` and related getters | -> returns year 2100 |
| Active subscription fallback | `CustomerInfo.getActiveSubscriptions()` | -> injects non-empty premium ids |
| Purchased product fallback | `CustomerInfo.getAllPurchasedProductIds()` | -> injects non-empty premium ids |
| Active entitlement map filtering | `EntitlementInfos.getActive()` | -> returns full `getAll()` map |
| Flutter payload mapping | `*MapperKt.map(...)` for CustomerInfo/Entitlement/Subscription | -> patches payload to premium-active values |

## Architecture

```
Layer 1 — Package Filter
	MainHook.handleLoadPackage()
		-> only hooks com.scheler.superproxy

Layer 2 — RevenueCat Object-Level Hooks
	EntitlementInfo.isActive()                  -> true
	EntitlementInfo.getWillRenew()              -> true
	EntitlementInfo.getExpirationDate()         -> 2100
	SubscriptionInfo.isActive()                 -> true
	SubscriptionInfo.getWillRenew()             -> true
	SubscriptionInfo.getExpiresDate()           -> 2100
	SubscriptionInfo.getGracePeriodExpiresDate()-> 2100
	DateActive.isActive()/getInGracePeriod()   -> true
	CustomerInfo expiry accessors               -> 2100

Layer 3 — Collection Safety Fallbacks
	CustomerInfo.getActiveSubscriptions()       -> non-empty set
	CustomerInfo.getAllPurchasedProductIds()    -> non-empty set
	EntitlementInfos.getActive()                -> full map (no active filtering)

Layer 4 — Flutter Mapper Payload Patching
	EntitlementInfoMapperKt.map(...)            -> force premium fields
	SubscriptionInfoMapperKt.map(...)           -> force premium fields
	EntitlementInfosMapperKt.map(...)           -> force active+verified container
	CustomerInfoMapperKt.map(...)               -> force premium aggregate payload

Layer 5 — Synthetic Fallback Entries
	If entitlement/subscription maps are empty,
	inject ids: premium, pro, vip, lifetime
```

## Premium Flow (How It Works)

```
RevenueCat models (CustomerInfo / EntitlementInfo / SubscriptionInfo)
	↓
Object-level hooks force active=true and far-future expiry
	↓
RevenueCat Flutter mapper Kt classes convert models to Map payloads
	↓
Mapper hooks patch payload fields (active subscriptions, entitlements, dates)
	↓
Flutter layer receives premium-active maps
	↓
UI and feature checks pass as premium
```

## Key Hook Targets

| Class | Purpose |
| --- | --- |
| `com.revenuecat.purchases.CustomerInfo` | Aggregate subscription/entitlement state |
| `com.revenuecat.purchases.EntitlementInfo` | Per-entitlement premium flags |
| `com.revenuecat.purchases.EntitlementInfos` | Entitlement containers (`all` / `active`) |
| `com.revenuecat.purchases.SubscriptionInfo` | Per-product subscription status |
| `com.revenuecat.purchases.utils.DateActive` | Date-to-active helper used in checks |
| `com.revenuecat.purchases.hybridcommon.mappers.CustomerInfoMapperKt` | Flutter payload mapping for CustomerInfo |
| `com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfoMapperKt` | Flutter payload mapping for EntitlementInfo |
| `com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfosMapperKt` | Flutter payload mapping for EntitlementInfos |
| `com.revenuecat.purchases.hybridcommon.mappers.SubscriptionInfoMapperKt` | Flutter payload mapping for SubscriptionInfo |

## Building

### Prerequisites

- Android Studio (or Gradle CLI)
- Android SDK 35+
- JDK 21+

### Steps

1. Open `SuperProxyUnlocker/` in Android Studio as a project.
2. Sync Gradle.
3. Build -> Build APK (or run `:app:assembleDebug`).

### Manual Build (CLI)

```bash
cd SuperProxyUnlocker
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

On Windows PowerShell:

```powershell
cd SuperProxyUnlocker
.\gradlew.bat :app:assembleDebug
```

## Logs

Check Xposed/LSPosed logs for hook status:

```bash
adb logcat | grep SuperProxyUnlocker
```

Expected output includes:

```
SuperProxyUnlocker: Build marker 2026-04-16-superproxy-revenuecat
SuperProxyUnlocker: Hooking target package com.scheler.superproxy
SuperProxyUnlocker: Installing RevenueCat premium hooks
SuperProxyUnlocker: [OK] EntitlementInfo.isActive -> true
SuperProxyUnlocker: [OK] SubscriptionInfo.isActive -> true
SuperProxyUnlocker: [OK] CustomerInfoMapper.map hook installed
SuperProxyUnlocker: RevenueCat premium hooks installed
SuperProxyUnlocker: Super Proxy hooks installed
```

## Troubleshooting

### Module enabled but premium UI still locked

1. Confirm LSPosed scope includes only `com.scheler.superproxy`.
2. Force stop Super Proxy after toggling module state.
3. Check logs for any `[WARN] ... hook failed` lines.
4. If RevenueCat package/class names changed in a new app update, re-run reverse analysis and update target class names.

### App update changed behavior

If Super Proxy ships a major RevenueCat SDK or obfuscation change, mapper keys and method signatures may shift. Re-validate:

- Mapper classes in `com.revenuecat.purchases.hybridcommon.mappers`
- Customer/entitlement map key names consumed by Flutter side

## Disclaimer

This module is created for **educational purposes only** as part of reverse engineering practice. Do not use it to violate software terms, local law, or platform policies.
