package tn.loukious.superproxyunlocker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class SuperProxyPatches {

    private static final long FAR_FUTURE_EPOCH_MILLIS = 4102444800000L; // 2100-01-01T00:00:00Z

    private static final String RC_CUSTOMER_INFO = "com.revenuecat.purchases.CustomerInfo";
    private static final String RC_ENTITLEMENT_INFO = "com.revenuecat.purchases.EntitlementInfo";
    private static final String RC_ENTITLEMENT_INFOS = "com.revenuecat.purchases.EntitlementInfos";
    private static final String RC_SUBSCRIPTION_INFO = "com.revenuecat.purchases.SubscriptionInfo";
    private static final String RC_DATE_ACTIVE = "com.revenuecat.purchases.utils.DateActive";

    private static final String RC_CUSTOMER_INFO_MAPPER = "com.revenuecat.purchases.hybridcommon.mappers.CustomerInfoMapperKt";
    private static final String RC_ENTITLEMENT_INFO_MAPPER = "com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfoMapperKt";
    private static final String RC_ENTITLEMENT_INFOS_MAPPER = "com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfosMapperKt";
    private static final String RC_SUBSCRIPTION_INFO_MAPPER = "com.revenuecat.purchases.hybridcommon.mappers.SubscriptionInfoMapperKt";

    private final String tag;
    private volatile boolean revenueCatHooksInstalled = false;

    SuperProxyPatches(String tag) {
        this.tag = tag;
    }

    synchronized void installRevenueCatPatches(ClassLoader cl) {
        if (revenueCatHooksInstalled) {
            return;
        }
        revenueCatHooksInstalled = true;

        XposedBridge.log(tag + ": Installing RevenueCat premium hooks");

        // Core object-level checks
        hookBooleanReturn(RC_ENTITLEMENT_INFO, cl, "isActive", true, "EntitlementInfo.isActive");
        hookBooleanReturn(RC_ENTITLEMENT_INFO, cl, "getWillRenew", true, "EntitlementInfo.getWillRenew");
        hookDateReturn(RC_ENTITLEMENT_INFO, cl, "getExpirationDate", "EntitlementInfo.getExpirationDate");

        hookBooleanReturn(RC_SUBSCRIPTION_INFO, cl, "isActive", true, "SubscriptionInfo.isActive");
        hookBooleanReturn(RC_SUBSCRIPTION_INFO, cl, "getWillRenew", true, "SubscriptionInfo.getWillRenew");
        hookDateReturn(RC_SUBSCRIPTION_INFO, cl, "getExpiresDate", "SubscriptionInfo.getExpiresDate");
        hookDateReturn(RC_SUBSCRIPTION_INFO, cl, "getGracePeriodExpiresDate",
                "SubscriptionInfo.getGracePeriodExpiresDate");

        hookBooleanReturn(RC_DATE_ACTIVE, cl, "isActive", true, "DateActive.isActive");
        hookBooleanReturn(RC_DATE_ACTIVE, cl, "getInGracePeriod", true, "DateActive.getInGracePeriod");

        // CustomerInfo date and collection accessors
        hookDateReturn(RC_CUSTOMER_INFO, cl, "getLatestExpirationDate", "CustomerInfo.getLatestExpirationDate");
        hookDateReturnWithStringArg(RC_CUSTOMER_INFO, cl, "getExpirationDateForEntitlement",
                "CustomerInfo.getExpirationDateForEntitlement");
        hookDateReturnWithStringArg(RC_CUSTOMER_INFO, cl, "getExpirationDateForProductId",
                "CustomerInfo.getExpirationDateForProductId");
        hookDateReturnWithStringArg(RC_CUSTOMER_INFO, cl, "getExpirationDateForSku",
                "CustomerInfo.getExpirationDateForSku");

        hookNonEmptySet(RC_CUSTOMER_INFO, cl, "getActiveSubscriptions", "CustomerInfo.getActiveSubscriptions");
        hookNonEmptySet(RC_CUSTOMER_INFO, cl, "getAllPurchasedProductIds",
                "CustomerInfo.getAllPurchasedProductIds");

        hookEntitlementInfosGetActive(cl);

        // Flutter payload shaping for all CustomerInfo mapping pathways.
        hookMapperHooks(cl);

        XposedBridge.log(tag + ": RevenueCat premium hooks installed");
    }

    private void hookBooleanReturn(String className, ClassLoader cl, String methodName, boolean value, String label) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, XC_MethodReplacement.returnConstant(value));
            XposedBridge.log(tag + ": [OK] " + label + " -> " + value);
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] " + label + " hook failed: " + t.getMessage());
        }
    }

    private void hookDateReturn(String className, ClassLoader cl, String methodName, String label) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return farFutureDate();
                }
            });
            XposedBridge.log(tag + ": [OK] " + label + " -> far future");
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] " + label + " hook failed: " + t.getMessage());
        }
    }

    private void hookDateReturnWithStringArg(String className, ClassLoader cl, String methodName, String label) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return farFutureDate();
                }
            });
            XposedBridge.log(tag + ": [OK] " + label + "(String) -> far future");
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] " + label + "(String) hook failed: " + t.getMessage());
        }
    }

    private void hookNonEmptySet(String className, ClassLoader cl, String methodName, String label) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (!(result instanceof Set<?>) || ((Set<?>) result).isEmpty()) {
                        param.setResult(premiumProductIds());
                    }
                }
            });
            XposedBridge.log(tag + ": [OK] " + label + " non-empty fallback");
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] " + label + " hook failed: " + t.getMessage());
        }
    }

    private void hookEntitlementInfosGetActive(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(RC_ENTITLEMENT_INFOS, cl, "getActive", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object all = XposedHelpers.callMethod(param.thisObject, "getAll");
                        if (all instanceof Map<?, ?> && !((Map<?, ?>) all).isEmpty()) {
                            param.setResult(all);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log(tag + ": [OK] EntitlementInfos.getActive prefers full entitlement map");
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] EntitlementInfos.getActive hook failed: " + t.getMessage());
        }
    }

    private interface MapPatcher {
        void patch(Map<Object, Object> map);
    }

    private void hookMapperHooks(ClassLoader cl) {
        hookMapMapper(cl, RC_ENTITLEMENT_INFO_MAPPER, "EntitlementInfoMapper", new MapPatcher() {
            @Override
            public void patch(Map<Object, Object> map) {
                patchEntitlementMap(map, (String) map.get("identifier"));
            }
        });

        hookMapMapper(cl, RC_SUBSCRIPTION_INFO_MAPPER, "SubscriptionInfoMapper", new MapPatcher() {
            @Override
            public void patch(Map<Object, Object> map) {
                patchSubscriptionMap(map, (String) map.get("productIdentifier"));
            }
        });

        hookMapMapper(cl, RC_ENTITLEMENT_INFOS_MAPPER, "EntitlementInfosMapper", new MapPatcher() {
            @Override
            public void patch(Map<Object, Object> map) {
                patchEntitlementInfosContainer(map);
            }
        });

        hookMapMapper(cl, RC_CUSTOMER_INFO_MAPPER, "CustomerInfoMapper", new MapPatcher() {
            @Override
            public void patch(Map<Object, Object> map) {
                patchCustomerInfoMap(map);
            }
        });
    }

    private void hookMapMapper(ClassLoader cl, String className, String label, MapPatcher patcher) {
        try {
            Class<?> mapperClass = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(mapperClass, "map", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (!(result instanceof Map<?, ?>)) {
                        return;
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> map = (Map<Object, Object>) result;
                        patcher.patch(map);
                    } catch (Throwable t) {
                        XposedBridge.log(tag + ": [WARN] " + label + " map patch failed: " + t.getMessage());
                    }
                }
            });
            XposedBridge.log(tag + ": [OK] " + label + ".map hook installed");
        } catch (Throwable t) {
            XposedBridge.log(tag + ": [WARN] " + label + ".map hook failed: " + t.getMessage());
        }
    }

    private void patchCustomerInfoMap(Map<Object, Object> map) {
        String farIso = farFutureIso8601();

        map.put("latestExpirationDate", farIso);
        map.put("latestExpirationDateMillis", FAR_FUTURE_EPOCH_MILLIS);

        if (isNullOrEmptyCollection(map.get("activeSubscriptions"))) {
            map.put("activeSubscriptions", premiumProductIdsList());
        }
        if (isNullOrEmptyCollection(map.get("allPurchasedProductIdentifiers"))) {
            map.put("allPurchasedProductIdentifiers", premiumProductIdsList());
        }

        Map<Object, Object> allExp = asObjectMap(map.get("allExpirationDates"));
        if (allExp == null) {
            allExp = new LinkedHashMap<>();
            map.put("allExpirationDates", allExp);
        }
        Map<Object, Object> allExpMs = asObjectMap(map.get("allExpirationDatesMillis"));
        if (allExpMs == null) {
            allExpMs = new LinkedHashMap<>();
            map.put("allExpirationDatesMillis", allExpMs);
        }

        for (String id : premiumProductIds()) {
            allExp.put(id, farIso);
            allExpMs.put(id, FAR_FUTURE_EPOCH_MILLIS);
        }

        Map<Object, Object> entitlements = asObjectMap(map.get("entitlements"));
        if (entitlements == null) {
            entitlements = new LinkedHashMap<>();
            map.put("entitlements", entitlements);
        }
        patchEntitlementInfosContainer(entitlements);

        Map<Object, Object> subsByProduct = asObjectMap(map.get("subscriptionsByProductIdentifier"));
        if (subsByProduct == null) {
            subsByProduct = new LinkedHashMap<>();
            map.put("subscriptionsByProductIdentifier", subsByProduct);
        }
        if (subsByProduct.isEmpty()) {
            for (String id : premiumProductIds()) {
                subsByProduct.put(id, buildSyntheticSubscriptionMap(id));
            }
        }
        for (Map.Entry<Object, Object> entry : subsByProduct.entrySet()) {
            Map<Object, Object> subMap = asObjectMap(entry.getValue());
            if (subMap != null) {
                patchSubscriptionMap(subMap, String.valueOf(entry.getKey()));
            }
        }
    }

    private void patchEntitlementInfosContainer(Map<Object, Object> container) {
        Map<Object, Object> all = asObjectMap(container.get("all"));
        if (all == null) {
            all = new LinkedHashMap<>();
            container.put("all", all);
        }

        if (all.isEmpty()) {
            for (String id : premiumProductIds()) {
                all.put(id, buildSyntheticEntitlementMap(id));
            }
        }

        for (Map.Entry<Object, Object> entry : all.entrySet()) {
            Map<Object, Object> entitlement = asObjectMap(entry.getValue());
            if (entitlement != null) {
                patchEntitlementMap(entitlement, String.valueOf(entry.getKey()));
            }
        }

        container.put("active", new LinkedHashMap<>(all));
        container.put("verification", "VERIFIED_ON_DEVICE");
    }

    private void patchEntitlementMap(Map<Object, Object> map, String fallbackIdentifier) {
        String id = valueOrFallback((String) map.get("identifier"), fallbackIdentifier, "premium");
        String farIso = farFutureIso8601();

        map.put("identifier", id);
        map.put("isActive", Boolean.TRUE);
        map.put("willRenew", Boolean.TRUE);
        map.put("periodType", "NORMAL");
        map.put("latestPurchaseDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        map.put("latestPurchaseDate", farIso);
        map.put("originalPurchaseDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        map.put("originalPurchaseDate", farIso);
        map.put("expirationDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        map.put("expirationDate", farIso);
        map.put("store", "PLAY_STORE");
        map.put("productIdentifier", id);
        map.put("productPlanIdentifier", null);
        map.put("isSandbox", Boolean.FALSE);
        map.put("unsubscribeDetectedAt", null);
        map.put("unsubscribeDetectedAtMillis", null);
        map.put("billingIssueDetectedAt", null);
        map.put("billingIssueDetectedAtMillis", null);
        map.put("ownershipType", "PURCHASED");
        map.put("verification", "VERIFIED_ON_DEVICE");
    }

    private void patchSubscriptionMap(Map<Object, Object> map, String fallbackIdentifier) {
        String id = valueOrFallback((String) map.get("productIdentifier"), fallbackIdentifier, "premium");
        String farIso = farFutureIso8601();

        map.put("productIdentifier", id);
        map.put("purchaseDate", farIso);
        map.put("originalPurchaseDate", farIso);
        map.put("expiresDate", farIso);
        map.put("store", "PLAY_STORE");
        map.put("unsubscribeDetectedAt", null);
        map.put("isSandbox", Boolean.FALSE);
        map.put("billingIssuesDetectedAt", null);
        map.put("gracePeriodExpiresDate", farIso);
        map.put("ownershipType", "PURCHASED");
        map.put("periodType", "NORMAL");
        map.put("refundedAt", null);
        map.put("storeTransactionId", "xposed-superproxy-unlocker");
        map.put("isActive", Boolean.TRUE);
        map.put("willRenew", Boolean.TRUE);
    }

    private static Map<Object, Object> buildSyntheticEntitlementMap(String id) {
        Map<Object, Object> entitlement = new LinkedHashMap<>();
        String farIso = farFutureIso8601();
        entitlement.put("identifier", id);
        entitlement.put("isActive", Boolean.TRUE);
        entitlement.put("willRenew", Boolean.TRUE);
        entitlement.put("periodType", "NORMAL");
        entitlement.put("latestPurchaseDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        entitlement.put("latestPurchaseDate", farIso);
        entitlement.put("originalPurchaseDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        entitlement.put("originalPurchaseDate", farIso);
        entitlement.put("expirationDateMillis", FAR_FUTURE_EPOCH_MILLIS);
        entitlement.put("expirationDate", farIso);
        entitlement.put("store", "PLAY_STORE");
        entitlement.put("productIdentifier", id);
        entitlement.put("productPlanIdentifier", null);
        entitlement.put("isSandbox", Boolean.FALSE);
        entitlement.put("unsubscribeDetectedAt", null);
        entitlement.put("unsubscribeDetectedAtMillis", null);
        entitlement.put("billingIssueDetectedAt", null);
        entitlement.put("billingIssueDetectedAtMillis", null);
        entitlement.put("ownershipType", "PURCHASED");
        entitlement.put("verification", "VERIFIED_ON_DEVICE");
        return entitlement;
    }

    private static Map<Object, Object> buildSyntheticSubscriptionMap(String id) {
        Map<Object, Object> subscription = new LinkedHashMap<>();
        String farIso = farFutureIso8601();
        subscription.put("productIdentifier", id);
        subscription.put("purchaseDate", farIso);
        subscription.put("originalPurchaseDate", farIso);
        subscription.put("expiresDate", farIso);
        subscription.put("store", "PLAY_STORE");
        subscription.put("unsubscribeDetectedAt", null);
        subscription.put("isSandbox", Boolean.FALSE);
        subscription.put("billingIssuesDetectedAt", null);
        subscription.put("gracePeriodExpiresDate", farIso);
        subscription.put("ownershipType", "PURCHASED");
        subscription.put("periodType", "NORMAL");
        subscription.put("refundedAt", null);
        subscription.put("storeTransactionId", "xposed-superproxy-unlocker");
        subscription.put("isActive", Boolean.TRUE);
        subscription.put("willRenew", Boolean.TRUE);
        return subscription;
    }

    private static boolean isNullOrEmptyCollection(Object value) {
        return !(value instanceof Collection<?>) || ((Collection<?>) value).isEmpty();
    }

    private static String valueOrFallback(String preferred, String fallback, String defaultValue) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        return defaultValue;
    }

    private static Date farFutureDate() {
        return new Date(FAR_FUTURE_EPOCH_MILLIS);
    }

    private static String farFutureIso8601() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(farFutureDate());
    }

    private static Set<String> premiumProductIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add("premium");
        ids.add("pro");
        ids.add("vip");
        ids.add("lifetime");
        return ids;
    }

    private static ArrayList<String> premiumProductIdsList() {
        return new ArrayList<>(premiumProductIds());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> asObjectMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<Object, Object>) value;
        }
        return null;
    }
}