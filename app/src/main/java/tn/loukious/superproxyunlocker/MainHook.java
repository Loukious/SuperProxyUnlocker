package tn.loukious.superproxyunlocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SuperProxyUnlocker entrypoint.
 *
 * MainHook owns package filtering while SuperProxyPatches contains
 * all hook implementations.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static volatile boolean buildMarkerLogged = false;

    private static final String TAG = "SuperProxyUnlocker";
    private static final String TARGET_PACKAGE = "com.scheler.superproxy";
    private static final String BUILD_MARKER = "2026-04-16-superproxy-revenuecat";

    private final SuperProxyPatches patches = new SuperProxyPatches(TAG);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!buildMarkerLogged) {
            buildMarkerLogged = true;
            XposedBridge.log(TAG + ": Build marker " + BUILD_MARKER);
        }

        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            // No-op outside the target package.
            return;
        }

        XposedBridge.log(TAG + ": Hooking target package " + TARGET_PACKAGE);
        patches.installRevenueCatPatches(lpparam.classLoader);
        XposedBridge.log(TAG + ": Super Proxy hooks installed");
    }
}
